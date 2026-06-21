package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSettingsUiState(
    val llmProvider    : String = Constants.LLM_PROVIDER_GEMINI,
    val llmModel       : String = Constants.GEMINI_MODEL,
    val localModelName : String = "",
)

@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ModelSettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            ModelSettingsUiState(
                llmProvider    = prefs.llmProvider,
                llmModel       = prefs.llmModel,
                localModelName = prefs.localModelName,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModelSettingsUiState(),
        )

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
}
