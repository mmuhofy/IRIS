package com.iris.assistant.domain.model

/**
 * A single turn in a conversation.
 * conversationId links this message to its parent Conversation.
 */
data class ChatMessage(
    val id             : Long   = 0,
    val conversationId : Long   = 0, // 0 = legacy / unassigned (pre-migration messages)
    val role           : Role,
    val content        : String,
    val timestampMs    : Long   = System.currentTimeMillis(),
) {
    enum class Role { USER, ASSISTANT }
}