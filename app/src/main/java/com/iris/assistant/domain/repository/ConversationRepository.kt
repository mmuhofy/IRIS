package com.iris.assistant.domain.repository

import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {

    // -------------------------------------------------------------------------
    // Conversation CRUD
    // -------------------------------------------------------------------------

    /** Reactive list of all conversations, newest first — drives drawer. */
    fun observeConversations(): Flow<List<Conversation>>

    /** Create a new empty conversation, returns its generated id. */
    suspend fun createConversation(title: String = "Yeni Sohbet"): Long

    /** Update the title of an existing conversation. */
    suspend fun updateTitle(conversationId: Long, title: String)

    /** Delete a conversation and all its messages (CASCADE). */
    suspend fun deleteConversation(conversationId: Long)

    /** Delete all conversations and messages. */
    suspend fun clearAllConversations()

    // -------------------------------------------------------------------------
    // Message operations (conversation-scoped)
    // -------------------------------------------------------------------------

    /** Reactive stream of messages for a conversation, oldest → newest. */
    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>

    /** Save a message into a conversation. Also bumps conversation.updatedAtMs. */
    suspend fun saveMessage(message: ChatMessage): Long

    /** Full message history for a conversation — for LLM context construction. */
    suspend fun getHistory(conversationId: Long): List<ChatMessage>

    /** Most recent N messages — for capped LLM context. */
    suspend fun getRecentHistory(conversationId: Long, limit: Int): List<ChatMessage>

    /**
     * Auto-generate a title from the first user message and persist it.
     * Truncates to 40 chars. No-op if title is already set (not "Yeni Sohbet").
     */
    suspend fun generateTitleIfNeeded(conversationId: Long)

    // -------------------------------------------------------------------------
    // Legacy — kept for AssistantViewModel / AssistantActivity compatibility
    // -------------------------------------------------------------------------

    /** Reactive stream of ALL messages across all conversations (oldest first). */
    fun observeMessages(): Flow<List<ChatMessage>>

    /** Save a message without a conversation context (legacy path). */
    suspend fun saveMessage(message: ChatMessage, conversationId: Long): Long

    /** Most recent N messages globally. */
    suspend fun getRecentHistory(limit: Int): List<ChatMessage>

    /** Clear all messages globally. */
    suspend fun clearAll()
}