package ru.yakut54.ktoto.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.FcmTokenRequest
import ru.yakut54.ktoto.data.store.TokenStore

class FcmService : FirebaseMessagingService() {

    private val api: ApiService by inject()
    private val tokenStore: TokenStore by inject()

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val accessToken = tokenStore.getAccessToken() ?: return@launch
            runCatching { api.updateFcmToken("Bearer $accessToken", FcmTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM high-priority message wakes the process.
        // The WebSocket reconnects and CallManager handles call_incoming normally.
        // No additional action needed here.
    }
}
