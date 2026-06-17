package com.iris.assistant.data.remote.groq

import android.util.Log
import com.iris.assistant.BuildConfig
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.LlmRepository
import com.iris.assistant.domain.tools.ToolRegistry
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqLlmRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val toolRegistry: ToolRegistry,
    private val preferencesRepository: PreferencesRepository
) : LlmRepository {

    companion object {
        private const val TAG = "GroqLlmRepository"
        private const val MAX_TOOL_ROUNDS = 10
    }

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.GROQ_LLM_API_KEY
        if (apiKey.isBlank()) throw IrisException.AuthException("GROQ_LLM_API_KEY is not set")

        val modelName = preferencesRepository.preferences
            .map { it.llmModel }
            .first()
            .takeIf { it.isNotBlank() }
            ?: Constants.GROQ_LLM_MODEL

        val toolsPayload = toolRegistry.openAiToolsPayload()

        // Build messages array — start with system + conversation history
        val messages = JSONArray()

        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemPrompt)
        )

        history.forEach { msg ->
            val role = when (msg.role) {
                ChatMessage.Role.USER      -> "user"
                ChatMessage.Role.ASSISTANT -> "assistant"
            }
            messages.put(
                JSONObject()
                    .put("role", role)
                    .put("content", msg.content)
            )
        }

        // Multi-turn tool calling loop
        for (round in 0 until MAX_TOOL_ROUNDS) {

            val requestBody = JSONObject()
                .put("model", modelName)
                .put("messages", messages)
                .put("temperature", 0.7)
                .put("max_tokens", 1024)

            if (toolsPayload != null) {
                requestBody.put("tools", toolsPayload)
                requestBody.put("tool_choice", "auto")
            }

            val jsonRequest = requestBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(Constants.GROQ_LLM_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonRequest)
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw IrisException.NetworkException("Groq LLM network error: ${e.message}", e)
            }

            val bodyString = response.body?.string()
                ?: throw IrisException.LlmException("Groq LLM response body is null (HTTP ${response.code})")

            when (response.code) {
                200 -> {
                    val json = JSONObject(bodyString)
                    val choices = json.optJSONArray("choices")

                    if (choices == null || choices.length() == 0) {
                        throw IrisException.LlmException("Groq LLM: no choices in response")
                    }

                    val choice = choices.getJSONObject(0)
                    val finishReason = choice.optString("finish_reason", "")
                    val message = choice.getJSONObject("message")

                    val toolCalls = message.optJSONArray("tool_calls")

                    // No tool calls → return text content
                    if (toolCalls == null || toolCalls.length() == 0 || finishReason == "stop") {
                        val content = message.optString("content", "").trim()
                        if (content.isNotEmpty()) {
                            return@withContext content
                        }
                        throw IrisException.LlmException("Groq LLM: empty response content")
                    }

                    Log.d(TAG, "round=$round: ${toolCalls.length()} tool_calls received")

                    // Add the assistant's message (with tool_calls) to history
                    messages.put(message)

                    // Execute each tool and append results
                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.getJSONObject(i)
                        val id = tc.optString("id", "call_$i")
                        val fn = tc.getJSONObject("function")
                        val name = fn.optString("name", "")
                        val args = try {
                            JSONObject(fn.optString("arguments", "{}"))
                        } catch (_: Exception) {
                            JSONObject()
                        }

                        Log.d(TAG, "round=$round: executing tool '$name'")
                        val toolResult = toolRegistry.execute(name, args)

                        // Handle permission / cancellation like GeminiRepository
                        if (toolResult is ToolResult.PermissionRequired) {
                            throw IrisException.PermissionRequiredException(
                                permission = toolResult.permission,
                                rationale  = toolResult.rationale,
                                toolName   = name
                            )
                        }
                        if (toolResult is ToolResult.Cancelled) {
                            return@withContext "İşlem iptal edildi."
                        }

                        val resultContent = when (toolResult) {
                            is ToolResult.Success -> toolResult.displayText
                            is ToolResult.Error   -> "Error: ${toolResult.message}"
                            else                  -> "Unknown result"
                        }

                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", id)
                                .put("content", resultContent)
                        )
                    }

                    // Continue loop — model will generate final text from tool results
                }
                401 -> throw IrisException.AuthException("Groq LLM auth failed — check GROQ_LLM_API_KEY")
                429 -> throw IrisException.RateLimitException("Groq LLM rate limit exceeded")
                else -> throw IrisException.LlmException("Groq LLM failed HTTP ${response.code}: $bodyString")
            }
        }

        throw IrisException.LlmException("Groq LLM exceeded max tool call rounds ($MAX_TOOL_ROUNDS)")
    }
}
