package com.iris.assistant.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iris.assistant.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Insert a single message, returns new row id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    /** All messages ordered oldest → newest, as a reactive Flow */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    /** All messages as a one-shot list (for LLM history construction) */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms ASC")
    suspend fun getAll(): List<MessageEntity>

    /** Delete all messages — called from Settings "Geçmişi Temizle" */
    @Query("DELETE FROM messages")
    suspend fun clearAll()

    /** Most recent N messages — useful for capping LLM context window */
    @Query("SELECT * FROM messages ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MessageEntity>
}