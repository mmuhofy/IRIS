package com.iris.assistant.data.remote.groq

import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.domain.repository.SttRepository
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) : SttRepository {

    // UNTESTED — verify before use
    override suspend fun transcribe(audioBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) throw IrisException.AuthException("GROQ_API_KEY is not set")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name     = "file",
                filename = "audio.wav",
                body     = audioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model",           Constants.GROQ_STT_MODEL)
            .addFormDataPart("language",        Constants.GROQ_STT_LANGUAGE)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature",     "0.0")
            .build()

        val request = Request.Builder()
            .url(Constants.GROQ_STT_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw IrisException.NetworkException("STT network error: ${e.message}", e)
        }

        val bodyString = response.body?.string()
            ?: throw IrisException.SttException("STT response body is null (HTTP ${response.code})")

        when (response.code) {
            200  -> {
                val text = runCatching { JSONObject(bodyString).getString("text") }
                    .getOrElse { throw IrisException.SttException("STT parse error: $bodyString") }
                if (text.isBlank()) throw IrisException.SttException("STT returned empty transcript")
                text.trim()
            }
            401  -> throw IrisException.AuthException("STT auth failed — check GROQ_API_KEY")
            429  -> throw IrisException.RateLimitException("STT rate limit exceeded")
            else -> throw IrisException.SttException("STT failed HTTP ${response.code}: $bodyString")
        }
    }
}