package ru.yakut54.ktoto.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.CreateConversationRequest
import ru.yakut54.ktoto.data.model.UserItem
import ru.yakut54.ktoto.data.store.TokenStore

@OptIn(FlowPreview::class)
class CreateGroupViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _users = MutableStateFlow<List<UserItem>>(emptyList())
    val users: StateFlow<List<UserItem>> = _users

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    private val _selectedUsers = MutableStateFlow<List<UserItem>>(emptyList())
    val selectedUsers: StateFlow<List<UserItem>> = _selectedUsers

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating

    init {
        viewModelScope.launch {
            query.debounce(300).collect { q -> search(q) }
        }
        search("")
    }

    private fun search(q: String) {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                _users.value = api.searchUsers("Bearer $token", q)
            }
            _loading.value = false
        }
    }

    fun toggleUser(user: UserItem) {
        val current = _selected.value
        if (user.id in current) {
            _selected.value = current - user.id
            _selectedUsers.value = _selectedUsers.value.filter { it.id != user.id }
        } else {
            _selected.value = current + user.id
            _selectedUsers.value = _selectedUsers.value + user
        }
    }

    fun createGroup(name: String, onCreated: (convId: String, convName: String) -> Unit) {
        if (_creating.value) return
        viewModelScope.launch {
            _creating.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val result = api.createConversation(
                    "Bearer $token",
                    CreateConversationRequest(
                        type = "group",
                        name = name.trim(),
                        memberIds = _selected.value.toList(),
                    ),
                )
                val convId = result["id"] as? String ?: return@launch
                onCreated(convId, name.trim())
            }
            _creating.value = false
        }
    }
}
