package com.iris.assistant.data.local.db

import com.iris.assistant.data.local.db.entity.ConversationEntity
import com.iris.assistant.data.local.db.entity.MessageEntity
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.Conversation
import com.iris.assistant.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val db: IrisDatabase,
) : ConversationRepository {

    // -------------------------------------------------------------------------
    // Conversation CRUD
    // -------------------------------------------------------------------------

    override fun observeConversations(): Flow<List<Conversation>> =
        db.conversationDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(title: String): Long =
        db.conversationDao().insert(
            ConversationEntity(title = title)
        )

    override suspend fun updateTitle(conversationId: Long, title: String) {
        val entity = db.conversationDao().getById(conversationId) ?: return
        db.conversationDao().update(entity.copy(title = title))
    }

    override suspend fun deleteConversation(conversationId: Long) =
        db.conversationDao().deleteById(conversationId)

    override suspend fun clearAllConversations() {
        db.conversationDao().clearAll()
        // Messages cascade via FK — no need to delete manually.
    }

    // -------------------------------------------------------------------------
    // Message operations (conversation-scoped)
    // -------------------------------------------------------------------------

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        db.messageDao().observeByConversation(conversationId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun saveMessage(message: ChatMessage): Long {
        var effectiveId = message.conversationId
        if (effectiveId == 0L || db.conversationDao().getById(effectiveId) == null) {
            effectiveId = db.conversationDao().insert(
                ConversationEntity(title = "Yeni Sohbet")
            )
        }
        val msg = if (effectiveId != message.conversationId) message.copy(conversationId = effectiveId) else message
        val rowId = db.messageDao().insert(MessageEntity.fromDomain(msg))
        db.conversationDao().getById(effectiveId)?.let { conv ->
            db.conversationDao().update(
                conv.copy(updatedAtMs = System.currentTimeMillis())
            )
        }
        return rowId
    }

    override suspend fun getHistory(conversationId: Long): List<ChatMessage> =
        db.messageDao().getByConversation(conversationId).map { it.toDomain() }

    override suspend fun getRecentHistory(conversationId: Long, limit: Int): List<ChatMessage> =
        db.messageDao().getRecentByConversation(conversationId, limit)
            .reversed()
            .map { it.toDomain() }

    override suspend fun generateTitleIfNeeded(conversationId: Long) {
        val conv = db.conversationDao().getById(conversationId) ?: return
        if (conv.title != "Yeni Sohbet") return // already titled

        val firstMsg = db.messageDao().getFirstUserMessage(conversationId) ?: return
        val title = firstMsg.content.take(40).let {
            if (firstMsg.content.length > 40) "$it…" else it
        }
        db.conversationDao().update(
            conv.copy(title = title, updatedAtMs = System.currentTimeMillis())
        )
    }

    // -------------------------------------------------------------------------
    // Legacy — AssistantViewModel / AssistantActivity compatibility
    // -------------------------------------------------------------------------

    override fun observeMessages(): Flow<List<ChatMessage>> =
        db.messageDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveMessage(message: ChatMessage, conversationId: Long): Long =
        saveMessage(message.copy(conversationId = conversationId))

    override suspend fun getRecentHistory(limit: Int): List<ChatMessage> =
        db.messageDao().getRecent(limit).reversed().map { it.toDomain() }

    override suspend fun clearAll() =
        db.messageDao().clearAll()
}