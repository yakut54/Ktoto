package ru.yakut54.ktoto.ui.groupinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.AddMemberRequest
import ru.yakut54.ktoto.data.model.ChangeRoleRequest
import ru.yakut54.ktoto.data.model.GroupMember
import ru.yakut54.ktoto.data.model.RenameGroupRequest
import ru.yakut54.ktoto.data.model.UserItem
import ru.yakut54.ktoto.data.store.TokenStore

sealed class GroupInfoState {
    object Loading : GroupInfoState()
    data class Success(val members: List<GroupMember>) : GroupInfoState()
    data class Error(val message: String) : GroupInfoState()
}

class GroupInfoViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<GroupInfoState>(GroupInfoState.Loading)
    val state: StateFlow<GroupInfoState> = _state

    /** ID текущего пользователя */
    val currentUserId: StateFlow<String> = tokenStore.userId.let { flow ->
        MutableStateFlow("").also { sf ->
            viewModelScope.launch { sf.value = flow.first() ?: "" }
        }
    }

    private val _convName = MutableStateFlow("")
    val convName: StateFlow<String> = _convName

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    // For "Add member" search
    private val _searchUsers = MutableStateFlow<List<UserItem>>(emptyList())
    val searchUsers: StateFlow<List<UserItem>> = _searchUsers

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    fun load(convId: String, initialName: String) {
        _convName.value = initialName
        viewModelScope.launch {
            _state.value = GroupInfoState.Loading
            runCatching {
                val token = tokenStore.accessToken.first() ?: error("No token")
                val members = api.getGroupMembers("Bearer $token", convId)
                _state.value = GroupInfoState.Success(members)
            }.onFailure {
                _state.value = GroupInfoState.Error(it.message ?: "Ошибка")
            }
        }
    }

    fun rename(convId: String, name: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.renameGroup("Bearer $token", convId, RenameGroupRequest(name.trim()))
                _convName.value = name.trim()
                onDone(name.trim())
            }
            _saving.value = false
        }
    }

    fun removeMember(convId: String, userId: String) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.removeGroupMember("Bearer $token", convId, userId)
                val s = _state.value
                if (s is GroupInfoState.Success) {
                    _state.value = GroupInfoState.Success(s.members.filter { it.id != userId })
                }
            }
        }
    }

    fun changeRole(convId: String, userId: String, role: String) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.changeGroupMemberRole("Bearer $token", convId, userId, ChangeRoleRequest(role))
                val s = _state.value
                if (s is GroupInfoState.Success) {
                    _state.value = GroupInfoState.Success(
                        s.members.map { if (it.id == userId) it.copy(role = role) else it }
                    )
                }
            }
        }
    }

    fun addMember(convId: String, userId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.addGroupMember("Bearer $token", convId, AddMemberRequest(userId))
                load(convId, _convName.value)
                onDone()
            }
        }
    }

    fun searchForAdd(query: String) {
        viewModelScope.launch {
            _searching.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                _searchUsers.value = api.searchUsers("Bearer $token", query)
            }
            _searching.value = false
        }
    }

    fun leaveGroup(convId: String, currentUserId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.removeGroupMember("Bearer $token", convId, currentUserId)
                onDone()
            }
        }
    }
}
