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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    val isMicOn     : Boolean       = false,
    val isScreenCtrl: Boolean       = false,
    val statusText  : String        = "Dinlemeye hazır",
    val errorMessage: String?       = null
)

// ---------------------------------------------------------------------------
// HomeViewModel
// ---------------------------------------------------------------------------
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context            : Context,
    private val audioRecorder                         : AudioRecorder,
    private val transcribeAudioUseCase                : TranscribeAudioUseCase,
    private val sendMessageUseCase                    : SendMessageUseCase,
    private val conversationRepository                : ConversationRepository,
    private val ttsProvider                           : TtsProvider,
    private val preferencesRepository                 : PreferencesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // In-memory history kept in sync with Room for fast LLM context access
    private val history = mutableListOf<ChatMessage>()

    // Active pipeline job — cancelled on stop()
    private var pipelineJob: Job? = null

    // ---------------------------------------------------------------------------
    // Wake word BroadcastReceiver
    // Registered/unregistered with the app lifecycle (see HomeScreen DisposableEffect).
    // Not registered here in init{} to avoid leaking a Context-bound receiver
    // beyond the ViewModel's scope without a paired unregister call.
    // ---------------------------------------------------------------------------
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                Log.d(TAG, "BroadcastReceiver: wake word detected — starting pipeline")
                // Only start if not already active
                if (_uiState.value.coreState == IrisCoreState.IDLE) {
                    startVoicePipeline()
                }
            }
        }
    }

    init {
        // Load conversation history
        viewModelScope.launch {
            history.addAll(conversationRepository.getHistory())
        }

        // Observe backgroundListening preference — start/stop WakeWordService accordingly
        viewModelScope.launch {
            preferencesRepository.preferences
                .map { it.backgroundListening }
                .distinctUntilChanged()
                .collect { enabled ->
                    Log.d(TAG, "backgroundListening changed: $enabled")
                    if (enabled) startWakeWordService() else stopWakeWordService()
                }
        }
    }

    // ---------------------------------------------------------------------------
    // WakeWordService start / stop
    // ---------------------------------------------------------------------------

    /**
     * Starts WakeWordService as a foreground service.
     * Safe to call multiple times — service is idempotent (ACTION_START guarded inside).
     */
    fun startWakeWordService() {
        val intent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_START
        }
        // startForegroundService required on Android 8+ for foreground services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Log.d(TAG, "startWakeWordService: intent sent")
    }

    /**
     * Stops WakeWordService.
     * Called when backgroundListening is disabled in Settings.
     */
    private fun stopWakeWordService() {
        val intent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_STOP
        }
        context.startService(intent)
        Log.d(TAG, "stopWakeWordService: stop intent sent")
    }

    // ---------------------------------------------------------------------------
    // BroadcastReceiver registration — called from HomeScreen DisposableEffect
    // ---------------------------------------------------------------------------

    /**
     * Registers the wake word BroadcastReceiver.
     * Must be called from HomeScreen's DisposableEffect (onResume equivalent).
     * Paired with [unregisterWakeWordReceiver].
     */
    fun registerWakeWordReceiver() {
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        // RECEIVER_NOT_EXPORTED: local broadcast, package-scoped — no external apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(wakeWordReceiver, filter)
        }
        Log.d(TAG, "registerWakeWordReceiver: registered")
    }

    /**
     * Unregisters the wake word BroadcastReceiver.
     * Must be called from HomeScreen's DisposableEffect onDispose.
     */
    fun unregisterWakeWordReceiver() {
        runCatching { context.unregisterReceiver(wakeWordReceiver) }
        Log.d(TAG, "unregisterWakeWordReceiver: unregistered")
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
                    isMicOn   = false,
                    amplitude = 0f,
                    coreState = IrisCoreState.THINKING,
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
                it.copy(coreState = IrisCoreState.SPEAKING, statusText = "Konuşuyorum...")
            }

            ttsProvider.speak(
                text       = reply,
                onProgress = { progress -> onTtsProgressUpdate(progress) },
                onDone     = {
                    _uiState.update {
                        it.copy(
                            coreState   = IrisCoreState.IDLE,
                            ttsProgress = 0f,
                            statusText  = "Dinlemeye hazır"
                        )
                    }
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
        _uiState.update {
            it.copy(
                coreState   = IrisCoreState.IDLE,
                isMicOn     = false,
                amplitude   = 0f,
                ttsProgress = 0f,
                statusText  = "Dinlemeye hazır"
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------
    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
        // TODO: Start/stop AccessibilityService (Phase 3)
    }

    // ---------------------------------------------------------------------------
    // TTS progress
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
        ttsProvider.release()
        // Receiver is unregistered by HomeScreen's DisposableEffect — not here
    }
}