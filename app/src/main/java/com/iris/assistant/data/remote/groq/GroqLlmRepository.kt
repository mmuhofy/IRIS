package com.iris.assistant.data.remote.groq

import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.LlmRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groq LLM fallback repository.
 * TODO: Implement full Groq chat completions API (Phase 1 — after Gemini verified working).
 * Confirm model name via https://console.groq.com/docs/models before implementing.
 */
@Singleton
class GroqLlmRepository @Inject constructor() : LlmRepository {

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String {
        // Stub — throws to surface the fallback gap clearly during development
        throw IrisException.LlmException(
            "GroqLlmRepository not yet implemented — primary Gemini must be working"
        )
    }
}