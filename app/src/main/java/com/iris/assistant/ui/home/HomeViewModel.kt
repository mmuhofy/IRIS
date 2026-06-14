package com.iris.assistant.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.remote.tts.TtsProvider
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import com.iris.assistant.service.WakeWordService
import com.iris.assistant.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------
data class HomeUiState(
    val coreState   : IrisCoreState = IrisCoreState.IDLE,
    val amplitude   : Float         = 0f,
    val ttsProgress : Float         = 0f,
    val isMuted     : Boolean       = false,
    val isScreenCtrl: Boolean       = false,
    val statusText  : String        = "Dinlemeye hazır",
    val errorMessage: String?       = null
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
    private val ttsProvider                          : TtsProvider,
    private val preferencesRepository                : PreferencesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val history     = mutableListOf<ChatMessage>()
    private var pipelineJob : Job? = null

    // ---------------------------------------------------------------------------
    // Wake word BroadcastReceiver
    // Registered/unregistered by HomeScreen's DisposableEffect — not here —
    // so the receiver lifetime matches the screen lifecycle, not the ViewModel.
    // ---------------------------------------------------------------------------
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                Log.d(TAG, "BroadcastReceiver: wake word detected")
                val state = _uiState.value
                if (state.coreState == IrisCoreState.IDLE && !state.isMuted) {
                    startVoicePipeline()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            history.addAll(conversationRepository.getHistory())
        }

        // Observe backgroundListening — start/stop WakeWordService accordingly.
        // distinctUntilChanged ensures we react to actual changes and also to
        // the first emission (which starts the service on app launch if enabled).
        viewModelScope.launch {
            preferencesRepository.preferences
                .map { it.backgroundListening }
                .distinctUntilChanged()
                .collect { enabled ->
                    Log.d(TAG, "backgroundListening → $enabled")
                    if (enabled) startWakeWordService() else stopWakeWordService()
                }
        }
    }

    // ---------------------------------------------------------------------------
    // WakeWordService control
    // ---------------------------------------------------------------------------

    fun startWakeWordService() {
        sendWakeWordServiceAction(WakeWordService.ACTION_START)
    }

    private fun stopWakeWordService() {
        sendWakeWordServiceAction(WakeWordService.ACTION_STOP)
    }

    /**
     * Pauses wake word detection so the voice pipeline can open the microphone.
     * WakeWordEngine releases AudioRecord on PAUSE, freeing the mic for AudioRecorder.
     * Must be called BEFORE AudioRecorder.recordUntilSilence().
     */
    private fun pauseWakeWordService() {
        sendWakeWordServiceAction(WakeWordService.ACTION_PAUSE)
    }

    /**
     * Resumes wake word detection after the voice pipeline has finished and
     * AudioRecorder has released the microphone.
     * Must be called AFTER AudioRecorder and TTS are both done.
     */
    private fun resumeWakeWordService() {
        sendWakeWordServiceAction(WakeWordService.ACTION_RESUME)
    }

    private fun sendWakeWordServiceAction(action: String) {
        val intent = Intent(context, WakeWordService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            action == WakeWordService.ACTION_START
        ) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Log.d(TAG, "sendWakeWordServiceAction: $action")
    }

    // ---------------------------------------------------------------------------
    // BroadcastReceiver — called from HomeScreen DisposableEffect
    // ---------------------------------------------------------------------------

    fun registerWakeWordReceiver() {
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(wakeWordReceiver, filter)
        }
        Log.d(TAG, "registerWakeWordReceiver: registered")
    }

    fun unregisterWakeWordReceiver() {
        runCatching { context.unregisterReceiver(wakeWordReceiver) }
        Log.d(TAG, "unregisterWakeWordReceiver: unregistered")
    }

    // ---------------------------------------------------------------------------
    // Mic toggle (manual button)
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
    // Voice pipeline: PAUSE wake word → record → STT → LLM → TTS → RESUME wake word
    // ---------------------------------------------------------------------------

    private fun startVoicePipeline() {
        pipelineJob = viewModelScope.launch {

            // Step 1 — Pause WakeWordService BEFORE opening mic.
            // WakeWordEngine's AudioRecord must be released before AudioRecorder can open.
            // Android does not allow two concurrent AudioRecord instances.
            // delay(150ms) gives the service time to process the PAUSE Intent and release
            // AudioRecord before we attempt to open our own — Intent dispatch is async.
            pauseWakeWordService()
            delay(Constants.WAKE_WORD_PAUSE_DELAY_MS)

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
            val audioBytes = runCatching {
                audioRecorder.recordUntilSilence(
                    onAmplitude = { amp -> _uiState.update { it.copy(amplitude = amp) } }
                )
            }.getOrElse { e ->
                handleError("Kayıt hatası", e)
                resumeWakeWordService() // Always resume on error
                return@launch
            }

            // Step 4 — THINKING (STT)
            _uiState.update {
                it.copy(
                    amplitude  = 0f,
                    coreState  = IrisCoreState.THINKING,
                    statusText = "Anlıyorum..."
                )
            }

            // Step 5 — STT via Groq Whisper
            val transcript = runCatching {
                transcribeAudioUseCase(audioBytes)
            }.getOrElse { e ->
                handleError("Ses anlaşılamadı", e)
                resumeWakeWordService()
                return@launch
            }

            // Step 6 — Persist user message
            val userMsg   = ChatMessage(role = ChatMessage.Role.USER, content = transcript)
            val userMsgId = conversationRepository.saveMessage(userMsg)
            history.add(userMsg.copy(id = userMsgId))

            // Step 7 — THINKING (LLM)
            _uiState.update { it.copy(statusText = "Düşünüyorum...") }

            // Step 8 — LLM via Gemini (Groq fallback handled inside use case)
            val reply = runCatching {
                sendMessageUseCase(history.toList())
            }.getOrElse { e ->
                history.removeLastOrNull()
                handleError("Yanıt alınamadı", e)
                resumeWakeWordService()
                return@launch
            }

            // Step 9 — Persist assistant message
            val assistantMsg   = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = reply)
            val assistantMsgId = conversationRepository.saveMessage(assistantMsg)
            history.add(assistantMsg.copy(id = assistantMsgId))

            // Step 10 — SPEAKING (TTS)
            _uiState.update {
                it.copy(coreState = IrisCoreState.SPEAKING, statusText = "Konuşuyorum...")
            }

            ttsProvider.speak(
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
                    resumeWakeWordService()
                }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Stop / interrupt
    // ---------------------------------------------------------------------------

    fun onStop() {
        pipelineJob?.cancel()
        pipelineJob = null
        ttsProvider.stop()
        val muted = _uiState.value.isMuted
        _uiState.update {
            it.copy(
                coreState   = IrisCoreState.IDLE,
                amplitude   = 0f,
                ttsProgress = 0f,
                statusText  = if (muted) "Sessize alındı" else "Dinlemeye hazır"
            )
        }
        resumeWakeWordService()
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------

    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
        // TODO Phase 3: Start/stop AccessibilityService
    }

    // ---------------------------------------------------------------------------
    // Error handling
    // ---------------------------------------------------------------------------

    private fun handleError(ctx: String, e: Throwable) {
        val msg = when (e) {
            is IrisException.NetworkException   -> "$ctx: İnternet bağlantısı yok"
            is IrisException.RateLimitException -> "$ctx: API limiti aşıldı"
            is IrisException.AuthException      -> "$ctx: API anahtarı eksik"
            is IrisException.SttException       -> "$ctx: ${e.message}"
            is IrisException.LlmException       -> "$ctx: ${e.message}"
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
        ttsProvider.release()
        // BroadcastReceiver unregistered by HomeScreen's DisposableEffect — not here
    }
}