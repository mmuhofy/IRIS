package com.iris.assistant.ui.home

/**
 * Represents the visual state of the Iris Core animation ring.
 * Driven by the voice pipeline state in the ViewModel.
 */
enum class IrisCoreState {
    /** No active interaction — slow pulse at ~40% opacity */
    IDLE,

    /** Wake word detected / mic active — ring reacts to audio amplitude */
    LISTENING,

    /** Waiting for LLM response — ring rotates */
    THINKING,

    /** TTS playback active — ring wave-syncs with audio output */
    SPEAKING
}