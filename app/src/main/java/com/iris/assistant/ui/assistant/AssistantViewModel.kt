package com.iris.assistant.ui.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.domain.model.ChatMessage
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

data class ChatBubble(
    val text: String,
    val isUser: Boolean
)

data class AssistantUiState(
    val textInput  : String        = "",
    val messages   : List<ChatBubble> = emptyList(),
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

    fun onTextInputChanged(text: String) {
        _uiState.update { it.copy(textInput = text) }
    }

    fun sendText() {
        val text = _uiState.value.textInput.trim()
        if (text.isEmpty()) return
        sendMessage(text)
    }

    fun startVoicePipeline() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }

        pipelineJob = viewModelScope.launch {
            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { }
                )
            }.getOrElse { e ->
                Log.e(TAG, "Recording failed", e)
                finish()
                return@launch
            }

            val transcript = runCatching {
                transcribeAudioUseCase(audioBytes)
            }.getOrElse { e ->
                Log.e(TAG, "STT failed", e)
                finish()
                return@launch
            }

            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = transcript, isUser = true))
            }

            val reply: String
            try {
                reply = sendMessageUseCase(listOf(
                    ChatMessage(
                        role = ChatMessage.Role.USER,
                        content = transcript
                    )
                ))
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                finish()
                return@launch
            }

            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = reply, isUser = false))
            }

            ttsProvider.speak(
                text       = reply,
                onProgress = { },
                onDone     = { finish() }
            )
        }
    }

    private fun sendMessage(text: String) {
        _uiState.update { it.copy(textInput = "") }

        pipelineJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = text, isUser = true))
            }

            val reply: String
            try {
                reply = sendMessageUseCase(listOf(
                    ChatMessage(
                        role = ChatMessage.Role.USER,
                        content = text
                    )
                ))
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatBubble(text = "Bir hata oluştu.", isUser = false))
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = reply, isUser = false))
            }

            ttsProvider.speak(
                text       = reply,
                onProgress = { },
                onDone     = { finish() }
            )
        }
    }

    fun stop() {
        pipelineJob?.cancel()
        pipelineJob = null
        ttsProvider.stop()
        finish()
    }

    private fun finish() {
        _uiState.update { it.copy(isDone = true) }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        ttsProvider.release()
    }
}
