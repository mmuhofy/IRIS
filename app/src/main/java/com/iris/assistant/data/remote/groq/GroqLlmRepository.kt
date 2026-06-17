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
        private val TOOL_CALL_PATTERN = Regex(
            """(\{[^}]*"tool"\s*:\s*"[^"]*"[^}]*\})""",
            RegexOption.DOT_MATCHES_ALL
        )
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

        val toolDescriptions = buildToolDescriptions()
        val fullSystemPrompt = if (toolDescriptions != null) {
            """
$systemPrompt

AVAILABLE FUNCTIONS:
You have access to the following functions. When the user asks you to do something that matches one of these functions, respond with a JSON object exactly in this format (and nothing else):

{"tool": "function_name", "args": {"key": "value"}}

Available functions:
$toolDescriptions

If no function is needed, respond normally. Never say you "cannot" do something when a function is available.

RULES:
- When calling a function, output ONLY the JSON object, no other text.
- After the function result is returned, explain the result to the user naturally.
- Always respond in Turkish.
            """.trimIndent()
        } else {
            systemPrompt
        }

        val messages = JSONArray()

        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", fullSystemPrompt)
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

        for (round in 0 until MAX_TOOL_ROUNDS) {

            val requestBody = JSONObject()
                .put("model", modelName)
                .put("messages", messages)
                .put("temperature", 0.7)
                .put("max_tokens", 1024)

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
                    val content = message.optString("content", "").trim()

                    if (finishReason == "stop" || content.isNotEmpty()) {
                        val toolCall = parseToolCall(content)
                        if (toolCall == null) {
                            return@withContext cleanOutput(content)
                        }
                        Log.d(TAG, "round=$round: toolCall=${toolCall.first} args=${toolCall.second}")
                        val toolResult = toolRegistry.execute(toolCall.first, toolCall.second)

                        if (toolResult is ToolResult.PermissionRequired) {
                            throw IrisException.PermissionRequiredException(
                                permission = toolResult.permission,
                                rationale  = toolResult.rationale,
                                toolName   = toolCall.first
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
                                .put("role", "assistant")
                                .put("content", content)
                        )
                        messages.put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", "Function result: $resultContent\n\nBased on this result, respond to the user naturally.")
                        )
                    } else {
                        throw IrisException.LlmException("Groq LLM: empty response content")
                    }
                }
                401 -> throw IrisException.AuthException("Groq LLM auth failed — check GROQ_LLM_API_KEY")
                429 -> throw IrisException.RateLimitException("Groq LLM rate limit exceeded")
                else -> throw IrisException.LlmException("Groq LLM failed HTTP ${response.code}: $bodyString")
            }
        }

        throw IrisException.LlmException("Groq LLM exceeded max tool call rounds ($MAX_TOOL_ROUNDS)")
    }

    private fun buildToolDescriptions(): String? {
        val toolsJson = toolRegistry.openAiToolsPayload() ?: return null
        val descriptions = JSONArray()

        for (i in 0 until toolsJson.length()) {
            val tool = toolsJson.getJSONObject(i)
            val fn = tool.optJSONObject("function") ?: continue
            descriptions.put(
                JSONObject()
                    .put("name", fn.optString("name", ""))
                    .put("description", fn.optString("description", ""))
                    .put("parameters", fn.optJSONObject("parameters") ?: JSONObject())
            )
        }

        return descriptions.toString(2)
    }

    private fun parseToolCall(output: String): Pair<String, JSONObject>? {
        val match = TOOL_CALL_PATTERN.find(output) ?: return null
        val jsonStr = match.value

        return try {
            val json = JSONObject(jsonStr)
            val name = json.optString("tool", "")
            if (name.isBlank()) return null
            val args = json.optJSONObject("args") ?: JSONObject()
            Pair(name, args)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanOutput(output: String): String {
        return output.replace(TOOL_CALL_PATTERN, "").trim()
    }
}
