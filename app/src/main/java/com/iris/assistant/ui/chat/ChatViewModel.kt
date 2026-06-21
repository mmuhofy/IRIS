package com.iris.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

data class ChatUiState(
    val messages      : List<ChatMessage> = emptyList(),
    val inputText     : String            = "",
    val isThinking    : Boolean           = false,  // LLM in-flight
    val isRecording   : Boolean           = false,  // mic active
    val isTranscribing: Boolean           = false,  // Whisper in-flight
    val errorMessage  : String?           = null,
    val conversationId: Long              = 0L,
)

// ---------------------------------------------------------------------------
// ChatViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepo  : ConversationRepository,
    private val sendMessage       : SendMessageUseCase,
    private val transcribeAudio   : TranscribeAudioUseCase,
    private val audioRecorder     : AudioRecorder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sendJob      : Job? = null
    private var recordingJob : Job? = null
    private var observeJob   : Job? = null
    private var conversationId: Long = 0L

    // ---------------------------------------------------------------------------
    // Init - called from ChatScreen once conversationId is known
    // ---------------------------------------------------------------------------

    fun init(rawId: Long) {
        if (conversationId == rawId && rawId != 0L) return // already loaded

        viewModelScope.launch {
            // 0 = "new conversation" - create one on the fly
            val id = if (rawId == 0L) {
                conversationRepo.createConversation()
            } else {
                rawId
            }
            conversationId = id
            _uiState.update { it.copy(conversationId = id) }
            observeMessages(id)
        }
    }

    private fun observeMessages(id: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            conversationRepo.observeMessages(id)
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
                .collect { msgs -> _uiState.update { it.copy(messages = msgs) } }
        }
    }

    // ---------------------------------------------------------------------------
    // Text input
    // ---------------------------------------------------------------------------

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ---------------------------------------------------------------------------
    // Send (text path)
    // ---------------------------------------------------------------------------

    fun onSend() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isThinking) return

        _uiState.update { it.copy(inputText = "") }
        sendToLlm(text)
    }

    // ---------------------------------------------------------------------------
    // Voice path - mic toggle leads to recordUntilSilence, then Whisper, then send.
    // AudioRecorder exposes a single suspend function (recordUntilSilence),
    // not start()/stop()/release(). Toggling off mid-recording cancels the job.
    // ---------------------------------------------------------------------------

    fun onMicToggle() {
        if (_uiState.value.isRecording) {
            recordingJob?.cancel()
            _uiState.update { it.copy(isRecording = false) }
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (_uiState.value.isThinking || _uiState.value.isTranscribing) return
        _uiState.update { it.copy(isRecording = true) }

        recordingJob = viewModelScope.launch {
            try {
                val audioBytes = audioRecorder.recordUntilSilence(
                    onAmplitude = { /* no amplitude UI in ChatScreen yet */ }
                )
                _uiState.update { it.copy(isRecording = false, isTranscribing = true) }

                if (audioBytes.isEmpty()) {
                    _uiState.update { it.copy(isTranscribing = false) }
                    return@launch
                }
                val transcript = transcribeAudio(audioBytes)
                _uiState.update { it.copy(isTranscribing = false) }
                if (transcript.isNotBlank()) sendToLlm(transcript)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRecording    = false,
                        isTranscribing = false,
                        errorMessage   = "Ses tanima basarisiz: " + (e.message ?: "")
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Core LLM flow
    // ---------------------------------------------------------------------------

    private fun sendToLlm(text: String) {
        val convId = conversationId
        if (convId == 0L) return

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            // 1. Persist user message
            val userMsg = ChatMessage(
                conversationId = convId,
                role           = ChatMessage.Role.USER,
                content        = text,
            )
            conversationRepo.saveMessage(userMsg)

            // 2. Auto-title the conversation from first user message
            conversationRepo.generateTitleIfNeeded(convId)

            // 3. Fetch history and call LLM
            _uiState.update { it.copy(isThinking = true, errorMessage = null) }
            try {
                val history = conversationRepo.getHistory(convId)
                val reply   = sendMessage(history)

                // 4. Persist assistant reply
                conversationRepo.saveMessage(
                    ChatMessage(
                        conversationId = convId,
                        role           = ChatMessage.Role.ASSISTANT,
                        content        = reply,
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Hata: " + (e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Stop / interrupt
    // ---------------------------------------------------------------------------

    fun onStop() {
        sendJob?.cancel()
        recordingJob?.cancel()
        _uiState.update {
            it.copy(
                isThinking     = false,
                isRecording    = false,
                isTranscribing = false,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Error dismiss
    // ---------------------------------------------------------------------------

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
    }
}