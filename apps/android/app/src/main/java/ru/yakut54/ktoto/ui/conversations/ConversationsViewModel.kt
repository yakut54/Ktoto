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

    val userId = tokenStore.userId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val username = tokenStore.username.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ConversationsState.Loading
            try {
                val token = tokenStore.accessToken.first() ?: error("Not authenticated")
                val list = api.getConversations("Bearer $token")
                _state.value = ConversationsState.Success(list)
            } catch (e: Exception) {
                _state.value = ConversationsState.Error(e.message ?: "Failed to load")
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
