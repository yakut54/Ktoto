package ru.yakut54.ktoto.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.SendMessageRequest
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore

class ChatViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    val socketManager: SocketManager,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private var conversationId: String = ""

    fun init(convId: String) {
        conversationId = convId
        loadHistory()
        subscribeToMessages()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val history = api.getMessages("Bearer $token", conversationId)
                _messages.value = history
            }
        }
    }

    private fun subscribeToMessages() {
        viewModelScope.launch {
            socketManager.messages
                .filter { it.conversationId == conversationId }
                .collect { msg ->
                    // Avoid duplicates if REST already returned it
                    if (_messages.value.none { it.id == msg.id }) {
                        _messages.value = _messages.value + msg
                    }
                }
        }
        viewModelScope.launch {
            socketManager.typing
                .filter { it.first == conversationId }
                .collect {
                    _isTyping.value = true
                    kotlinx.coroutines.delay(3000)
                    _isTyping.value = false
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val msg = api.sendMessage("Bearer $token", conversationId, SendMessageRequest(content))
                if (_messages.value.none { it.id == msg.id }) {
                    _messages.value = _messages.value + msg
                }
            }
            _sending.value = false
        }
    }

    fun notifyTyping() {
        socketManager.sendTyping(conversationId)
    }
}
