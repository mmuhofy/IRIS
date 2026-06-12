package com.iris.assistant.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iris.assistant.domain.model.ChatMessage

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id          : Long   = 0,

    @ColumnInfo(name = "role")
    val role        : String, // "USER" | "ASSISTANT"

    @ColumnInfo(name = "content")
    val content     : String,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs : Long = System.currentTimeMillis()
) {
    fun toDomain() = ChatMessage(
        id          = id,
        role        = ChatMessage.Role.valueOf(role),
        content     = content,
        timestampMs = timestampMs
    )

    companion object {
        fun fromDomain(msg: ChatMessage) = MessageEntity(
            id          = msg.id,
            role        = msg.role.name,
            content     = msg.content,
            timestampMs = msg.timestampMs
        )
    }
}