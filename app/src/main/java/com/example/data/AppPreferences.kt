package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cilla_tts_prefs")

data class UserSettings(
    val hfToken: String = "",
    val activeVoiceId: String = "",
    val temperature: Float = 0.7f,
    val flowSteps: Int = 1,
    val sentencePauseMs: Int = 220,
    val trimSilence: Boolean = true,
    val autoNormalizePersian: Boolean = true,
    val darkTheme: Boolean = false,
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val ACTIVE_VOICE_ID = stringPreferencesKey("active_voice_id")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val FLOW_STEPS = intPreferencesKey("flow_steps")
        val SENTENCE_PAUSE_MS = intPreferencesKey("sentence_pause_ms")
        val TRIM_SILENCE = booleanPreferencesKey("trim_silence")
        val AUTO_NORMALIZE_FA = booleanPreferencesKey("auto_normalize_fa")
        val DARK_THEME = booleanPreferencesKey("dark_theme_v2")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            hfToken = prefs[Keys.HF_TOKEN] ?: "",
            activeVoiceId = prefs[Keys.ACTIVE_VOICE_ID] ?: "",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            flowSteps = prefs[Keys.FLOW_STEPS] ?: 1,
            sentencePauseMs = prefs[Keys.SENTENCE_PAUSE_MS] ?: 220,
            trimSilence = prefs[Keys.TRIM_SILENCE] ?: true,
            autoNormalizePersian = prefs[Keys.AUTO_NORMALIZE_FA] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
        )
    }

    suspend fun setHfToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setActiveVoiceId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_VOICE_ID] = id }
    }

    suspend fun setTemperature(value: Float) {
        context.dataStore.edit { it[Keys.TEMPERATURE] = value.coerceIn(0.1f, 1.4f) }
    }

    suspend fun setFlowSteps(steps: Int) {
        context.dataStore.edit { it[Keys.FLOW_STEPS] = steps.coerceIn(1, 12) }
    }

    suspend fun setSentencePauseMs(ms: Int) {
        context.dataStore.edit { it[Keys.SENTENCE_PAUSE_MS] = ms.coerceIn(0, 800) }
    }

    suspend fun setTrimSilence(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRIM_SILENCE] = enabled }
    }

    suspend fun setAutoNormalizePersian(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_NORMALIZE_FA] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }
}
