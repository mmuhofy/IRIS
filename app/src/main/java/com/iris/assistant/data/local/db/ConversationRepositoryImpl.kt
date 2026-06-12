package com.iris.assistant.data.local.db

import com.iris.assistant.data.local.db.entity.MessageEntity
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val db: IrisDatabase
) : ConversationRepository {

    override fun observeMessages(): Flow<List<ChatMessage>> =
        db.messageDao().observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun saveMessage(message: ChatMessage): Long =
        db.messageDao().insert(MessageEntity.fromDomain(message))

    override suspend fun getHistory(): List<ChatMessage> =
        db.messageDao().getAll().map { it.toDomain() }

    override suspend fun getRecentHistory(limit: Int): List<ChatMessage> =
        db.messageDao().getRecent(limit).reversed().map { it.toDomain() }

    override suspend fun clearAll() =
        db.messageDao().clearAll()
}