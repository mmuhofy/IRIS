package com.iris.assistant.domain.usecase

import com.iris.assistant.domain.repository.SttRepository
import javax.inject.Inject

/**
 * Transcribes raw audio bytes to text via the STT repository.
 * Caller is responsible for providing valid WAV audio (16kHz mono recommended).
 */
class TranscribeAudioUseCase @Inject constructor(
    private val sttRepository: SttRepository
) {
    /**
     * @param audioBytes WAV audio bytes recorded from the mic
     * @return Transcribed text
     * @throws IrisException.SttException on transcription failure
     * @throws IrisException.NetworkException on connectivity failure
     * @throws IrisException.RateLimitException on API rate limit
     */
    suspend operator fun invoke(audioBytes: ByteArray): String =
        sttRepository.transcribe(audioBytes)
}