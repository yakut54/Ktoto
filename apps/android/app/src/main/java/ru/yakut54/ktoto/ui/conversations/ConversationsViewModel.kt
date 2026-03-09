package ru.yakut54.ktoto.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore

sealed class ConversationsState {
    object Loading : ConversationsState()
    data class Success(val items: List<Conversation>) : ConversationsState()
    data class Error(val message: String) : ConversationsState()
}

class ConversationsViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val socketManager: SocketManager,
) : ViewModel() {

    private val _state = MutableStateFlow<ConversationsState>(ConversationsState.Loading)
    val state: StateFlow<ConversationsState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val userId = tokenStore.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val username = tokenStore.username.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Real-time online user IDs from WebSocket */
    val onlineUsers: StateFlow<Set<String>> = socketManager.onlineUsers

    /** Conversation IDs where someone is currently typing */
    private val _typingConvIds = MutableStateFlow<Set<String>>(emptySet())
    val typingConvIds: StateFlow<Set<String>> = _typingConvIds
    private val typingJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            socketManager.typing.collect { (convId, _) ->
                _typingConvIds.value = _typingConvIds.value + convId
                typingJobs[convId]?.cancel()
                typingJobs[convId] = viewModelScope.launch {
                    delay(3000)
                    _typingConvIds.value = _typingConvIds.value - convId
                }
            }
        }
        viewModelScope.launch {
            socketManager.newConversation.collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            val hasData = _state.value is ConversationsState.Success
            if (!hasData) _state.value = ConversationsState.Loading
            try {
                val token = tokenStore.accessToken.first() ?: error("Not authenticated")
                val list = api.getConversations("Bearer $token")
                _state.value = ConversationsState.Success(list)
                // Seed initial online status from REST snapshot
                val onlineFromRest = list.mapNotNull { if (it.otherStatus == "online") it.otherId else null }.toSet()
                socketManager.initOnlineUsers(onlineFromRest)
            } catch (e: Exception) {
                if (!hasData) _state.value = ConversationsState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val token = tokenStore.accessToken.first() ?: error("Not authenticated")
                val list = api.getConversations("Bearer $token")
                _state.value = ConversationsState.Success(list)
                val onlineFromRest = list.mapNotNull { if (it.otherStatus == "online") it.otherId else null }.toSet()
                socketManager.initOnlineUsers(onlineFromRest)
            } catch (_: Exception) {}
            finally {
                _refreshing.value = false
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            try {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.deleteConversation("Bearer $token", id)
                val current = _state.value
                if (current is ConversationsState.Success) {
                    _state.value = ConversationsState.Success(current.items.filter { it.id != id })
                }
            } catch (_: Exception) {
                load()
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            tokenStore.clear()
            onDone()
        }
    }
}
