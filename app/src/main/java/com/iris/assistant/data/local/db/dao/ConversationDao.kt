package com.iris.assistant.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.iris.assistant.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    /** Insert new conversation, returns generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    /** Update title or updatedAtMs on an existing conversation. */
    @Update
    suspend fun update(conversation: ConversationEntity)

    /** All conversations newest-first — drives drawer list. */
    @Query("SELECT * FROM conversations ORDER BY updated_at_ms DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    /** One-shot fetch for a single conversation (e.g. to update title). */
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?

    /** Delete a conversation — cascades to its messages via trigger (see migration). */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Delete all conversations. */
    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}