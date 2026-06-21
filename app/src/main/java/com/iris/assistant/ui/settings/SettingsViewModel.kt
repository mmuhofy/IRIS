package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.ui.theme.AppFont
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val colorScheme        : ColorSchemeOption  = ColorSchemeOption.LAVENDER,
    val backgroundListening: Boolean            = true,
    val ttsVoice           : TtsVoice           = TtsVoice.DEFAULT,
    val llmProvider        : String             = Constants.LLM_PROVIDER_GEMINI,
    val llmModel           : String             = Constants.GEMINI_MODEL,
    val autonomyLevel      : AutonomyLevel      = AutonomyLevel.SAFE,
    val ttsProvider        : TtsProviderType    = TtsProviderType.GEMINI,
    val historyCleared     : Boolean            = false,
    val localModelName     : String             = "",
    val localModelPath     : String             = "",
    val fontFamily         : AppFont            = AppFont.SystemDefault
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository : PreferencesRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            SettingsUiState(
                colorScheme         = prefs.colorScheme,
                backgroundListening = prefs.backgroundListening,
                ttsVoice            = prefs.ttsVoice,
                ttsProvider         = prefs.ttsProvider,
                llmProvider         = prefs.llmProvider,
                llmModel            = prefs.llmModel,
                autonomyLevel       = prefs.autonomyLevel,
                localModelName      = prefs.localModelName,
                localModelPath      = prefs.localModelPath,
                fontFamily          = prefs.fontFamily
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun onColorSchemeChange(scheme: ColorSchemeOption) {
        viewModelScope.launch { preferencesRepository.setColorScheme(scheme) }
    }

    fun onBackgroundListeningChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBackgroundListening(enabled) }
    }

    fun onTtsVoiceChange(voice: TtsVoice) {
        viewModelScope.launch { preferencesRepository.setTtsVoice(voice) }
    }

    fun onTtsProviderChange(provider: TtsProviderType) {
        viewModelScope.launch { preferencesRepository.setTtsProvider(provider) }
    }

    fun onLlmProviderChange(provider: String) {
        viewModelScope.launch {
            preferencesRepository.setLlmProvider(provider)
            val defaultModel = Constants.defaultModelForProvider(provider)
            if (defaultModel != null) {
                preferencesRepository.setLlmModel(defaultModel.apiName)
            }
        }
    }

    fun onLlmModelChange(model: String) {
        viewModelScope.launch { preferencesRepository.setLlmModel(model) }
    }

    fun onAutonomyLevelChange(level: AutonomyLevel) {
        viewModelScope.launch { preferencesRepository.setAutonomyLevel(level) }
    }

    fun onFontFamilyChange(font: AppFont) {
        viewModelScope.launch { preferencesRepository.setFontFamily(font) }
    }

    fun onClearHistory() {
        viewModelScope.launch { conversationRepository.clearAll() }
    }
}