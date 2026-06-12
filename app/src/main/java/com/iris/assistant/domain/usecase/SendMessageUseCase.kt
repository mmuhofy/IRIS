package com.iris.assistant.domain.usecase

import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.model.SystemPrompt
import com.iris.assistant.domain.repository.LlmRepository
import javax.inject.Inject

/**
 * Sends user message + history to the LLM, returns assistant reply.
 * Falls back to Groq if Gemini throws RateLimitException.
 *
 * @param primaryLlm   GeminiRepository
 * @param fallbackLlm  GroqLlmRepository (Phase 1 — stub until implemented)
 */
class SendMessageUseCase @Inject constructor(
    @PrimaryLlm  private val primaryLlm : LlmRepository,
    @FallbackLlm private val fallbackLlm: LlmRepository
) {
    /**
     * @param history     Full conversation so far (oldest first, must include latest user message)
     * @return            Assistant reply text
     * @throws IrisException on unrecoverable failure
     */
    suspend operator fun invoke(history: List<ChatMessage>): String {
        return try {
            primaryLlm.chat(history, SystemPrompt.v1)
        } catch (e: IrisException.RateLimitException) {
            // Gemini rate-limited — try Groq fallback
            fallbackLlm.chat(history, SystemPrompt.v1)
        }
    }
}