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

    /** Tool requires a permission that has not been granted */
    class PermissionRequiredException(
        val permission: String,
        val rationale : String,
        val toolName  : String,
        cause: Throwable? = null
    ) : IrisException("Permission required: $permission for tool $toolName", cause)
}