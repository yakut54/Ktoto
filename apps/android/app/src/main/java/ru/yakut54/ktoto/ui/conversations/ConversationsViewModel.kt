package ru.yakut54.ktoto.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.store.TokenStore

sealed class ConversationsState {
    object Loading : ConversationsState()
    data class Success(val items: List<Conversation>) : ConversationsState()
    data class Error(val message: String) : ConversationsState()
}

class ConversationsViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ConversationsState>(ConversationsState.Loading)
    val state: StateFlow<ConversationsState> = _state

    // Only true when user manually pulls to refresh
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    val userId = tokenStore.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val username = tokenStore.username.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Silent load — called on screen resume, no visible indicator
    fun load() {
        viewModelScope.launch {
            val hasData = _state.value is ConversationsState.Success
            if (!hasData) _state.value = ConversationsState.Loading
            try {
                val token = tokenStore.accessToken.first() ?: error("Not authenticated")
                val list = api.getConversations("Bearer $token")
                _state.value = ConversationsState.Success(list)
            } catch (e: Exception) {
                if (!hasData) _state.value = ConversationsState.Error(e.message ?: "Failed to load")
            }
        }
    }

    // Explicit pull-to-refresh — shows the spinner indicator
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val token = tokenStore.accessToken.first() ?: error("Not authenticated")
                val list = api.getConversations("Bearer $token")
                _state.value = ConversationsState.Success(list)
            } catch (_: Exception) {
                // Silently fail — don't replace existing data on refresh error
            } finally {
                _refreshing.value = false
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
