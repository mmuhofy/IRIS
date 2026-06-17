package com.iris.assistant.data.remote.groq

import com.iris.assistant.BuildConfig
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.LlmRepository
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
    private val preferencesRepository: PreferencesRepository
) : LlmRepository {

    companion object {
        private const val TAG = "GroqLlmRepository"
    }

    override suspend fun chat(
        history     : List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.GROQ_LLM_API_KEY
        if (apiKey.isBlank()) throw IrisException.AuthException("GROQ_LLM_API_KEY is not set")

        val selectedModel = preferencesRepository.preferences
            .map { it.llmModel }
            .first()
        val modelName = selectedModel.removePrefix(Constants.LLM_PROVIDER_PREFIX_GROQ)
            .takeIf { it.isNotBlank() }
            ?: Constants.GROQ_LLM_MODEL

        // Build messages array
        val messages = JSONArray()

        // System prompt first
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemPrompt)
        )

        // Conversation history
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

        val requestBody = JSONObject()
            .put("model", modelName)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 1024)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(Constants.GROQ_LLM_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
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
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0)
                        .optJSONObject("message")
                        ?.optString("content", "")
                        ?.trim()
                        ?: throw IrisException.LlmException("Groq LLM: empty message content")
                } else {
                    throw IrisException.LlmException("Groq LLM: no choices in response")
                }
            }
            401 -> throw IrisException.AuthException("Groq LLM auth failed — check GROQ_LLM_API_KEY")
            429 -> throw IrisException.RateLimitException("Groq LLM rate limit exceeded")
            else -> throw IrisException.LlmException("Groq LLM failed HTTP ${response.code}: $bodyString")
        }
    }
}
