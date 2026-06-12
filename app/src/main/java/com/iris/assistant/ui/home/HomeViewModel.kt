package com.iris.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------
data class HomeUiState(
    val coreState   : IrisCoreState = IrisCoreState.IDLE,
    val amplitude   : Float         = 0f,   // 0..1 — mic amplitude (LISTENING)
    val ttsProgress : Float         = 0f,   // 0..1 — TTS playback progress (SPEAKING)
    val isMicOn     : Boolean       = false,
    val isScreenCtrl: Boolean       = false,
    val statusText  : String        = "Dinlemeye hazır"
)

// ---------------------------------------------------------------------------
// HomeViewModel
// ---------------------------------------------------------------------------
@HiltViewModel
class HomeViewModel @Inject constructor(
    // TODO: Inject SendMessageUseCase, WakeWordRepository, TtsRepository (Phase 1 pipeline)
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------------------
    // Mic toggle — triggered by bottom bar button or wake word detection
    // ---------------------------------------------------------------------------
    fun onMicToggle() {
        _uiState.update { current ->
            if (current.isMicOn) {
                // Stop listening
                current.copy(
                    isMicOn    = false,
                    coreState  = IrisCoreState.IDLE,
                    amplitude  = 0f,
                    statusText = "Dinlemeye hazır"
                )
            } else {
                // Start listening
                current.copy(
                    isMicOn    = true,
                    coreState  = IrisCoreState.LISTENING,
                    statusText = "Dinliyorum..."
                )
                // TODO: launch audio recording + VAD coroutine (Phase 1)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Screen control toggle
    // ---------------------------------------------------------------------------
    fun onScreenControlToggle() {
        _uiState.update { it.copy(isScreenCtrl = !it.isScreenCtrl) }
        // TODO: Start/stop AccessibilityService screen reading (Phase 3)
    }

    // ---------------------------------------------------------------------------
    // Stop / interrupt — wires to Job.cancel() + TTS.stop() (Phase 1)
    // ---------------------------------------------------------------------------
    fun onStop() {
        _uiState.update {
            it.copy(
                coreState   = IrisCoreState.IDLE,
                isMicOn     = false,
                amplitude   = 0f,
                ttsProgress = 0f,
                statusText  = "Dinlemeye hazır"
            )
        }
        // TODO: orchestrator.stop() — cancel active AI/tool coroutine + TTS (Phase 1)
    }

    // ---------------------------------------------------------------------------
    // Called by audio pipeline to update mic amplitude during LISTENING
    // ---------------------------------------------------------------------------
    fun onAmplitudeUpdate(amplitude: Float) {
        _uiState.update { it.copy(amplitude = amplitude.coerceIn(0f, 1f)) }
    }

    // ---------------------------------------------------------------------------
    // Called by TTS player to sync SPEAKING wave animation
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
    // Transition to THINKING state while waiting for LLM response
    // ---------------------------------------------------------------------------
    fun onThinking() {
        _uiState.update {
            it.copy(
                coreState  = IrisCoreState.THINKING,
                amplitude  = 0f,
                statusText = "Düşünüyorum..."
            )
        }
    }
}