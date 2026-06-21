package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackgroundSettingsUiState(
    val backgroundListening: Boolean = true,
)

@HiltViewModel
class BackgroundSettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<BackgroundSettingsUiState> = preferencesRepository.preferences
        .map { prefs -> BackgroundSettingsUiState(backgroundListening = prefs.backgroundListening) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = BackgroundSettingsUiState(),
        )

    fun onBackgroundListeningChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBackgroundListening(enabled) }
    }
}
