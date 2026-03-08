package ru.yakut54.ktoto.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
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
        if (data["type"] == "incoming_call") {
            // FCM woke the process — connect socket so server re-emits call_incoming
            CoroutineScope(Dispatchers.IO).launch {
                val token = tokenStore.getAccessToken() ?: return@launch
                socketManager.connect(token)
            }
        }
    }
}
