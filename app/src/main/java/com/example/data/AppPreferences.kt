package com.example.data

import android.content.Context
import androidx.compose.ui.text.font.FontWeight
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

object FontIds {
    // Persian Fonts
    const val VAZIRMATN = "vazirmatn"
    const val ESTEDAD = "estedad"
    const val NOTO_NASKH_ARABIC = "noto_naskh_arabic"
    const val SAHEL = "sahel"
    const val B_NAZANIN = "b_nazanin"

    // English Fonts
    const val PLAYFAIR_DISPLAY = "playfair_display"
    const val CORMORANT_GARAMOND = "cormorant_garamond"
    const val LIBRE_BASKERVILLE = "libre_baskerville"
    const val LORA = "lora"
    const val INTER = "inter"

    // Technical Monospace
    const val JETBRAINS_MONO = "jetbrains_mono"
}

enum class AppFontWeight(
    val numericValue: Int,
    val labelEn: String,
    val labelFa: String,
    val composeWeight: FontWeight,
) {
    REGULAR(400, "Regular", "معمولی", FontWeight.Normal),
    MEDIUM(500, "Medium", "متوسط", FontWeight.Medium),
    SEMIBOLD(600, "SemiBold", "نیمه‌ضخیم", FontWeight.SemiBold),
    BOLD(700, "Bold", "ضخیم", FontWeight.Bold);

    companion object {
        fun fromStored(value: String?): AppFontWeight =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.numericValue.toString() == value }
                ?: REGULAR
    }
}

data class TypographySettings(
    val persianUiFontId: String = FontIds.VAZIRMATN,
    val persianHeadingFontId: String = FontIds.ESTEDAD,
    val persianWeight: AppFontWeight = AppFontWeight.REGULAR,
    val englishUiFontId: String = FontIds.INTER,
    val englishHeadingFontId: String = FontIds.PLAYFAIR_DISPLAY,
    val englishWeight: AppFontWeight = AppFontWeight.REGULAR,
) {
    val isDefault: Boolean
        get() = this == TypographySettings()
}

enum class NarratorGender(
    val id: String,
    val persianName: String,
    val roleLabel: String,
    val timbreNote: String,
    val pitchScale: Float,
    val baseFreqHz: Float,
) {
    FEMALE(
        id = "builtin_female",
        persianName = "سارا",
        roleLabel = "زن",
        timbreNote = "سارا · لطیف و روشن",
        pitchScale = 1.18f,
        baseFreqHz = 215f,
    ),
    MALE(
        id = "builtin_male",
        persianName = "آرش",
        roleLabel = "مرد",
        timbreNote = "آرش · بم و رسا",
        pitchScale = 0.78f,
        baseFreqHz = 115f,
    );

    val fullLabel: String
        get() = "صدای $roleLabel ($persianName)"

    companion object {
        fun fromStored(value: String?): NarratorGender =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.id == value } ?: FEMALE
    }
}

data class UserSettings(
    val hfToken: String = "",
    val activeVoiceId: String = "",
    val narratorGender: NarratorGender = NarratorGender.FEMALE,
    val temperature: Float = 0.7f,
    val flowSteps: Int = 1,
    val sentencePauseMs: Int = 220,
    val trimSilence: Boolean = true,
    val autoNormalizePersian: Boolean = true,
    val darkTheme: Boolean = false,
    val typography: TypographySettings = TypographySettings(),
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val ACTIVE_VOICE_ID = stringPreferencesKey("active_voice_id")
        val NARRATOR_GENDER = stringPreferencesKey("narrator_gender")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val FLOW_STEPS = intPreferencesKey("flow_steps")
        val SENTENCE_PAUSE_MS = intPreferencesKey("sentence_pause_ms")
        val TRIM_SILENCE = booleanPreferencesKey("trim_silence")
        val AUTO_NORMALIZE_FA = booleanPreferencesKey("auto_normalize_fa")
        val DARK_THEME = booleanPreferencesKey("dark_theme_v2")

        // Typography Preferences
        val FA_UI_FONT = stringPreferencesKey("font_fa_ui")
        val FA_HEADING_FONT = stringPreferencesKey("font_fa_heading")
        val FA_WEIGHT = stringPreferencesKey("font_fa_weight")
        val EN_UI_FONT = stringPreferencesKey("font_en_ui")
        val EN_HEADING_FONT = stringPreferencesKey("font_en_heading")
        val EN_WEIGHT = stringPreferencesKey("font_en_weight")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            hfToken = prefs[Keys.HF_TOKEN] ?: "",
            activeVoiceId = prefs[Keys.ACTIVE_VOICE_ID] ?: "",
            narratorGender = NarratorGender.fromStored(prefs[Keys.NARRATOR_GENDER]),
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            flowSteps = prefs[Keys.FLOW_STEPS] ?: 1,
            sentencePauseMs = prefs[Keys.SENTENCE_PAUSE_MS] ?: 220,
            trimSilence = prefs[Keys.TRIM_SILENCE] ?: true,
            autoNormalizePersian = prefs[Keys.AUTO_NORMALIZE_FA] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            typography = TypographySettings(
                persianUiFontId = prefs[Keys.FA_UI_FONT] ?: FontIds.VAZIRMATN,
                persianHeadingFontId = prefs[Keys.FA_HEADING_FONT] ?: FontIds.ESTEDAD,
                persianWeight = AppFontWeight.fromStored(prefs[Keys.FA_WEIGHT]),
                englishUiFontId = prefs[Keys.EN_UI_FONT] ?: FontIds.INTER,
                englishHeadingFontId = prefs[Keys.EN_HEADING_FONT] ?: FontIds.PLAYFAIR_DISPLAY,
                englishWeight = AppFontWeight.fromStored(prefs[Keys.EN_WEIGHT]),
            ),
        )
    }

    suspend fun setHfToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setActiveVoiceId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_VOICE_ID] = id }
    }

    suspend fun setNarratorGender(gender: NarratorGender) {
        context.dataStore.edit {
            it[Keys.NARRATOR_GENDER] = gender.name
            it[Keys.ACTIVE_VOICE_ID] = gender.id
        }
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

    suspend fun setPersianUiFont(fontId: String) {
        context.dataStore.edit { it[Keys.FA_UI_FONT] = fontId }
    }

    suspend fun setPersianHeadingFont(fontId: String) {
        context.dataStore.edit { it[Keys.FA_HEADING_FONT] = fontId }
    }

    suspend fun setPersianWeight(weight: AppFontWeight) {
        context.dataStore.edit { it[Keys.FA_WEIGHT] = weight.name }
    }

    suspend fun setEnglishUiFont(fontId: String) {
        context.dataStore.edit { it[Keys.EN_UI_FONT] = fontId }
    }

    suspend fun setEnglishHeadingFont(fontId: String) {
        context.dataStore.edit { it[Keys.EN_HEADING_FONT] = fontId }
    }

    suspend fun setEnglishWeight(weight: AppFontWeight) {
        context.dataStore.edit { it[Keys.EN_WEIGHT] = weight.name }
    }

    suspend fun resetTypographyToDefaults() {
        context.dataStore.edit {
            it[Keys.FA_UI_FONT] = FontIds.VAZIRMATN
            it[Keys.FA_HEADING_FONT] = FontIds.ESTEDAD
            it[Keys.FA_WEIGHT] = AppFontWeight.REGULAR.name
            it[Keys.EN_UI_FONT] = FontIds.INTER
            it[Keys.EN_HEADING_FONT] = FontIds.PLAYFAIR_DISPLAY
            it[Keys.EN_WEIGHT] = AppFontWeight.REGULAR.name
        }
    }
}
