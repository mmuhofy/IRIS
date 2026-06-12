package com.iris.assistant.di

import com.iris.assistant.data.local.db.ConversationRepositoryImpl
import com.iris.assistant.data.remote.gemini.GeminiRepository
import com.iris.assistant.data.remote.groq.GroqLlmRepository
import com.iris.assistant.data.remote.groq.WhisperRepository
import com.iris.assistant.domain.repository.ConversationRepository
import com.iris.assistant.domain.repository.LlmRepository
import com.iris.assistant.domain.repository.SttRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindSttRepository(impl: WhisperRepository): SttRepository

    @Binds
    @Singleton
    @PrimaryLlm
    abstract fun bindPrimaryLlm(impl: GeminiRepository): LlmRepository

    @Binds
    @Singleton
    @FallbackLlm
    abstract fun bindFallbackLlm(impl: GroqLlmRepository): LlmRepository
}