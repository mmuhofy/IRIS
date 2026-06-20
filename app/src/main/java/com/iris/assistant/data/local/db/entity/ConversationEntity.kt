package com.iris.assistant.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iris.assistant.domain.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Auto-generated from first user message (first ~40 chars). */
    @ColumnInfo(name = "title")
    val title: String = "Yeni Sohbet",

    /** Unix epoch ms — used for ordering in drawer list. */
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    /** Updated every time a new message is added — drives "recent first" sort. */
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun toDomain() = Conversation(
        id          = id,
        title       = title,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        fun fromDomain(c: Conversation) = ConversationEntity(
            id          = c.id,
            title       = c.title,
            createdAtMs = c.createdAtMs,
            updatedAtMs = c.updatedAtMs,
        )
    }
}