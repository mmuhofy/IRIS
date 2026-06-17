package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.remote.local.LocalModelManifest
import com.iris.assistant.data.remote.local.ModelDownloader
import com.iris.assistant.domain.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalModelUiState(
    val models: List<ModelUiItem> = emptyList(),
    val selectedModelId: String = ""
)

data class ModelUiItem(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeMb: Int,
    val recommended: Boolean,
    val isDownloaded: Boolean,
    val downloadState: DownloadState = DownloadState.Idle
)

@HiltViewModel
class LocalModelViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val modelDownloader: ModelDownloader
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalModelUiState())
    val uiState: StateFlow<LocalModelUiState> = _uiState.asStateFlow()

    init {
        refreshModels()
    }

    private fun refreshModels() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.map { it }.first()
            val items = LocalModelManifest.models.map { model ->
                val downloaded = modelDownloader.isDownloaded(model)
                ModelUiItem(
                    id = model.id,
                    displayName = model.displayName,
                    description = model.description,
                    sizeMb = model.sizeMb,
                    recommended = model.recommended,
                    isDownloaded = downloaded,
                    downloadState = if (downloaded) DownloadState.Ready else DownloadState.Idle
                )
            }
            _uiState.update {
                it.copy(models = items, selectedModelId = prefs.localModelName)
            }
        }
    }

    fun onSelectModel(modelId: String) {
        viewModelScope.launch {
            val model = LocalModelManifest.models.find { it.id == modelId } ?: return@launch
            val file = modelDownloader.getModelFile(model)
            if (file.exists()) {
                preferencesRepository.setLocalModelName(model.id)
                preferencesRepository.setLocalModelPath(file.absolutePath)
                _uiState.update { it.copy(selectedModelId = modelId) }
            }
        }
    }

    fun onDownloadModel(modelId: String) {
        viewModelScope.launch {
            val model = LocalModelManifest.models.find { it.id == modelId } ?: return@launch

            _uiState.update { state ->
                state.copy(models = state.models.map {
                    if (it.id == modelId) it.copy(downloadState = DownloadState.Downloading(0f, 0, 0))
                    else it
                })
            }

            modelDownloader.download(model) { state ->
                _uiState.update { uiState ->
                    uiState.copy(models = uiState.models.map {
                        if (it.id == modelId) it.copy(
                            downloadState = state,
                            isDownloaded = state is DownloadState.Ready
                        ) else it
                    })
                }
            }
        }
    }

    fun onDeleteModel(modelId: String) {
        viewModelScope.launch {
            val model = LocalModelManifest.models.find { it.id == modelId } ?: return@launch
            modelDownloader.deleteModel(model)

            val prefs = preferencesRepository.preferences.map { it }.first()
            if (prefs.localModelName == modelId) {
                preferencesRepository.setLocalModelName("")
                preferencesRepository.setLocalModelPath("")
                _uiState.update { it.copy(selectedModelId = "") }
            }

            _uiState.update { state ->
                state.copy(models = state.models.map {
                    if (it.id == modelId) it.copy(
                        isDownloaded = false,
                        downloadState = DownloadState.Idle
                    ) else it
                })
            }
        }
    }
}
