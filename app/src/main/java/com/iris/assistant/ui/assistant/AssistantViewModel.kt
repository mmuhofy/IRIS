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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatBubble(
    val text: String,
    val isUser: Boolean
)

data class AssistantUiState(
    val textInput  : String           = "",
    val messages   : List<ChatBubble> = emptyList(),
    val amplitude  : Float            = 0f,
    val isListening: Boolean          = false,
    val isThinking : Boolean          = false,
    val isSpeaking : Boolean          = false,
    val isDone     : Boolean          = false
)

class AssistantViewModel(
    private val context              : Context,
    private val audioRecorder        : AudioRecorder,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val sendMessageUseCase   : SendMessageUseCase,
    private val ttsProvider          : TtsProvider
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
            _uiState.update { it.copy(isListening = true, amplitude = 0f) }

            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { amp -> _uiState.update { it.copy(amplitude = amp) } }
                )
            }.getOrElse { e ->
                Log.e(TAG, "Recording failed", e)
                finish()
                return@launch
            }

            if (audioBytes.isEmpty()) {
                finish()
                return@launch
            }

            _uiState.update { it.copy(isListening = false, amplitude = 0f, isThinking = true) }

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
                    ChatMessage(role = ChatMessage.Role.USER, content = transcript)
                ))
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                finish()
                return@launch
            }

            _uiState.update { it.copy(isThinking = false, isSpeaking = true) }
            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = reply, isUser = false))
            }

            ttsProvider.speak(
                text       = reply,
                onProgress = { p -> _uiState.update { it.copy(amplitude = p.coerceIn(0f, 1f)) } },
                onDone     = {
                    _uiState.update { it.copy(isSpeaking = false, amplitude = 0f) }
                    finish()
                }
            )
        }
    }

    private fun sendMessage(text: String) {
        _uiState.update { it.copy(textInput = "", isThinking = true) }

        pipelineJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = text, isUser = true))
            }

            val reply: String
            try {
                reply = sendMessageUseCase(listOf(
                    ChatMessage(role = ChatMessage.Role.USER, content = text)
                ))
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                _uiState.update { it.copy(isThinking = false) }
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatBubble(text = "Bir hata oluştu.", isUser = false))
                }
                return@launch
            }

            _uiState.update { it.copy(isThinking = false, isSpeaking = true) }
            _uiState.update { state ->
                state.copy(messages = state.messages + ChatBubble(text = reply, isUser = false))
            }

            ttsProvider.speak(
                text       = reply,
                onProgress = { p -> _uiState.update { it.copy(amplitude = p.coerceIn(0f, 1f)) } },
                onDone     = {
                    _uiState.update { it.copy(isSpeaking = false, amplitude = 0f) }
                    finish()
                }
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
        _uiState.update { it.copy(isDone = true, isListening = false, isThinking = false, isSpeaking = false) }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        ttsProvider.release()
    }
}
