package com.iris.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------
data class HomeUiState(
    val coreState    : IrisCoreState = IrisCoreState.IDLE,
    val amplitude    : Float         = 0f,
    val ttsProgress  : Float         = 0f,
    val isMicOn      : Boolean       = false,
    val isScreenCtrl : Boolean       = false,
    val statusText   : String        = "Dinlemeye hazır",
    val errorMessage : String?       = null
)

// ---------------------------------------------------------------------------
// HomeViewModel
// ---------------------------------------------------------------------------
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val audioRecorder          : AudioRecorder,
    private val transcribeAudioUseCase : TranscribeAudioUseCase,
    private val sendMessageUseCase     : SendMessageUseCase,
    private val conversationRepository : ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // In-memory history kept in sync with Room for fast LLM context access
    private val history = mutableListOf<ChatMessage>()

    // Active pipeline job — cancelled on stop()
    private var pipelineJob: Job? = null

    init {
        viewModelScope.launch {
            history.addAll(conversationRepository.getHistory())
        }
    }

    // ---------------------------------------------------------------------------
    // Mic toggle
    // ---------------------------------------------------------------------------
    fun onMicToggle() {
        if (_uiState.value.isMicOn) {
            onStop()
            return
        }
        startVoicePipeline()
    }

    // ---------------------------------------------------------------------------
    // Full voice pipeline: record → STT → LLM → TTS
    // ---------------------------------------------------------------------------
    private fun startVoicePipeline() {
        pipelineJob = viewModelScope.launch {
            // 1. LISTENING
            _uiState.update {
                it.copy(
                    isMicOn      = true,
                    coreState    = IrisCoreState.LISTENING,
                    statusText   = "Dinliyorum...",
                    errorMessage = null
                )
            }

            // 2. Record until VAD silence
            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { amp -> _uiState.update { it.copy(amplitude = amp) } }
                )
            }.getOrElse { e ->
                handleError("Kayıt hatası", e)
                return@launch
            }

            // 3. THINKING — STT in progress
            _uiState.update {
                it.copy(
                    isMicOn    = false,
                    amplitude  = 0f,
                    coreState  = IrisCoreState.THINKING,
                    statusText = "Anlıyorum..."
                )
            }

            // 4. STT — Groq Whisper
            val transcript = runCatching {
                transcribeAudioUseCase(audioBytes)
            }.getOrElse { e ->
                handleError("Ses anlaşılamadı", e)
                return@launch
            }

            // 5. Persist user message
            val userMsg = ChatMessage(role = ChatMessage.Role.USER, content = transcript)
            val userMsgId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = userMsgId))

            // 6. THINKING — LLM in progress
            _uiState.update { it.copy(statusText = "Düşünüyorum...") }

            // 7. LLM — Gemini (with Groq fallback)
            val reply = runCatching {
                sendMessageUseCase(history.toList())
            }.getOrElse { e ->
                // Rollback user message from in-memory history on failure
                history.removeLastOrNull()
                handleError("Yanıt alınamadı", e)
                return@launch
            }

            // 8. Persist assistant message
            val assistantMsg = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
            val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
            history.add(assistantMsg.copy(id = assistantMsgId))

            // 9. SPEAKING — TTS
            _uiState.update {
                it.copy(
                    coreState  = IrisCoreState.SPEAKING,
                    statusText = "Konuşuyorum..."
                )
            }

            // TODO: EdgeTTS synthesis + playback (Phase 1 Part A)
            kotlinx.coroutines.delay(2000L)

            _uiState.update {
                it.copy(
                    coreState   = IrisCoreState.IDLE,
                    ttsProgress = 0f,
                    statusText  = "Dinlemeye hazır"
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Stop / interrupt
    // ---------------------------------------------------------------------------
    fun onStop() {
        pipelineJob?.cancel()
        pipelineJob = null
        _uiState.update {
            it.copy(
                coreState   = IrisCoreState.IDLE,
                isMicOn     = false,
                amplitude   = 0f,
                ttsProgress = 0f,
                statusText  = "Dinlemeye hazır"
            )
        }
        // TODO: ttsPlayer.stop() (Phase 1 Part A)
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------
    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
        // TODO: Start/stop AccessibilityService (Phase 3)
    }

    // ---------------------------------------------------------------------------
    // TTS progress update — called by TTS player (Phase 1 Part A)
    // ---------------------------------------------------------------------------
    fun onTtsProgressUpdate(progress: Float) {
        _uiState.update {
            it.copy(
                coreState   = IrisCoreState.SPEAKING,
                ttsProgress = progress.coerceIn(0f, 1f),
                statusText  = "Konuşuyorum..."
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Error handling
    // ---------------------------------------------------------------------------
    private fun handleError(context: String, e: Throwable) {
        val msg = when (e) {
            is IrisException.NetworkException   -> "$context: İnternet bağlantısı yok"
            is IrisException.RateLimitException -> "$context: API limiti aşıldı"
            is IrisException.AuthException      -> "$context: API anahtarı eksik"
            is IrisException.SttException       -> "$context: ${e.message}"
            is IrisException.LlmException       -> "$context: ${e.message}"
            else                                -> "$context: Bilinmeyen hata"
        }
        _uiState.update {
            it.copy(
                coreState    = IrisCoreState.IDLE,
                isMicOn      = false,
                amplitude    = 0f,
                statusText   = "Dinlemeye hazır",
                errorMessage = msg
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
    }
}