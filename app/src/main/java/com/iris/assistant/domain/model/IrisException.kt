package com.iris.assistant.domain.model

/**
 * Domain-level exceptions for IRIS pipeline failures.
 * All repositories throw these — never raw IOException or HttpException.
 */
sealed class IrisException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** STT transcription failed */
    class SttException(message: String, cause: Throwable? = null) : IrisException(message, cause)

    /** LLM request failed */
    class LlmException(message: String, cause: Throwable? = null) : IrisException(message, cause)

    /** TTS synthesis failed */
    class TtsException(message: String, cause: Throwable? = null) : IrisException(message, cause)

    /** No internet connection */
    class NetworkException(message: String, cause: Throwable? = null) : IrisException(message, cause)

    /** API rate limit exceeded */
    class RateLimitException(message: String, cause: Throwable? = null) : IrisException(message, cause)

    /** API key missing or invalid */
    class AuthException(message: String, cause: Throwable? = null) : IrisException(message, cause)
}