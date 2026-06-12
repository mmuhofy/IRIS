package com.iris.assistant.domain.repository

import com.iris.assistant.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    /** Reactive stream of all messages (oldest first) — for Chat UI */
    fun observeMessages(): Flow<List<ChatMessage>>

    /** Save a single message */
    suspend fun saveMessage(message: ChatMessage): Long

    /** Full history as list — for LLM context construction */
    suspend fun getHistory(): List<ChatMessage>

    /** Most recent N messages — for capped LLM context */
    suspend fun getRecentHistory(limit: Int): List<ChatMessage>

    /** Clear all messages */
    suspend fun clearAll()
}