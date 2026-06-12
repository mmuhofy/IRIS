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

    // Conversation history — persisted to Room
    // In-memory list kept in sync for fast LLM context access
    private val history = mutableListOf<ChatMessage>()

    init {
        // Load existing history from Room on startup
        viewModelScope.launch {
            history.addAll(conversationRepository.getHistory())
        }
    }

    // Active pipeline job — cancelled on stop()
    private var pipelineJob: Job? = null

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
                    isMicOn    = true,
                    coreState  = IrisCoreState.LISTENING,
                    statusText = "Dinliyorum...",
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

            // Add user message to history and persist
            val userMsg = ChatMessage(role = ChatMessage.Role.USER, content = transcript)
            val savedId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = savedId))

            // 5. THINKING — LLM in progress
            _uiState.update { it.copy(statusText = "Düşünüyorum...") }

            // 6. LLM — Gemini (with Groq fallback)
            val reply = runCatching {
                sendMessageUseCase(history.toList())
            }.getOrElse { e ->
                history.removeLastOrNull() // rollback user message on failure
                handleError("Yanıt alınamadı", e)
                return@launch
            }

            // Add assistant message to history and persist
            val assistantMsg = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
            val savedId = conversationRepository.saveMessage(assistantMsg)
            history.add(assistantMsg.copy(id = savedId))

            // 7. SPEAKING — TTS
            _uiState.update {
                it.copy(
                    coreState  = IrisCoreState.SPEAKING,
                    statusText = "Konuşuyorum..."
                )
            }

            // TODO: TTS synthesis + playback (Phase 1 — EdgeTTS client)
            // Placeholder: simulate TTS duration then return to IDLE
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
    // Stop / interrupt — cancels active pipeline immediately
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
        // TODO: ttsPlayer.stop() (Phase 1)
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------
    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
        // TODO: Start/stop AccessibilityService (Phase 3)
    }

    // ---------------------------------------------------------------------------
    // TTS progress update — called by TTS player (Phase 1)
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
    // Error handling — maps IrisException to user-facing message
    // ---------------------------------------------------------------------------
    private fun handleError(context: String, e: Throwable) {
        val msg = when (e) {
            is IrisException.NetworkException  -> "$context: İnternet bağlantısı yok"
            is IrisException.RateLimitException -> "$context: API limiti aşıldı"
            is IrisException.AuthException     -> "$context: API anahtarı eksik"
            is IrisException.SttException      -> "$context: ${e.message}"
            is IrisException.LlmException      -> "$context: ${e.message}"
            else                               -> "$context: Bilinmeyen hata"
        }
        _uiState.update {
            it.copy(
                coreState  = IrisCoreState.IDLE,
                isMicOn    = false,
                amplitude  = 0f,
                statusText = "Dinlemeye hazır",
                errorMessage = msg
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
    }
}