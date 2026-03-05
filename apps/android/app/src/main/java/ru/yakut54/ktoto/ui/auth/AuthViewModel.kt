package ru.yakut54.ktoto.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.LoginRequest
import ru.yakut54.ktoto.data.model.RegisterRequest
import ru.yakut54.ktoto.data.store.TokenStore

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                val response = api.login(LoginRequest(username, password))
                tokenStore.save(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    userId = response.user.id,
                    username = response.user.username,
                )
                _state.value = AuthState.Success
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                val response = api.register(RegisterRequest(username, email, password))
                tokenStore.save(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    userId = response.user.id,
                    username = response.user.username,
                )
                _state.value = AuthState.Success
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun resetState() {
        _state.value = AuthState.Idle
    }
}
