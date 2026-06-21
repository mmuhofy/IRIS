package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.AutonomyLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutonomySettingsUiState(
    val autonomyLevel: AutonomyLevel = AutonomyLevel.SAFE,
)

@HiltViewModel
class AutonomySettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AutonomySettingsUiState> = preferencesRepository.preferences
        .map { prefs -> AutonomySettingsUiState(autonomyLevel = prefs.autonomyLevel) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = AutonomySettingsUiState(),
        )

    fun onAutonomyLevelChange(level: AutonomyLevel) {
        viewModelScope.launch { preferencesRepository.setAutonomyLevel(level) }
    }
}
