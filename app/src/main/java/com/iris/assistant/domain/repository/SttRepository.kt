package com.iris.assistant.domain.repository

/**
 * Domain interface for Speech-to-Text.
 * Implementation lives in data/remote/groq/WhisperRepository.
 */
interface SttRepository {
    /**
     * Transcribe raw PCM/WAV audio bytes to text.
     * @param audioBytes  Raw audio data (WAV format, 16kHz mono recommended)
     * @return Transcribed text, or throws SttException on failure
     */
    suspend fun transcribe(audioBytes: ByteArray): String
}