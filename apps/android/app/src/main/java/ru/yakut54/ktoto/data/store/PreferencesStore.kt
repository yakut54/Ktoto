package ru.yakut54.ktoto.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsDataStore by preferencesDataStore("preferences")

enum class AppTheme { SYSTEM, LIGHT, DARK }

class PreferencesStore(private val context: Context) {

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
    }

    val theme: Flow<AppTheme> = context.prefsDataStore.data.map {
        when (it[KEY_THEME]) {
            "LIGHT" -> AppTheme.LIGHT
            "DARK" -> AppTheme.DARK
            else -> AppTheme.SYSTEM
        }
    }

    val fontScale: Flow<Float> = context.prefsDataStore.data.map {
        it[KEY_FONT_SCALE] ?: 1.0f
    }

    val soundEnabled: Flow<Boolean> = context.prefsDataStore.data.map {
        it[KEY_SOUND] != false
    }

    val vibrationEnabled: Flow<Boolean> = context.prefsDataStore.data.map {
        it[KEY_VIBRATION] != false
    }

    suspend fun setTheme(theme: AppTheme) {
        context.prefsDataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setFontScale(scale: Float) {
        context.prefsDataStore.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.prefsDataStore.edit { it[KEY_SOUND] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.prefsDataStore.edit { it[KEY_VIBRATION] = enabled }
    }
}
