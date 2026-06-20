package com.iris.assistant.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iris.assistant.domain.model.ChatMessage

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity        = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns  = ["conversation_id"],
            // Deleting a conversation deletes all its messages automatically.
            onDelete      = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversation_id")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** FK → conversations.id */
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "role")
    val role: String, // "USER" | "ASSISTANT"

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis(),
) {
    fun toDomain() = ChatMessage(
        id              = id,
        conversationId  = conversationId,
        role            = ChatMessage.Role.valueOf(role),
        content         = content,
        timestampMs     = timestampMs,
    )

    companion object {
        fun fromDomain(msg: ChatMessage) = MessageEntity(
            id             = msg.id,
            conversationId = msg.conversationId,
            role           = msg.role.name,
            content        = msg.content,
            timestampMs    = msg.timestampMs,
        )
    }
}