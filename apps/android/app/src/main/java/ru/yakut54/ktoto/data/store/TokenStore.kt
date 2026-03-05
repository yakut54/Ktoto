package ru.yakut54.ktoto.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("auth")

class TokenStore(private val context: Context) {

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS] }
    val userId: Flow<String> = context.dataStore.data.map { it[KEY_USER_ID] ?: "" }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }

    suspend fun save(accessToken: String, refreshToken: String, userId: String, username: String) {
        context.dataStore.edit {
            it[KEY_ACCESS] = accessToken
            it[KEY_REFRESH] = refreshToken
            it[KEY_USER_ID] = userId
            it[KEY_USERNAME] = username
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.map { it[KEY_ACCESS] }.let { flow ->
            var result: String? = null
            flow.collect { result = it; return@collect }
            result
        }
}
