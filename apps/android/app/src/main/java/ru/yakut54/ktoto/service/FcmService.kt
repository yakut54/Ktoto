package ru.yakut54.ktoto.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.KtotoApp
import ru.yakut54.ktoto.MainActivity
import ru.yakut54.ktoto.R
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.FcmTokenRequest
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore

class FcmService : FirebaseMessagingService() {

    private val api: ApiService by inject()
    private val tokenStore: TokenStore by inject()
    private val socketManager: SocketManager by inject()

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val accessToken = tokenStore.getAccessToken() ?: return@launch
            runCatching { api.updateFcmToken("Bearer $accessToken", FcmTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val token = tokenStore.getAccessToken() ?: return@launch
                    socketManager.connect(token)
                }
            }
            "new_message" -> {
                val conversationId = data["conversationId"] ?: return
                val senderName = data["senderName"] ?: return
                val messageType = data["messageType"] ?: "text"
                val content = data["content"] ?: ""

                val body = when (messageType) {
                    "image" -> "📷 Фото"
                    "voice" -> "🎤 Голосовое"
                    "video" -> "🎬 Видео"
                    "file"  -> "📎 Файл"
                    else    -> content.take(100)
                }

                showMessageNotification(conversationId, senderName, body)
            }
        }
    }

    private fun showMessageNotification(conversationId: String, title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, KtotoApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(conversationId.hashCode(), notification)
        }
    }
}
