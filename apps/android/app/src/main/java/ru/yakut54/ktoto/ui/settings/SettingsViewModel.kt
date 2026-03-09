package ru.yakut54.ktoto.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.BlockedUser
import ru.yakut54.ktoto.data.model.ChangePasswordRequest
import ru.yakut54.ktoto.data.store.AppTheme
import ru.yakut54.ktoto.data.store.PreferencesStore
import ru.yakut54.ktoto.data.store.TokenStore

data class ProfileState(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
)

sealed class SettingsEvent {
    data class Error(val message: String) : SettingsEvent()
    object PasswordChanged : SettingsEvent()
    object ProfileSaved : SettingsEvent()
}

class SettingsViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    val prefsStore: PreferencesStore,
) : ViewModel() {

    private val _profile = MutableStateFlow(ProfileState())
    val profile: StateFlow<ProfileState> = _profile

    private val _uploadingAvatar = MutableStateFlow(false)
    val uploadingAvatar: StateFlow<Boolean> = _uploadingAvatar

    private val _savingProfile = MutableStateFlow(false)
    val savingProfile: StateFlow<Boolean> = _savingProfile

    private val _changingPassword = MutableStateFlow(false)
    val changingPassword: StateFlow<Boolean> = _changingPassword

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers

    private val _loadingBlocked = MutableStateFlow(false)
    val loadingBlocked: StateFlow<Boolean> = _loadingBlocked

    private val _event = MutableStateFlow<SettingsEvent?>(null)
    val event: StateFlow<SettingsEvent?> = _event

    fun load() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val resp = api.getMe("Bearer $token")
                val u = resp["user"] as? Map<*, *> ?: return@launch
                _profile.value = ProfileState(
                    userId = u["id"] as? String ?: "",
                    username = u["username"] as? String ?: "",
                    email = u["email"] as? String ?: "",
                    avatarUrl = u["avatarUrl"] as? String,
                )
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uploadingAvatar.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "avatar.jpg", body)
                val result = api.updateProfile("Bearer $token", part, null)
                val userMap = result["user"] as? Map<*, *>
                val newAvatarUrl = userMap?.get("avatarUrl") as? String
                if (newAvatarUrl != null) {
                    _profile.value = _profile.value.copy(avatarUrl = newAvatarUrl)
                }
                _event.value = SettingsEvent.ProfileSaved
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message ?: "Ошибка загрузки аватара")
            }
            _uploadingAvatar.value = false
        }
    }

    fun saveUsername(newUsername: String) {
        if (newUsername.isBlank()) return
        viewModelScope.launch {
            _savingProfile.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val usernamePart = newUsername.trim().toRequestBody("text/plain".toMediaTypeOrNull())
                val result = api.updateProfile("Bearer $token", null, usernamePart)
                val userMap = result["user"] as? Map<*, *>
                val savedName = userMap?.get("username") as? String ?: newUsername.trim()
                _profile.value = _profile.value.copy(username = savedName)
                tokenStore.save(
                    tokenStore.accessToken.first() ?: "",
                    tokenStore.refreshToken.first() ?: "",
                    _profile.value.userId,
                    savedName,
                )
                _event.value = SettingsEvent.ProfileSaved
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message ?: "Ошибка сохранения")
            }
            _savingProfile.value = false
        }
    }

    fun changePassword(current: String, new: String) {
        viewModelScope.launch {
            _changingPassword.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.changePassword("Bearer $token", ChangePasswordRequest(current, new))
                _event.value = SettingsEvent.PasswordChanged
            }.onFailure {
                _event.value = SettingsEvent.Error(
                    when {
                        it.message?.contains("401") == true || it.message?.contains("Wrong") == true ->
                            "Неверный текущий пароль"
                        else -> it.message ?: "Ошибка смены пароля"
                    }
                )
            }
            _changingPassword.value = false
        }
    }

    fun loadBlocked() {
        viewModelScope.launch {
            _loadingBlocked.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                _blockedUsers.value = api.getBlockedUsers("Bearer $token")
            }
            _loadingBlocked.value = false
        }
    }

    fun unblock(userId: String) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.unblockUser("Bearer $token", userId)
                _blockedUsers.value = _blockedUsers.value.filter { it.id != userId }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { prefsStore.setTheme(theme) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { prefsStore.setFontScale(scale) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setVibrationEnabled(enabled) }
    }

    fun consumeEvent() {
        _event.value = null
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val refresh = tokenStore.refreshToken.first()
                if (!refresh.isNullOrEmpty()) {
                    api.logout("Bearer $token", mapOf("refreshToken" to refresh))
                }
            }
            tokenStore.clear()
            onDone()
        }
    }
}
