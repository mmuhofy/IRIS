package com.iris.assistant.domain.repository

import com.iris.assistant.domain.model.ChatMessage

/**
 * Domain interface for LLM chat.
 * Implementation: data/remote/gemini/GeminiRepository (primary)
 *                 data/remote/groq/GroqLlmRepository (fallback)
 */
interface LlmRepository {
    /**
     * Send conversation history + new user message, receive assistant reply.
     * @param history  All previous turns (oldest first)
     * @param systemPrompt  IRIS personality/instructions
     * @return Assistant reply text
     * @throws IrisException.LlmException on failure
     * @throws IrisException.RateLimitException on 429
     */
    suspend fun chat(
        history      : List<ChatMessage>,
        systemPrompt : String
    ): String
}