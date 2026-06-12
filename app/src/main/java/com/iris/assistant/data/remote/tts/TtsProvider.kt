package com.iris.assistant.data.remote.tts

/**
 * Common interface for all TTS providers.
 * Implementations: AndroidTtsClient (MVP), EdgeTtsClient (Phase 2)
 */
interface TtsProvider {
    /**
     * Synthesize text to audio and play it immediately.
     * @param text Text to speak
     * @param onProgress 0..1 progress callback for Iris Core SPEAKING animation
     * @param onDone Called when playback completes or is stopped
     */
    suspend fun speak(
        text      : String,
        onProgress: (Float) -> Unit = {},
        onDone    : () -> Unit      = {}
    )

    /** Stop current playback immediately */
    fun stop()

    /** Release underlying resources — call from ViewModel.onCleared() */
    fun release()
}