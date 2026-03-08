package ru.yakut54.ktoto.ui.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.CallRecord
import ru.yakut54.ktoto.data.store.TokenStore

class CallHistoryViewModel(
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _records = MutableStateFlow<List<CallRecord>>(emptyList())
    val records: StateFlow<List<CallRecord>> = _records.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val token = tokenStore.getAccessToken() ?: return@launch
                _records.value = apiService.getCallHistory("Bearer $token")
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }
}
