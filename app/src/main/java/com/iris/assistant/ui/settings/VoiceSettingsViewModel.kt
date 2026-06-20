package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceSettingsUiState(
    val ttsVoice   : TtsVoice        = TtsVoice.DEFAULT,
    val ttsProvider: TtsProviderType = TtsProviderType.GEMINI,
)

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<VoiceSettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            VoiceSettingsUiState(
                ttsVoice    = prefs.ttsVoice,
                ttsProvider = prefs.ttsProvider,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = VoiceSettingsUiState()
        )

    fun onTtsVoiceChange(voice: TtsVoice) {
        viewModelScope.launch { preferencesRepository.setTtsVoice(voice) }
    }

    fun onTtsProviderChange(provider: TtsProviderType) {
        viewModelScope.launch { preferencesRepository.setTtsProvider(provider) }
    }
}
