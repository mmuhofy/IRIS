package com.iris.assistant.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.remote.tts.TtsProvider
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import com.iris.assistant.service.wakeword.WakeWordManager
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------
data class PermissionRequest(
    val permission: String,
    val rationale : String
)

data class HomeUiState(
    val coreState        : IrisCoreState      = IrisCoreState.IDLE,
    val amplitude        : Float              = 0f,
    val ttsProgress      : Float              = 0f,
    val isMuted          : Boolean            = false,
    val isScreenCtrl     : Boolean            = false,
    val statusText       : String             = "Dinlemeye hazır",
    val modelName        : String             = Constants.GEMINI_MODEL,
    val captionText      : String?            = null,
    val errorMessage     : String?            = null,
    val permissionRequest: PermissionRequest? = null
)

// ---------------------------------------------------------------------------
// HomeViewModel
// ---------------------------------------------------------------------------
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context           : Context,
    private val audioRecorder                        : AudioRecorder,
    private val transcribeAudioUseCase               : TranscribeAudioUseCase,
    private val sendMessageUseCase                   : SendMessageUseCase,
    private val conversationRepository               : ConversationRepository,
    @Named("gemini")  private val geminiTts          : TtsProvider,
    @Named("mms")     private val mmsTts             : TtsProvider,
    @Named("android") private val androidTts         : TtsProvider,
    private val wakeWordManager                      : WakeWordManager,
    private val preferencesRepository                : PreferencesRepository
) : ViewModel() {

    private var activeTtsProvider: TtsProvider = geminiTts
        private set

    companion object {
        private const val TAG = "HomeViewModel"

        private val STOP_KEYWORDS = setOf(
            "dur", "yeter", "stop", "kes", "tamam", "yeterli", "kapat",
            "tamamm", "durdur", "sus", "yeteeer", "kapa"
        )
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val history          = mutableListOf<ChatMessage>()
    private var pipelineJob      : Job? = null
    private var pendingTranscript: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            history.addAll(conversationRepository.getHistory())
        }

        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(modelName = prefs.llmModel) }
                activeTtsProvider = when (prefs.ttsProvider) {
                    TtsProviderType.GEMINI  -> geminiTts
                    TtsProviderType.MMS     -> mmsTts
                    TtsProviderType.ANDROID -> androidTts
                }
            }
        }

        viewModelScope.launch {
            wakeWordManager.detections.collect {
                val state = _uiState.value
                if (state.coreState == IrisCoreState.IDLE && !state.isMuted) {
                    startVoicePipeline()
                }
        }
        }
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    fun onScreenVisible() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (prefs.backgroundListening) {
                wakeWordManager.startListening()
            }
        }
    }

    fun onScreenHidden() {
        wakeWordManager.stopListening()
    }

    // ---------------------------------------------------------------------------
    // Mic toggle
    // ---------------------------------------------------------------------------

    fun onMicToggle() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update {
            it.copy(
                isMuted    = newMuted,
                statusText = if (newMuted) "Sessize alındı" else "Dinlemeye hazır"
            )
        }
        if (!newMuted && _uiState.value.coreState == IrisCoreState.IDLE) {
            startVoicePipeline()
        }
    }

    // ---------------------------------------------------------------------------
    // Voice pipeline — runs on Dispatchers.IO to keep Main thread free for UI
    // MutableStateFlow.update() is thread-safe; all state changes work from IO.
    // UNTESTED after dispatcher change — verify wakeWordManager is thread-safe.
    // ---------------------------------------------------------------------------

    fun startVoicePipeline() {
        pipelineJob = viewModelScope.launch(Dispatchers.IO) {

            // Step 0 — Permission check (ContextCompat reads a binder cache, safe on IO)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _uiState.update {
                    it.copy(
                        permissionRequest = PermissionRequest(
                            permission = Manifest.permission.RECORD_AUDIO,
                            rationale  = "IRIS sesinizi duyabilmek için mikrofon iznine ihtiyaç duyar."
                        )
                    )
                }
                return@launch
            }

            // Step 1 — Pause wake word detection to free the mic
            wakeWordManager.stopListening()

            try {
                // Step 2 — LISTENING state
                _uiState.update {
                    it.copy(
                        isMuted      = false,
                        coreState    = IrisCoreState.LISTENING,
                        statusText   = "Dinliyorum...",
                        errorMessage = null
                    )
                }

                // Step 3 — Record until VAD silence
                // AudioRecorder already dispatches internally to IO, but we're already on IO here.
                val audioBytes = runCatching {
                    audioRecorder.recordUntilSilence(
                        onAmplitude = { amp -> _uiState.update { it.copy(amplitude = amp) } }
                    )
                }.getOrElse { e ->
                    handleError("Kayıt hatası", e)
                    return@launch
                }

                // Step 4 — Skip if no audio detected
                if (audioBytes.isEmpty()) {
                    Log.d(TAG, "no speech detected")
                    _uiState.update {
                        it.copy(
                            coreState  = IrisCoreState.IDLE,
                            amplitude  = 0f,
                            statusText = "Dinlemeye hazır"
                        )
                    }
                    return@launch
                }

                // Step 5 — THINKING (STT)
                _uiState.update {
                    it.copy(
                        amplitude  = 0f,
                        coreState  = IrisCoreState.THINKING,
                        statusText = "Anlıyorum..."
                    )
                }

                // Step 6 — STT via Groq Whisper (network — stays on IO, Main is free)
                val transcript = runCatching {
                    transcribeAudioUseCase(audioBytes)
                }.getOrElse { e ->
                    handleError("Ses anlaşılamadı", e)
                    return@launch
                }

                // Step 7 — Check for stop keywords
                val normalized = transcript.lowercase().trim()
                if (STOP_KEYWORDS.any {
                        normalized == it ||
                        normalized.startsWith("$it ") ||
                        normalized.endsWith(" $it")
                    }) {
                    Log.d(TAG, "stop keyword: \"$normalized\"")
                    _uiState.update {
                        it.copy(
                            coreState  = IrisCoreState.IDLE,
                            amplitude  = 0f,
                            statusText = "Dinlemeye hazır"
                        )
                    }
                    return@launch
                }

                // Step 8 — Persist user message (Room dispatches its own IO internally)
                val userMsg   = ChatMessage(role = ChatMessage.Role.USER, content = transcript)
                val userMsgId = conversationRepository.saveMessage(userMsg)
                history.add(userMsg.copy(id = userMsgId))

                // Step 9 — THINKING (LLM)
                _uiState.update { it.copy(statusText = "Düşünüyorum...") }

                // Step 10 — LLM via Gemini (network — stays on IO, Main is free)
                val reply: String
                try {
                    reply = sendMessageUseCase(history.toList())
                } catch (e: IrisException.PermissionRequiredException) {
                    history.removeLastOrNull()
                    pendingTranscript = transcript
                    _uiState.update {
                        it.copy(
                            coreState         = IrisCoreState.IDLE,
                            statusText        = "İzin gerekiyor",
                            permissionRequest = PermissionRequest(
                                permission = e.permission,
                                rationale  = e.rationale
                            )
                        )
                    }
                    return@launch
                } catch (e: Exception) {
                    history.removeLastOrNull()
                    handleError("Yanıt alınamadı", e)
                    return@launch
                }

                // Step 11 — Persist assistant message
                val assistantMsg   = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
                val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
                history.add(assistantMsg.copy(id = assistantMsgId))

                // Step 12 — SPEAKING
                _uiState.update {
                    it.copy(
                        coreState   = IrisCoreState.SPEAKING,
                        statusText  = "Konuşuyorum...",
                        captionText = reply
                    )
                }

                try {
                    activeTtsProvider.speak(
                        text       = reply,
                        onProgress = { p -> _uiState.update { it.copy(ttsProgress = p.coerceIn(0f, 1f)) } },
                        onDone     = {
                            val muted = _uiState.value.isMuted
                            _uiState.update {
                                it.copy(
                                    coreState   = IrisCoreState.IDLE,
                                    ttsProgress = 0f,
                                    statusText  = if (muted) "Sessize alındı" else "Dinlemeye hazır",
                                    captionText = null
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    _uiState.update { it.copy(captionText = null) }
                    handleError("Konuşma hatası", e)
                }

            } finally {
                if (!_uiState.value.isMuted) {
                    wakeWordManager.startListening()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Stop / interrupt
    // ---------------------------------------------------------------------------

    fun onStop() {
        pipelineJob?.cancel()
        pipelineJob = null
        activeTtsProvider.stop()
        val muted = _uiState.value.isMuted
        _uiState.update {
            it.copy(
                coreState    = IrisCoreState.IDLE,
                amplitude    = 0f,
                ttsProgress  = 0f,
                statusText   = if (muted) "Sessize alındı" else "Dinlemeye hazır",
                captionText  = null
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Permission result
    // ---------------------------------------------------------------------------

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionRequest = null) }

        if (granted) {
            val transcript = pendingTranscript ?: return
            pendingTranscript = null
            retryLlmStep(transcript)
        } else {
            _uiState.update { it.copy(statusText = "İzin reddedildi") }
        }
    }

    // Runs on IO — same rationale as startVoicePipeline
    private fun retryLlmStep(transcript: String) {
        pipelineJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(statusText = "Düşünüyorum...") }

            val userMsg   = ChatMessage(role = ChatMessage.Role.USER, content = transcript)
            val userMsgId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = userMsgId))

            try {
                val reply = sendMessageUseCase(history.toList())

                val assistantMsg   = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
                val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
                history.add(assistantMsg.copy(id = assistantMsgId))

                _uiState.update {
                    it.copy(coreState = IrisCoreState.SPEAKING, statusText = "Konuşuyorum...")
                }

                activeTtsProvider.speak(
                    text       = reply,
                    onProgress = { p -> _uiState.update { it.copy(ttsProgress = p.coerceIn(0f, 1f)) } },
                    onDone     = {
                        val muted = _uiState.value.isMuted
                        _uiState.update {
                            it.copy(
                                coreState   = IrisCoreState.IDLE,
                                ttsProgress = 0f,
                                statusText  = if (muted) "Sessize alındı" else "Dinlemeye hazır"
                            )
                        }
                    }
                )
            } catch (e: IrisException.PermissionRequiredException) {
                history.removeLastOrNull()
                pendingTranscript = transcript
                _uiState.update {
                    it.copy(
                        coreState         = IrisCoreState.IDLE,
                        statusText        = "İzin gerekiyor",
                        permissionRequest = PermissionRequest(
                            permission = e.permission,
                            rationale  = e.rationale
                        )
                    )
                }
            } catch (e: Exception) {
                history.removeLastOrNull()
                handleError("Yanıt alınamadı", e)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------

    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
    }

    // ---------------------------------------------------------------------------
    // Error handling — thread-safe, works from IO
    // ---------------------------------------------------------------------------

    private fun handleError(ctx: String, e: Throwable) {
        val msg = when (e) {
            is IrisException.NetworkException   -> "$ctx: İnternet bağlantısı yok"
            is IrisException.RateLimitException -> "$ctx: API limiti aşıldı"
            is IrisException.AuthException      -> "$ctx: API anahtarı eksik"
            is IrisException.SttException       -> "$ctx: ${e.message}"
            is IrisException.LlmException       -> "$ctx: ${e.message}"
            is IrisException.TtsException       -> "$ctx: ${e.message}"
            else                                -> "$ctx: Bilinmeyen hata"
        }
        Log.e(TAG, "handleError: $msg", e)
        val muted = _uiState.value.isMuted
        _uiState.update {
            it.copy(
                coreState    = IrisCoreState.IDLE,
                amplitude    = 0f,
                statusText   = if (muted) "Sessize alındı" else "Dinlemeye hazır",
                errorMessage = msg
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        activeTtsProvider.release()
    }
}