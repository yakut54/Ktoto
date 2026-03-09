package ru.yakut54.ktoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.MainActivity
import ru.yakut54.ktoto.R
import ru.yakut54.ktoto.call.CallManager
import ru.yakut54.ktoto.call.CallState

class CallService : Service() {

    companion object {
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Switch from ringing → active notification when call is accepted via UI
        scope.launch {
            callManager.callState.collect { state ->
                if (prevCallState == CallState.INCOMING_RINGING &&
                    (state == CallState.NEGOTIATING || state == CallState.IN_CALL)
                ) {
                    switchToActiveNotification()
                }
                prevCallState = state
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> {
                callManager.acceptCall()
                switchToActiveNotification()
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

                // On unlocked screen Android suppresses fullScreenIntent and shows
                // a heads-up notification instead. Bring MainActivity to foreground
                // directly so the call screen appears immediately.
                if (state == CallState.INCOMING_RINGING) {
                    val activityIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        putExtra("action", "INCOMING_CALL")
                    }
                    startActivity(activityIntent)
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
            builder
                .setContentTitle("Входящий звонок")
                .setContentText(peerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(openIntent, true)

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
