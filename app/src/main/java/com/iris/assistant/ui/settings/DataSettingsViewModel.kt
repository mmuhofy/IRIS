package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.domain.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataSettingsUiState(
    val historyCleared: Boolean = false,
)

@HiltViewModel
class DataSettingsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataSettingsUiState())
    val uiState: StateFlow<DataSettingsUiState> = _uiState.asStateFlow()

    fun onClearHistory() {
        viewModelScope.launch {
            conversationRepository.clearAll()
            _uiState.value = DataSettingsUiState(historyCleared = true)
        }
    }
}
