package com.iris.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.remote.local.LocalModelManifest
import com.iris.assistant.data.remote.local.ModelDownloader
import com.iris.assistant.domain.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.map { it }.first()

            // Observe download states from the application-scoped downloader
            modelDownloader.downloadStates.collect { downloadStates ->
                val items = LocalModelManifest.models.map { model ->
                    val downloaded = modelDownloader.isDownloaded(model)
                    val dlState = downloadStates[model.id]
                        ?: if (downloaded) DownloadState.Ready else DownloadState.Idle
                    ModelUiItem(
                        id = model.id,
                        displayName = model.displayName,
                        description = model.description,
                        sizeMb = model.sizeMb,
                        recommended = model.recommended,
                        isDownloaded = downloaded || dlState is DownloadState.Ready,
                        downloadState = dlState
                    )
                }
                _uiState.update {
                    it.copy(models = items, selectedModelId = prefs.localModelName)
                }
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
        modelDownloader.startDownload(modelId)
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
        }
    }
}
