package com.iris.assistant.data.remote.router

import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.data.remote.gemini.GeminiRepository
import com.iris.assistant.data.remote.groq.GroqLlmRepository
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.repository.LlmRepository
import com.iris.assistant.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderRouter @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val groqLlmRepository: GroqLlmRepository,
    private val preferencesRepository: PreferencesRepository
) : LlmRepository {

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String {
        val provider = preferencesRepository.preferences
            .map { it.llmProvider }
            .first()

        return if (provider == Constants.LLM_PROVIDER_GROQ) {
            groqLlmRepository.chat(history, systemPrompt)
        } else {
            geminiRepository.chat(history, systemPrompt)
        }
    }
}
