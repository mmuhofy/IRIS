package com.iris.assistant.domain.model

/**
 * A single turn in the conversation.
 * Used in both domain logic and as the Room entity (via mapping).
 */
data class ChatMessage(
    val id        : Long   = 0,
    val role      : Role,
    val content   : String,
    val timestampMs: Long  = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}