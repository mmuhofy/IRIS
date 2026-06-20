package com.iris.assistant.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iris.assistant.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Insert a single message, returns new row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    // -------------------------------------------------------------------------
    // Conversation-scoped queries (primary — used by ChatViewModel)
    // -------------------------------------------------------------------------

    /** Reactive stream of messages for a single conversation, oldest → newest. */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp_ms ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    /** One-shot list for a conversation — used for LLM context construction. */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp_ms ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    /** Most recent N messages in a conversation — capped LLM context window. */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun getRecentByConversation(conversationId: Long, limit: Int): List<MessageEntity>

    /** First user message in a conversation — used to auto-generate title. */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND role = 'USER' ORDER BY timestamp_ms ASC LIMIT 1")
    suspend fun getFirstUserMessage(conversationId: Long): MessageEntity?

    // -------------------------------------------------------------------------
    // Global queries (legacy — kept for AssistantViewModel compatibility)
    // -------------------------------------------------------------------------

    /** All messages ordered oldest → newest. */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    /** All messages as a one-shot list. */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms ASC")
    suspend fun getAll(): List<MessageEntity>

    /** Most recent N messages globally. */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MessageEntity>

    /** Delete all messages in a conversation (manual — CASCADE handles FK deletes). */
    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun clearByConversation(conversationId: Long)

    /** Delete all messages globally — called from Settings "Geçmişi Temizle". */
    @Query("DELETE FROM messages")
    suspend fun clearAll()
}