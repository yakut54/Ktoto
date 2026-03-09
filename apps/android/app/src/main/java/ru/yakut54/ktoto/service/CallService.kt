package ru.yakut54.ktoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.IncomingCallActivity
import ru.yakut54.ktoto.MainActivity
import ru.yakut54.ktoto.R
import ru.yakut54.ktoto.call.CallManager
import ru.yakut54.ktoto.call.CallState
import ru.yakut54.ktoto.utils.CallLogger

class CallService : Service() {

    companion object {
        private const val TAG = "CallService"
        const val CHANNEL_ID = "ktoto_calls"          // legacy (kept for compat)
        const val CHANNEL_RING_ID = "ktoto_calls_ring"   // incoming — ringtone + vibration
        const val CHANNEL_ACTIVE_ID = "ktoto_calls_active" // in-call — silent
        const val NOTIF_ID = 1002
        const val ACTION_ACCEPT = "ru.yakut54.ktoto.CALL_ACCEPT"
        const val ACTION_DECLINE = "ru.yakut54.ktoto.CALL_DECLINE"
        const val ACTION_END = "ru.yakut54.ktoto.CALL_END"
    }

    private val callManager: CallManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prevCallState: CallState = CallState.IDLE

    // ─── Overlay (shown instead of fullScreenIntent when screen is ON) ────────
    private var overlayView: View? = null
    private val windowManagerService: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Switch from ringing → active notification when call is accepted via UI
        scope.launch {
            callManager.callState.collect { state ->
                if (prevCallState == CallState.INCOMING_RINGING) {
                    // Dismiss overlay whenever ringing stops (accept, reject, timeout)
                    hideCallOverlay()
                    if (state == CallState.NEGOTIATING || state == CallState.IN_CALL) {
                        switchToActiveNotification()
                    }
                }
                prevCallState = state
            }
        }
    }

    override fun onDestroy() {
        hideCallOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> {
                callManager.acceptCall()
                switchToActiveNotification()
                // User tapped notification button — BAL exemption is active for 30s.
                // Bring the app to foreground so the call screen appears immediately.
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("action", "IN_CALL")
                    },
                )
            }
            ACTION_DECLINE -> callManager.rejectCall()
            ACTION_END -> callManager.endCall()
            else -> {
                // Start foreground with appropriate notification
                val info = callManager.callInfo.value
                val state = callManager.callState.value
                val isVideoCall = intent?.getBooleanExtra("isVideoCall", false) ?: false
                val notification = buildNotification(
                    peerName = info?.peerName ?: "...",
                    isIncoming = state == CallState.INCOMING_RINGING,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // During ringing the microphone is not captured yet — use dataSync
                    // to avoid SecurityException on devices where RECORD_AUDIO wasn't
                    // granted before the first incoming call notification appears.
                    val isRinging = state == CallState.INCOMING_RINGING ||
                        state == CallState.OUTGOING_RINGING
                    val serviceType = when {
                        isRinging -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        isVideoCall -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(NOTIF_ID, notification, serviceType)
                } else {
                    startForeground(NOTIF_ID, notification)
                }

                // When screen is ON, Android suppresses fullScreenIntent and shows
                // a HUD notification instead. To show the call screen automatically:
                //   • If SYSTEM_ALERT_WINDOW is granted → show WindowManager overlay
                //     (works on all ROMs including HiOS/MIUI because user interaction
                //     on the overlay gives us BAL exemption for startActivity).
                //   • Otherwise → try startActivity(IncomingCallActivity) as fallback
                //     (works on stock Android, may be blocked on restrictive OEM ROMs).
                // Screen OFF is handled reliably by fullScreenIntent in the notification.
                if (state == CallState.INCOMING_RINGING) {
                    val pm = getSystemService(PowerManager::class.java)
                    if (pm.isInteractive) {
                        val peerName = callManager.callInfo.value?.peerName ?: "..."
                        if (Settings.canDrawOverlays(this)) {
                            showCallOverlay(peerName)
                        } else {
                            // Fallback: may be blocked on HiOS without Auto-start permission
                            CallLogger.i(TAG, "screen ON, no overlay permission — trying startActivity")
                            startActivity(
                                Intent(this, IncomingCallActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(peerName: String, isIncoming: Boolean): Notification {
        // Intent to open the app (call screen)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", if (isIncoming) "INCOMING_CALL" else "IN_CALL")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = if (isIncoming) CHANNEL_RING_ID else CHANNEL_ACTIVE_ID
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setAutoCancel(false)

        if (isIncoming) {
            // fullScreenIntent must target IncomingCallActivity (trampoline with manifest-level
            // showWhenLocked + turnScreenOn), NOT MainActivity directly.
            // Screen OFF: system fires this intent → IncomingCallActivity → MainActivity.
            // Screen ON:  system suppresses it; we use the overlay instead (see onStartCommand).
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this, 10,
                Intent(this, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .setContentTitle("Входящий звонок")
                .setContentText(peerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)

            // Accept action
            val acceptIntent = PendingIntent.getService(
                this, 1,
                Intent(this, CallService::class.java).apply { action = ACTION_ACCEPT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_menu_call, "Принять", acceptIntent)

            // Decline action
            val declineIntent = PendingIntent.getService(
                this, 2,
                Intent(this, CallService::class.java).apply { action = ACTION_DECLINE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_delete, "Отклонить", declineIntent)
        } else {
            // In-call notification
            val endIntent = PendingIntent.getService(
                this, 3,
                Intent(this, CallService::class.java).apply { action = ACTION_END },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .setContentTitle("Звонок с $peerName")
                .setContentText("Нажмите, чтобы вернуться")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(android.R.drawable.ic_delete, "Завершить", endIntent)
        }

        return builder.build()
    }

    /** Stop the ringing notification and post a silent active-call one. */
    private fun switchToActiveNotification() {
        val peerName = callManager.callInfo.value?.peerName ?: "..."
        val activeNotif = buildNotification(peerName = peerName, isIncoming = false)
        // stopForeground removes the old notification (with ringtone channel), then
        // startForeground re-attaches with the new silent channel notification.
        stopForeground(STOP_FOREGROUND_REMOVE)
        val isVideo = callManager.isVideoCall.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (isVideo) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIF_ID, activeNotif, serviceType)
        } else {
            startForeground(NOTIF_ID, activeNotif)
        }
    }

    // ─── Incoming call overlay (screen ON) ───────────────────────────────────

    /** Shows a WindowManager overlay card so the call screen appears even when
     *  the screen is already on and the app is in the background.
     *  Requires SYSTEM_ALERT_WINDOW permission (checked before calling).
     *  Must be called on the main thread. */
    private fun showCallOverlay(peerName: String) {
        if (!Settings.canDrawOverlays(this)) return
        hideCallOverlay()

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        fun roundBg(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(8).toFloat()
        }

        fun makeBtn(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 16f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(4), dp(14), dp(4), dp(14))
            isClickable = true
            isFocusable = true
            background = roundBg(color)
            setOnClickListener { onClick() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0111111.toInt())
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Входящий звонок"
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
        })

        root.addView(TextView(this).apply {
            text = peerName
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        btnRow.addView(
            makeBtn("Принять", 0xFF4CAF50.toInt()) {
                callManager.acceptCall()
                switchToActiveNotification()
                hideCallOverlay()
                // User just interacted with our overlay → BAL exemption is active
                startActivity(Intent(this@CallService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("action", "IN_CALL")
                })
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) },
        )

        btnRow.addView(
            makeBtn("Отклонить", 0xFFF44336.toInt()) {
                callManager.rejectCall()
                hideCallOverlay()
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) },
        )

        root.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) })

        val wlp = WindowManager.LayoutParams(
            MATCH, WRAP,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }

        runCatching { windowManagerService.addView(root, wlp) }
            .onSuccess { overlayView = root; CallLogger.i(TAG, "overlay shown", "peer" to peerName) }
            .onFailure { CallLogger.e(TAG, "overlay addView failed: ${it.message}") }
    }

    /** Removes the call overlay. Must be called on the main thread. */
    private fun hideCallOverlay() {
        overlayView?.let { v ->
            runCatching { windowManagerService.removeView(v) }
                .onFailure { CallLogger.e(TAG, "overlay removeView failed: ${it.message}") }
            overlayView = null
            CallLogger.i(TAG, "overlay hidden")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Incoming call channel — ringtone + vibration
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val ringChannel = NotificationChannel(
                CHANNEL_RING_ID,
                "Входящие звонки",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о входящих звонках"
                setShowBadge(false)
                setSound(ringtoneUri, audioAttr)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ringChannel)

            // Active call channel — silent
            val activeChannel = NotificationChannel(
                CHANNEL_ACTIVE_ID,
                "Активный звонок",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Уведомление активного звонка"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(activeChannel)
        }
    }
}
