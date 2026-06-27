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
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import com.iris.assistant.data.remote.tts.TtsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

data class ChatBubble(
    val text: String,
    val isUser: Boolean
)

enum class CapsuleMode {
    LISTENING,
    THINKING,
    REPLY,
    INPUT,
}

data class AssistantUiState(
    val capsuleMode : CapsuleMode        = CapsuleMode.LISTENING,
    val textInput   : String             = "",
    val messages    : List<ChatBubble>   = emptyList(),
    val amplitude   : Float              = 0f,
    val replyText   : String             = "",
    val isDismissed : Boolean            = false,
    val errorMessage: String?            = null,
)

// ---------------------------------------------------------------------------
// AssistantViewModel
// ---------------------------------------------------------------------------

class AssistantViewModel(
    private val context               : Context,
    private val audioRecorder         : AudioRecorder,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val sendMessageUseCase    : SendMessageUseCase,
    private val ttsProvider           : TtsProvider
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantViewModel"
    }

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var pipelineJob: Job? = null

    init {
        startVoicePipeline()
    }

    // -----------------------------------------------------------------------
    // Input changes
    // -----------------------------------------------------------------------

    fun onTextInputChanged(text: String) {
        _uiState.update { it.copy(textInput = text) }
    }

    // -----------------------------------------------------------------------
    // Tap capsule — expand to input mode
    // -----------------------------------------------------------------------

    fun onCapsuleTap() {
        val s = _uiState.value
        if (s.capsuleMode == CapsuleMode.INPUT) return
        pipelineJob?.cancel()
        pipelineJob = null
        ttsProvider.stop()
        _uiState.update {
            it.copy(
                capsuleMode = CapsuleMode.INPUT,
                amplitude = 0f,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Send text from input
    // -----------------------------------------------------------------------

    fun sendText() {
        val text = _uiState.value.textInput.trim()
        if (text.isEmpty()) return
        _uiState.update {
            it.copy(
                textInput = "",
                capsuleMode = CapsuleMode.THINKING,
                messages = it.messages + ChatBubble(text = text, isUser = true),
            )
        }
        sendToLlm(text)
    }

    // -----------------------------------------------------------------------
    // Start voice pipeline
    // -----------------------------------------------------------------------

    fun startVoicePipeline() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            dismiss()
            return
        }

        pipelineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    capsuleMode = CapsuleMode.LISTENING,
                    amplitude = 0f,
                )
            }

            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { amp ->
                        _uiState.update { it.copy(amplitude = amp) }
                    }
                )
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "Recording failed", e)
                _uiState.update {
                    it.copy(
                        capsuleMode = CapsuleMode.LISTENING,
                        errorMessage = "Kayıt başarısız",
                    )
                }
                return@launch
            }

            if (audioBytes.isEmpty()) {
                _uiState.update {
                    it.copy(
                        capsuleMode = CapsuleMode.LISTENING,
                        amplitude = 0f,
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    capsuleMode = CapsuleMode.THINKING,
                    amplitude = 0f,
                )
            }

            val transcript = runCatching {
                transcribeAudioUseCase(audioBytes)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "STT failed", e)
                _uiState.update {
                    it.copy(
                        capsuleMode = CapsuleMode.LISTENING,
                        errorMessage = "Ses anlaşılamadı",
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatBubble(text = transcript, isUser = true),
                )
            }

            sendToLlm(transcript)
        }
    }

    // -----------------------------------------------------------------------
    // LLM call (shared by voice + text)
    // -----------------------------------------------------------------------

    private fun sendToLlm(text: String) {
        pipelineJob = viewModelScope.launch {
            val reply: String
            try {
                reply = sendMessageUseCase(listOf(
                    ChatMessage(role = ChatMessage.Role.USER, content = text)
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IrisException.PermissionRequiredException) {
                _uiState.update {
                    it.copy(
                        capsuleMode = CapsuleMode.REPLY,
                        errorMessage = "İzin gerekiyor: ${e.permission}",
                        messages = it.messages + ChatBubble(text = "İzin gerekiyor: ${e.permission}", isUser = false),
                    )
                }
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "LLM failed", e)
                _uiState.update {
                    it.copy(
                        capsuleMode = CapsuleMode.REPLY,
                        errorMessage = "Yanıt alınamadı",
                        messages = it.messages + ChatBubble(text = "Bir hata oluştu.", isUser = false),
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    capsuleMode = CapsuleMode.REPLY,
                    replyText = reply,
                    messages = state.messages + ChatBubble(text = reply, isUser = false),
                )
            }

            ttsProvider.speak(
                text = reply,
                onProgress = { p ->
                    _uiState.update { it.copy(amplitude = p.coerceIn(0f, 1f)) }
                },
                onDone = {
                    _uiState.update {
                        it.copy(amplitude = 0f)
                    }
                    // Stay in REPLY mode until user dismisses or taps
                },
            )
        }
    }

    // -----------------------------------------------------------------------
    // Dismiss
    // -----------------------------------------------------------------------

    fun dismiss() {
        pipelineJob?.cancel()
        pipelineJob = null
        ttsProvider.stop()
        _uiState.update {
            it.copy(isDismissed = true, amplitude = 0f, errorMessage = null)
        }
    }

    fun release() {
        pipelineJob?.cancel()
        ttsProvider.release()
        onCleared()
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        ttsProvider.release()
    }
}
