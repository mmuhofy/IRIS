package com.iris.assistant.data.remote.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.iris.assistant.BuildConfig
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS provider using the Gemini generateContent API.
 *
 * Verified endpoint:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 *
 * Model: gemini-3.1-flash-tts-preview
 *
 * Request shape:
 * {
 *   "contents": [{ "parts": [{ "text": "<text>" }] }],
 *   "generationConfig": {
 *     "responseModalities": ["AUDIO"],
 *     "speechConfig": {
 *       "voiceConfig": {
 *         "prebuiltVoiceConfig": { "voiceName": "<voice>" }
 *       }
 *     }
 *   }
 * }
 *
 * Response shape:
 *   candidates[0].content.parts[0].inlineData.data  → base64 PCM
 *   candidates[0].content.parts[0].inlineData.mimeType → "audio/pcm;rate=24000"
 *
 * Output format: PCM 16-bit signed, 24000 Hz, mono
 * Language: auto-detected from input text — Turkish (tr) supported
 *
 * Voice: read from DataStore (PreferencesRepository) on each call.
 * Changing the voice in Settings takes effect on the next speak() call.
 */
@Singleton
class GeminiTtsClient @Inject constructor(
    private val okHttpClient         : OkHttpClient,
    private val preferencesRepository: PreferencesRepository
) : TtsProvider {

    // API key sourced from BuildConfig — injected at build time via GitHub Secrets / local.properties
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    companion object {
        private const val TAG = "GeminiTtsClient"
    }

    @Volatile private var activeAudioTrack: AudioTrack? = null
    @Volatile private var stopRequested = false

    override suspend fun speak(
        text      : String,
        onProgress: (Float) -> Unit,
        onDone    : () -> Unit
    ) {
        stopRequested = false

        // Read user's preferred voice from DataStore — .first() takes the current snapshot
        val voice = preferencesRepository.preferences.first().ttsVoice

        val pcmBytes = fetchPcmWithRetry(text, voice) ?: run {
            Log.e(TAG, "speak: failed to fetch audio after ${Constants.GEMINI_TTS_MAX_RETRIES} retries")
            onDone()
            return
        }

        playPcm(pcmBytes, onProgress, onDone)
    }

    /**
     * Retries the TTS request up to GEMINI_TTS_MAX_RETRIES times.
     * HTTP 500 with text tokens is a known intermittent issue — retry handles it.
     */
    private suspend fun fetchPcmWithRetry(text: String, voice: TtsVoice): ByteArray? =
        withContext(Dispatchers.IO) {
            repeat(Constants.GEMINI_TTS_MAX_RETRIES) { attempt ->
                if (stopRequested) return@withContext null

                val result = runCatching { fetchPcm(text, voice) }
                val pcm = result.getOrNull()

                if (pcm != null) return@withContext pcm

                Log.w(TAG, "fetchPcmWithRetry: attempt ${attempt + 1} failed — ${result.exceptionOrNull()?.message}")

                if (attempt < Constants.GEMINI_TTS_MAX_RETRIES - 1) {
                    delay(Constants.GEMINI_TTS_RETRY_DELAY_MS)
                }
            }
            null
        }

    /**
     * Single TTS request attempt.
     * Throws on network error, non-200 response, or missing inlineData block.
     */
    private suspend fun fetchPcm(text: String, voice: TtsVoice): ByteArray =
        withContext(Dispatchers.IO) {
            val bodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", text) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voice.apiName)
                            })
                        })
                    })
                })
            }

            val url = "${Constants.GEMINI_TTS_BASE_URL}/${Constants.GEMINI_TTS_MODEL}:generateContent"

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response     = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                throw IllegalStateException("TTS request failed: HTTP ${response.code} — $responseBody")
            }

            // Parse: candidates[0].content.parts[0].inlineData.data
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
                ?: throw IllegalStateException("TTS response missing 'candidates' — body: $responseBody")

            val parts = candidates
                .getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("TTS response missing 'content.parts'")

            val inlineData = parts
                .getJSONObject(0)
                .optJSONObject("inlineData")
                ?: throw IllegalStateException("TTS inlineData missing — model may have returned text tokens, will retry")

            val base64Data = inlineData.optString("data")
            if (base64Data.isEmpty()) {
                throw IllegalStateException("TTS inlineData.data is empty")
            }

            Base64.decode(base64Data, Base64.DEFAULT)
        }

    /**
     * Plays raw PCM bytes via AudioTrack.
     * Format: PCM 16-bit signed, 24000 Hz, mono — matches Gemini TTS output spec.
     */
    private suspend fun playPcm(
        pcmBytes  : ByteArray,
        onProgress: (Float) -> Unit,
        onDone    : () -> Unit
    ) = withContext(Dispatchers.IO) {
        val sampleRate = Constants.GEMINI_TTS_SAMPLE_RATE
        val channelCfg = AudioFormat.CHANNEL_OUT_MONO
        val encoding   = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelCfg, encoding)
            .coerceAtLeast(Constants.GEMINI_TTS_MIN_BUFFER_SIZE)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelCfg)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeAudioTrack = track
        track.play()
        onProgress(0.1f)

        var offset = 0
        while (offset < pcmBytes.size && !stopRequested) {
            val chunk = minOf(bufferSize, pcmBytes.size - offset)
            track.write(pcmBytes, offset, chunk)
            offset += chunk
            onProgress((offset.toFloat() / pcmBytes.size).coerceIn(0f, 1f))
        }

        track.stop()
        track.release()
        activeAudioTrack = null

        onProgress(1f)
        onDone()
    }

    override fun stop() {
        stopRequested = true
        activeAudioTrack?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
            activeAudioTrack = null
        }
    }

    override fun release() {
        stop()
    }
}