package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.ui.theme.ColorSchemeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val colorScheme        : ColorSchemeOption = ColorSchemeOption.LAVENDER,
    val backgroundListening: Boolean           = true,
    val ttsVoice           : TtsVoice          = TtsVoice.DEFAULT,
    val historyCleared     : Boolean           = false
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
                ttsVoice            = prefs.ttsVoice
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

    fun onClearHistory() {
        viewModelScope.launch { conversationRepository.clearAll() }
    }
}