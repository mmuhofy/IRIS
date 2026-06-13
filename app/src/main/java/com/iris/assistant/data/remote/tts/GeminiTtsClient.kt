package com.iris.assistant.data.remote.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.iris.assistant.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS provider using the Gemini Interactions API (gemini-2.5-flash-tts).
 *
 * API reference: https://ai.google.dev/gemini-api/docs/interactions/speech-generation
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/interactions
 * Output format: PCM 16-bit, 24kHz, mono, base64-encoded in JSON response
 * Language: auto-detected from input text (Turkish supported)
 * Streaming: NOT supported by this model — full audio returned in one response
 *
 * UNTESTED — verify before use
 */
@Singleton
class GeminiTtsClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : TtsProvider {

    // API key sourced from BuildConfig — injected at build time via GitHub Secrets / local.properties
    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    companion object {
        private const val TAG = "GeminiTtsClient"
    }

    // Currently playing AudioTrack — held so stop() can interrupt it
    @Volatile private var activeAudioTrack: AudioTrack? = null

    // Flag to abort playback loop on stop()
    @Volatile private var stopRequested = false

    // UNTESTED — verify before use
    override suspend fun speak(
        text: String,
        onProgress: (Float) -> Unit,
        onDone: () -> Unit
    ) {
        stopRequested = false

        val pcmBytes = fetchPcmWithRetry(text) ?: run {
            Log.e(TAG, "speak: failed to fetch audio after retries")
            onDone()
            return
        }

        playPcm(pcmBytes, onProgress, onDone)
    }

    /**
     * Calls the Gemini TTS endpoint with retry logic.
     * The API occasionally returns HTTP 500 with text tokens instead of audio tokens —
     * this is a known intermittent issue documented in the API limitations.
     * Returns decoded PCM bytes on success, null after all retries exhausted.
     *
     * UNTESTED — verify before use
     */
    private suspend fun fetchPcmWithRetry(text: String): ByteArray? =
        withContext(Dispatchers.IO) {
            repeat(Constants.GEMINI_TTS_MAX_RETRIES) { attempt ->
                if (stopRequested) return@withContext null

                val result = runCatching { fetchPcm(text) }
                val pcm = result.getOrNull()

                if (pcm != null) return@withContext pcm

                Log.w(TAG, "fetchPcmWithRetry: attempt ${attempt + 1} failed: ${result.exceptionOrNull()?.message}")

                if (attempt < Constants.GEMINI_TTS_MAX_RETRIES - 1) {
                    delay(Constants.GEMINI_TTS_RETRY_DELAY_MS)
                }
            }
            null
        }

    /**
     * Single attempt: builds the request, sends it, parses the base64 PCM audio.
     * Throws on network error, non-200 response, or missing audio block.
     *
     * Request shape (REST):
     * POST /v1beta/interactions
     * {
     *   "model": "gemini-2.5-flash-tts",
     *   "input": "<text>",
     *   "response_modalities": ["audio"],
     *   "generation_config": {
     *     "speech_config": [{ "voice": "<voice_name>" }]
     *   }
     * }
     *
     * Response: steps[] → content[] → { type: "audio", data: "<base64 PCM>" }
     *
     * UNTESTED — verify before use
     */
    private suspend fun fetchPcm(text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", Constants.GEMINI_TTS_MODEL)
            put("input", text)
            put("response_modalities", JSONArray().apply { put("audio") })
            put("generation_config", JSONObject().apply {
                put("speech_config", JSONArray().apply {
                    put(JSONObject().apply {
                        put("voice", Constants.GEMINI_TTS_VOICE)
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(Constants.GEMINI_TTS_ENDPOINT)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody == null) {
            throw IllegalStateException("TTS request failed: HTTP ${response.code} — $responseBody")
        }

        // Parse: { "steps": [ { "content": [ { "type": "audio", "data": "<base64>" } ] } ] }
        val json = JSONObject(responseBody)
        val steps = json.optJSONArray("steps")
            ?: throw IllegalStateException("TTS response missing 'steps' field")

        for (i in 0 until steps.length()) {
            val content = steps.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val block = content.getJSONObject(j)
                if (block.optString("type") == "audio") {
                    val base64Data = block.optString("data")
                    if (base64Data.isNotEmpty()) {
                        return@withContext Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }
        }

        throw IllegalStateException("TTS response contained no audio block (possible text-token fallback — will retry)")
    }

    /**
     * Plays raw PCM bytes via AudioTrack.
     * Format: 16-bit signed, 24000 Hz, mono — matches Gemini TTS output spec.
     * Calls onProgress(0..1) as playback advances, then onDone() when finished.
     *
     * UNTESTED — verify before use
     */
    private suspend fun playPcm(
        pcmBytes: ByteArray,
        onProgress: (Float) -> Unit,
        onDone: () -> Unit
    ) = withContext(Dispatchers.IO) {
        val sampleRate   = Constants.GEMINI_TTS_SAMPLE_RATE
        val channelCfg   = AudioFormat.CHANNEL_OUT_MONO
        val encoding     = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize   = AudioTrack.getMinBufferSize(sampleRate, channelCfg, encoding)
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

            val progress = offset.toFloat() / pcmBytes.size
            onProgress(progress.coerceIn(0f, 1f))
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