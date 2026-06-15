package com.iris.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.remote.tts.TtsProvider
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.usecase.SendMessageUseCase
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
data class PermissionRequest(
    val permission: String,
    val rationale : String
)

data class ChatUiState(
    val messages         : List<ChatMessage> = emptyList(),
    val inputText        : String            = "",
    val isLoading        : Boolean           = false,
    val errorMessage     : String?           = null,
    val permissionRequest: PermissionRequest? = null
)

// ---------------------------------------------------------------------------
// ChatViewModel
// ---------------------------------------------------------------------------
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val sendMessageUseCase    : SendMessageUseCase,
    private val ttsProvider           : TtsProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val history      = mutableListOf<ChatMessage>()
    private var sendJob      : Job? = null
    private var pendingMessage: String? = null

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val loaded = conversationRepository.getHistory()
            history.addAll(loaded)
            _uiState.update { it.copy(messages = history.toList()) }
        }
    }

    // ---------------------------------------------------------------------------
    // Input field update
    // ---------------------------------------------------------------------------
    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ---------------------------------------------------------------------------
    // Send message
    // ---------------------------------------------------------------------------
    fun onSend() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        sendJob = viewModelScope.launch {
            // Clear input + show loading
            _uiState.update { it.copy(inputText = "", isLoading = true, errorMessage = null) }

            // Persist + add user message
            val userMsg = ChatMessage(role = ChatMessage.Role.USER, content = text)
            val userMsgId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = userMsgId))
            _uiState.update { it.copy(messages = history.toList()) }

            // LLM
            val reply: String
            try {
                reply = sendMessageUseCase(history.toList())
            } catch (e: IrisException.PermissionRequiredException) {
                history.removeLastOrNull()
                pendingMessage = text
                _uiState.update {
                    it.copy(
                        messages          = history.toList(),
                        isLoading         = false,
                        permissionRequest = PermissionRequest(
                            permission = e.permission,
                            rationale  = e.rationale
                        )
                    )
                }
                return@launch
            } catch (e: Exception) {
                history.removeLastOrNull()
                _uiState.update {
                    it.copy(
                        messages     = history.toList(),
                        isLoading    = false,
                        errorMessage = mapError(e)
                    )
                }
                return@launch
            }

            // Persist + add assistant message
            val assistantMsg = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
            val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
            history.add(assistantMsg.copy(id = assistantMsgId))
            _uiState.update { it.copy(messages = history.toList(), isLoading = false) }

            // TTS — speak reply
            ttsProvider.speak(text = reply)
        }
    }

    // ---------------------------------------------------------------------------
    // Permission request result
    // ---------------------------------------------------------------------------

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionRequest = null) }

        if (granted) {
            val text = pendingMessage ?: return
            pendingMessage = null
            retrySend(text)
        } else {
            _uiState.update { it.copy(errorMessage = "İzin reddedildi") }
        }
    }

    private fun retrySend(text: String) {
        sendJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val userMsg = ChatMessage(role = ChatMessage.Role.USER, content = text)
            val userMsgId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = userMsgId))
            _uiState.update { it.copy(messages = history.toList()) }

            try {
                val reply = sendMessageUseCase(history.toList())

                val assistantMsg = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
                val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
                history.add(assistantMsg.copy(id = assistantMsgId))
                _uiState.update { it.copy(messages = history.toList(), isLoading = false) }

                ttsProvider.speak(text = reply)
            } catch (e: IrisException.PermissionRequiredException) {
                history.removeLastOrNull()
                pendingMessage = text
                _uiState.update {
                    it.copy(
                        messages          = history.toList(),
                        isLoading         = false,
                        permissionRequest = PermissionRequest(
                            permission = e.permission,
                            rationale  = e.rationale
                        )
                    )
                }
            } catch (e: Exception) {
                history.removeLastOrNull()
                _uiState.update {
                    it.copy(
                        messages     = history.toList(),
                        isLoading    = false,
                        errorMessage = mapError(e)
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Stop
    // ---------------------------------------------------------------------------
    fun onStop() {
        sendJob?.cancel()
        ttsProvider.stop()
        _uiState.update { it.copy(isLoading = false) }
    }

    // ---------------------------------------------------------------------------
    // Clear error
    // ---------------------------------------------------------------------------
    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun mapError(e: Throwable): String = when (e) {
        is IrisException.NetworkException   -> "İnternet bağlantısı yok"
        is IrisException.RateLimitException -> "API limiti aşıldı"
        is IrisException.AuthException      -> "API anahtarı eksik"
        is IrisException.LlmException       -> e.message ?: "LLM hatası"
        else                                -> "Bilinmeyen hata"
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        ttsProvider.release()
    }
}