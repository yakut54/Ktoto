package ru.yakut54.ktoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.MainActivity
import ru.yakut54.ktoto.R
import ru.yakut54.ktoto.data.store.TokenStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class PushService : Service() {

    companion object {
        const val CHANNEL_SERVICE = "ktoto_service"
        const val CHANNEL_MESSAGES = "ktoto_messages"
        const val NOTIF_SERVICE_ID = 1
        private const val NTFY_URL = "http://31.128.39.216:2586"
        private const val NTFY_USER = "ktoto-client"
        private const val NTFY_PASS = "ntfy-client-2026"
    }

    private val tokenStore: TokenStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var msgNotifId = 1000

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_SERVICE_ID, buildServiceNotification())
        scope.launch { subscribeToNotifications() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun subscribeToNotifications() {
        while (true) {
            try {
                val userId = tokenStore.userId.first()
                if (userId.isBlank()) { delay(5000); continue }

                val url = URL("$NTFY_URL/ktoto-$userId/sse")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    val creds = Base64.getEncoder().encodeToString("$NTFY_USER:$NTFY_PASS".toByteArray())
                    setRequestProperty("Authorization", "Basic $creds")
                    setRequestProperty("Accept", "text/event-stream")
                    connectTimeout = 10_000
                    readTimeout = 0 // keep-alive
                    connect()
                }

                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    var eventData = ""
                    for (line in reader.lines()) {
                        when {
                            line.startsWith("data:") -> eventData = line.removePrefix("data:").trim()
                            line.isEmpty() && eventData.isNotEmpty() -> {
                                handleEvent(eventData)
                                eventData = ""
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                delay(5000) // reconnect after error
            }
        }
    }

    private fun handleEvent(data: String) {
        try {
            // Simple JSON parsing without Gson to keep it lightweight
            if (data.contains("\"event\":\"message\"")) {
                val title = data.extractJson("title") ?: "Ktoto"
                val message = data.extractJson("message") ?: return
                showMessageNotification(title, message)
            }
        } catch (_: Exception) {}
    }

    private fun String.extractJson(key: String): String? {
        val pattern = Regex("\"$key\":\\s*\"([^\"]+)\"")
        return pattern.find(this)?.groupValues?.get(1)
    }

    private fun showMessageNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(msgNotifId++, notif)
    }

    private fun buildServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ktoto")
            .setContentText("На связи")
            .setContentIntent(pi)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Фоновая служба", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
