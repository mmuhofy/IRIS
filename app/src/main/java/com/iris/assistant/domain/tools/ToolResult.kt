package com.iris.assistant.domain.tools

/**
 * Result type for all JarvisTool executions.
 *
 * Every tool returns one of these — never throws directly.
 * Callers (ToolRegistry, SendMessageUseCase) handle each branch explicitly.
 */
sealed class ToolResult {

    /**
     * Tool executed successfully.
     * [displayText] is shown to the user (via TTS / chat bubble).
     * [data] is optional structured data passed back to Gemini as the function result.
     */
    data class Success(
        val displayText: String,
        val data       : Map<String, Any> = emptyMap()
    ) : ToolResult()

    /**
     * Tool failed during execution (runtime error, API failure, etc.).
     * [message] is a user-facing error description.
     */
    data class Error(
        val message: String,
        val cause  : Throwable? = null
    ) : ToolResult()

    /**
     * Tool requires a permission that has not been granted yet.
     * [permission] is the Android permission string (e.g. Manifest.permission.CALL_PHONE).
     * [rationale] is shown to the user before the system permission dialog.
     * The caller is responsible for requesting the permission and retrying.
     */
    data class PermissionRequired(
        val permission: String,
        val rationale : String
    ) : ToolResult()

    /**
     * Tool was cancelled — either by user action (stop button, "Dur IRIS")
     * or by ActionPreviewOverlay countdown expiry with cancel pressed.
     */
    data object Cancelled : ToolResult()
}