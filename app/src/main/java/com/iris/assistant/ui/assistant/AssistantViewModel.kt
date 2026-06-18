package com.iris.assistant.ui.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import com.iris.assistant.data.remote.tts.TtsProvider
import com.iris.assistant.ui.home.IrisCoreState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(
    val coreState  : IrisCoreState = IrisCoreState.IDLE,
    val amplitude  : Float         = 0f,
    val statusText : String        = "",
    val transcript : String        = "",
    val response   : String        = "",
    val isDone     : Boolean       = false
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context    : Context,
    private val audioRecorder                 : AudioRecorder,
    private val transcribeAudioUseCase        : TranscribeAudioUseCase,
    private val sendMessageUseCase            : SendMessageUseCase,
    private val ttsProvider                   : TtsProvider
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantViewModel"
    }

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var pipelineJob: Job? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.update { it.copy(isDone = true) }
            return
        }

        pipelineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(coreState = IrisCoreState.LISTENING, statusText = "Dinliyorum...")
            }

            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { amp -> _uiState.update { it.copy(amplitude = amp) } }
                )
            }.getOrElse { e ->
                Log.e(TAG, "Recording failed", e)
                _uiState.update { it.copy(isDone = true) }
                return@launch
            }

            _uiState.update {
                it.copy(amplitude = 0f, coreState = IrisCoreState.THINKING, statusText = "Anlıyorum...")
            }

            val transcript = runCatching {
                transcribeAudioUseCase(audioBytes)
            }.getOrElse { e ->
                Log.e(TAG, "STT failed", e)
                _uiState.update { it.copy(isDone = true) }
                return@launch
            }

            _uiState.update { it.copy(transcript = transcript, statusText = "Düşünüyorum...") }

            val reply: String
            try {
                reply = sendMessageUseCase(listOf(
                    com.iris.assistant.domain.model.ChatMessage(
                        role = com.iris.assistant.domain.model.ChatMessage.Role.USER,
                        content = transcript
                    )
                ))
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                _uiState.update { it.copy(isDone = true) }
                return@launch
            }

            _uiState.update { it.copy(response = reply, coreState = IrisCoreState.SPEAKING, statusText = "Konuşuyorum...") }

            ttsProvider.speak(
                text       = reply,
                onProgress = { p -> _uiState.update { it.copy(amplitude = p.coerceIn(0f, 1f)) } },
                onDone     = {
                    _uiState.update { it.copy(isDone = true) }
                }
            )
        }
    }

    fun stop() {
        pipelineJob?.cancel()
        pipelineJob = null
        ttsProvider.stop()
        _uiState.update { it.copy(isDone = true) }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        ttsProvider.release()
    }
}
