// app/src/main/java/com/iris/assistant/data/local/datastore/PreferencesRepository.kt
package com.iris.assistant.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.theme.AppFont
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = Constants.DATASTORE_NAME)

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val COLOR_SCHEME         = stringPreferencesKey("color_scheme")

        val BACKGROUND_LISTENING = booleanPreferencesKey("background_listening")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TTS_VOICE            = stringPreferencesKey("tts_voice")
        val USER_NAME            = stringPreferencesKey("user_name")
        val LLM_PROVIDER         = stringPreferencesKey("llm_provider")
        val LLM_MODEL            = stringPreferencesKey("llm_model")
        val AUTONOMY_LEVEL       = stringPreferencesKey("autonomy_level")
        val LOCAL_MODEL_NAME     = stringPreferencesKey("local_model_name")
        val LOCAL_MODEL_PATH     = stringPreferencesKey("local_model_path")
        val FONT_FAMILY          = stringPreferencesKey("font_family")
        val TTS_PROVIDER         = stringPreferencesKey("tts_provider")
        val IS_DARK_MODE          = booleanPreferencesKey("is_dark_mode")
        // Phase 4 — Power Mode
        val POWER_MODE_ENABLED   = booleanPreferencesKey("power_mode_enabled")
        val SHELL_SECURITY       = stringPreferencesKey("shell_security")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            colorScheme = prefs[Keys.COLOR_SCHEME]
                ?.let { runCatching { ColorSchemeOption.valueOf(it) }.getOrDefault(ColorSchemeOption.SLATE) }
                ?: ColorSchemeOption.SLATE,

            backgroundListening = prefs[Keys.BACKGROUND_LISTENING] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            ttsVoice = prefs[Keys.TTS_VOICE]
                ?.let { TtsVoice.fromApiName(it) }
                ?: TtsVoice.DEFAULT,
            userName      = prefs[Keys.USER_NAME]    ?: Constants.USER_NAME,
            llmProvider   = prefs[Keys.LLM_PROVIDER] ?: Constants.LLM_PROVIDER_GEMINI,
            llmModel      = prefs[Keys.LLM_MODEL]    ?: Constants.GEMINI_MODEL,
            autonomyLevel = prefs[Keys.AUTONOMY_LEVEL]
                ?.let { runCatching { AutonomyLevel.valueOf(it) }.getOrDefault(AutonomyLevel.SAFE) }
                ?: AutonomyLevel.SAFE,
            localModelName = prefs[Keys.LOCAL_MODEL_NAME] ?: "",
            localModelPath = prefs[Keys.LOCAL_MODEL_PATH] ?: "",
            isDarkMode         = prefs[Keys.IS_DARK_MODE]  ?: true,
            fontFamilyKey  = prefs[Keys.FONT_FAMILY] ?: AppFont.Inter.key,
            fontFamily     = prefs[Keys.FONT_FAMILY]
                ?.let { AppFont.fromKey(it) }
                ?: AppFont.Inter,
            ttsProvider = prefs[Keys.TTS_PROVIDER]
                ?.let { TtsProviderType.fromKey(it) }
                ?: TtsProviderType.GEMINI,
            // Phase 4 — Power Mode
            powerModeEnabled = prefs[Keys.POWER_MODE_ENABLED] ?: false,
            shellSecurity    = prefs[Keys.SHELL_SECURITY]     ?: Constants.SHELL_SECURITY_DEFAULT,
        )
    }

    suspend fun setColorScheme(scheme: ColorSchemeOption) {
        context.dataStore.edit { it[Keys.COLOR_SCHEME] = scheme.name }
    }

    suspend fun setBackgroundListening(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_LISTENING] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setTtsVoice(voice: TtsVoice) {
        context.dataStore.edit { it[Keys.TTS_VOICE] = voice.apiName }
    }

    suspend fun setLlmModel(model: String) {
        context.dataStore.edit { it[Keys.LLM_MODEL] = model }
    }

    suspend fun setLlmProvider(provider: String) {
        context.dataStore.edit { it[Keys.LLM_PROVIDER] = provider }
    }

    suspend fun setTtsProvider(provider: TtsProviderType) {
        context.dataStore.edit { it[Keys.TTS_PROVIDER] = TtsProviderType.keyOf(provider) }
    }

    suspend fun setAutonomyLevel(level: AutonomyLevel) {
        context.dataStore.edit { it[Keys.AUTONOMY_LEVEL] = level.name }
    }

    suspend fun setLocalModelName(name: String) {
        context.dataStore.edit { it[Keys.LOCAL_MODEL_NAME] = name }
    }

    suspend fun setLocalModelPath(path: String) {
        context.dataStore.edit { it[Keys.LOCAL_MODEL_PATH] = path }
    }

    suspend fun setFontFamily(font: AppFont) {
        context.dataStore.edit { it[Keys.FONT_FAMILY] = font.key }
    }

    suspend fun setIsDarkMode(isDark: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = isDark }
    }

    // Phase 4 — Power Mode
    suspend fun setPowerModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POWER_MODE_ENABLED] = enabled }
    }

    suspend fun setShellSecurity(level: String) {
        context.dataStore.edit { it[Keys.SHELL_SECURITY] = level }
    }
}