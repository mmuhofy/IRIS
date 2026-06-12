package com.iris.assistant.data.remote.gemini

import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.LlmRepository
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
    private val okHttpClient: OkHttpClient
) : LlmRepository {

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw IrisException.AuthException("GEMINI_API_KEY is not set")

        val url = "${Constants.GEMINI_ENDPOINT}/${Constants.GEMINI_MODEL}:generateContent?key=$apiKey"

        // Build contents array from history
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

        // System instruction (Gemini supports it as a top-level field)
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))

        val requestBody = JSONObject()
            .put("system_instruction", systemInstruction)
            .put("contents", contents)
            .put(
                "generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 1024)
            )
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
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
            200 -> parseGeminiResponse(bodyString)
            401 -> throw IrisException.AuthException("Gemini auth failed — check GEMINI_API_KEY")
            429 -> throw IrisException.RateLimitException("Gemini rate limit exceeded")
            else -> throw IrisException.LlmException("Gemini failed HTTP ${response.code}: $bodyString")
        }
    }

    private fun parseGeminiResponse(body: String): String {
        return runCatching {
            JSONObject(body)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }.getOrElse {
            throw IrisException.LlmException("Gemini parse error: $body")
        }
    }
}