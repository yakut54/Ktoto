package ru.yakut54.ktoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.MainActivity
import ru.yakut54.ktoto.R
import ru.yakut54.ktoto.call.CallManager
import ru.yakut54.ktoto.call.CallState

class CallService : Service() {

    companion object {
        const val CHANNEL_ID = "ktoto_calls"
        const val NOTIF_ID = 1002
        const val ACTION_ACCEPT = "ru.yakut54.ktoto.CALL_ACCEPT"
        const val ACTION_DECLINE = "ru.yakut54.ktoto.CALL_DECLINE"
        const val ACTION_END = "ru.yakut54.ktoto.CALL_END"
    }

    private val callManager: CallManager by inject()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> callManager.acceptCall()
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
                    val serviceType = if (isVideoCall) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(NOTIF_ID, notification, serviceType)
                } else {
                    startForeground(NOTIF_ID, notification)
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Звонки",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о звонках"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
