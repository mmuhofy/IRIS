package com.iris.assistant.data.remote.gemini

import android.util.Log
import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.LlmRepository
import com.iris.assistant.domain.tools.ToolRegistry
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
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

// UNTESTED — verify before use
@Singleton
class GeminiRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val toolRegistry: ToolRegistry
) : LlmRepository {

    companion object {
        private const val TAG = "GeminiRepository"
        private const val MAX_FUNC_CALL_ROUNDS = 10
    }

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw IrisException.AuthException("GEMINI_API_KEY is not set")

        val url = "${Constants.GEMINI_ENDPOINT}/${Constants.GEMINI_MODEL}:generateContent?key=$apiKey"

        // Build contents array from history — start with the conversation
        val contents = buildContents(history)

        // Include tool declarations if any tools are registered
        val toolsPayload = toolRegistry.geminiToolsPayload()

        // System instruction
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))

        // Multi-turn function calling loop
        for (round in 0 until MAX_FUNC_CALL_ROUNDS) {

            val requestBody = JSONObject()
                .put("system_instruction", systemInstruction)
                .put("contents", contents)
                .put(
                    "generationConfig", JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 1024)
                )

            if (toolsPayload != null) {
                requestBody.put("tools", toolsPayload)
            }

            val jsonRequest = requestBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(jsonRequest)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw IrisException.NetworkException("Gemini network error: ${e.message}", e)
            }

            val bodyString = response.body?.string()
                ?: throw IrisException.LlmException("Gemini response body is null (HTTP ${response.code})")

            when (response.code) {
                200 -> {
                    val parsed = parseGeminiResponse(bodyString) ?: run {
                        throw IrisException.LlmException("Gemini empty response after $round rounds")
                    }
                    val textContent = parsed.first
                    val functionCall = parsed.second

                    // If no function_call, return text directly
                    if (functionCall == null) {
                        return@withContext textContent?.trim() ?: ""
                    }

                    Log.d(TAG, "round=$round: functionCall=${functionCall.fnName}")

                    // Add the model's function_call content to history
                    contents.put(
                        JSONObject()
                            .put("role", "model")
                            .put("parts", JSONArray().put(functionCall.toJson()))
                    )

                    // Execute tool
                    val toolResult = toolRegistry.execute(functionCall.fnName, functionCall.args)

                    // Build function_response part and add to contents
                    val responsePart = buildFunctionResponse(functionCall.fnName, toolResult)
                    contents.put(responsePart)

                    // If tool returned PermissionRequired, propagate as exception
                    if (toolResult is ToolResult.PermissionRequired) {
                        throw IrisException.PermissionRequiredException(
                            permission = toolResult.permission,
                            rationale  = toolResult.rationale,
                            toolName   = functionCall.fnName
                        )
                    }

                    // If tool cancelled, stop
                    if (toolResult is ToolResult.Cancelled) {
                        return@withContext "İşlem iptal edildi."
                    }

                    // Continue loop — Gemini will generate final text from function_response
                }
                401 -> throw IrisException.AuthException("Gemini auth failed — check GEMINI_API_KEY")
                429 -> throw IrisException.RateLimitException("Gemini rate limit exceeded")
                else -> throw IrisException.LlmException("Gemini failed HTTP ${response.code}: $bodyString")
            }
        }

        throw IrisException.LlmException("Gemini exceeded max function call rounds ($MAX_FUNC_CALL_ROUNDS)")
    }

    private fun buildContents(history: List<ChatMessage>): JSONArray {
        val contents = JSONArray()
        history.forEach { msg ->
            val role = when (msg.role) {
                ChatMessage.Role.USER      -> "user"
                ChatMessage.Role.ASSISTANT -> "model"
            }
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            )
        }
        return contents
    }

    private data class FunctionCallInfo(
        val fnName: String,
        val args  : JSONObject
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put("functionCall", JSONObject()
                    .put("name", fnName)
                    .put("args", args))
        }
    }

    /**
     * Parses Gemini response. Returns (textContent, functionCallInfo) or null if empty.
     * Either textContent or functionCallInfo may be null depending on what Gemini returned.
     */
    private fun parseGeminiResponse(body: String): Pair<String?, FunctionCallInfo?>? {
        return runCatching {
            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) return@runCatching null

            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@runCatching null
            val parts = content.optJSONArray("parts") ?: return@runCatching null

            var textContent: String? = null
            var functionCall: FunctionCallInfo? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    textContent = part.getString("text")
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    functionCall = FunctionCallInfo(name, args)
                }
            }

            Pair(textContent, functionCall)
        }.getOrElse {
            throw IrisException.LlmException("Gemini parse error: $body")
        }
    }

    private fun buildFunctionResponse(fnName: String, toolResult: ToolResult): JSONObject {
        val responseJson = when (toolResult) {
            is ToolResult.Success -> {
                JSONObject().apply {
                    put("success", true)
                    put("result", toolResult.displayText)
                    toolResult.data.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            }
            is ToolResult.Error -> {
                JSONObject().apply {
                    put("success", false)
                    put("error", toolResult.message)
                }
            }
            is ToolResult.PermissionRequired -> {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Permission required: ${toolResult.permission}")
                    put("rationale", toolResult.rationale)
                }
            }
            is ToolResult.Cancelled -> {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Cancelled by user")
                }
            }
        }

        return JSONObject()
            .put("role", "function")
            .put("parts", JSONArray().put(
                JSONObject().put("functionResponse", JSONObject()
                    .put("name", fnName)
                    .put("response", responseJson))
            ))
    }
}