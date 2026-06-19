package com.iris.assistant.data.remote.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.iris.assistant.BuildConfig
import com.iris.assistant.domain.model.IrisException
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MmsTtsClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : TtsProvider {

    companion object {
        private const val TAG = "MmsTtsClient"
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 2000L
    }

    private val apiKey: String get() = BuildConfig.HF_API_KEY

    @Volatile private var activeAudioTrack: AudioTrack? = null
    @Volatile private var stopRequested = false

    override suspend fun speak(
        text      : String,
        onProgress: (Float) -> Unit,
        onDone    : () -> Unit
    ) {
        stopRequested = false

        if (apiKey.isBlank()) {
            throw IrisException.TtsException("MMS TTS: HF_API_KEY eksik — local.properties'e ekleyin")
        }

        val wavBytes = fetchWavWithRetry(text)
            ?: throw IrisException.TtsException("MMS TTS failed after $MAX_RETRIES retries")
        val (pcmBytes, sampleRate) = parseWav(wavBytes)
            ?: throw IrisException.TtsException("MMS TTS: geçersiz WAV yanıtı")
        playPcm(pcmBytes, sampleRate, onProgress, onDone)
    }

    private suspend fun fetchWavWithRetry(text: String): ByteArray? =
        withContext(Dispatchers.IO) {
            repeat(MAX_RETRIES) { attempt ->
                if (stopRequested) return@withContext null
                val result = runCatching { fetchWav(text) }
                val wav = result.getOrNull()
                if (wav != null) return@withContext wav

                Log.w(TAG, "fetchWavWithRetry: attempt ${attempt + 1} failed — ${result.exceptionOrNull()?.message}")
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
            null
        }

    private suspend fun fetchWav(text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val url = "${Constants.HF_API_ENDPOINT}/${Constants.MMS_TTS_MODEL}"

            val body = JSONObject().apply {
                put("inputs", text)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw IllegalStateException(
                    "MMS TTS failed: HTTP ${response.code} — ${body.take(200)}"
                )
            }

            response.body?.bytes()
                ?: throw IllegalStateException("MMS TTS: boş yanıt")
        }

    private data class WavInfo(val pcmData: ByteArray, val sampleRate: Int)

    private fun parseWav(wavBytes: ByteArray): WavInfo? {
        return try {
            val stream = ByteArrayInputStream(wavBytes)
            val header = ByteArray(44)
            if (stream.read(header) < 44) return null

            if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()) return null
            if (header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte() ||
                header[10] != 'V'.code.toByte() || header[11] != 'E'.code.toByte()) return null

            val sampleRate = ((header[24].toInt() and 0xFF) shl 0) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[27].toInt() and 0xFF) shl 24)

            val bitsPerSample = ((header[34].toInt() and 0xFF) shl 0) or
                    ((header[35].toInt() and 0xFF) shl 8)

            // Find "data" subchunk (skip any extra chunks between fmt and data)
            var dataSize = 0
            val dataStart: Int = run {
                var offset = 12
                while (offset + 8 <= wavBytes.size) {
                    val chunkId = String(wavBytes, offset, 4)
                    val chunkSize = ((wavBytes[offset + 4].toInt() and 0xFF) shl 0) or
                            ((wavBytes[offset + 5].toInt() and 0xFF) shl 8) or
                            ((wavBytes[offset + 6].toInt() and 0xFF) shl 16) or
                            ((wavBytes[offset + 7].toInt() and 0xFF) shl 24)
                    if (chunkId == "data") {
                        dataSize = chunkSize
                        return@run offset + 8
                    }
                    offset += 8 + chunkSize
                }
                -1
            }

            if (dataStart < 0 || dataSize <= 0) return null

            val pcmData = wavBytes.copyOfRange(dataStart, (dataStart + dataSize).coerceAtMost(wavBytes.size))

            // Convert non-16-bit samples
            val finalPcm = if (bitsPerSample == 16) {
                pcmData
            } else if (bitsPerSample == 8) {
                // Convert 8-bit unsigned to 16-bit signed
                ByteArray(pcmData.size * 2).also { out ->
                    pcmData.forEachIndexed { i, sample ->
                        val s = ((sample.toInt() and 0xFF) - 128) * 256
                        out[i * 2] = (s and 0xFF).toByte()
                        out[i * 2 + 1] = (s shr 8).toByte()
                    }
                }
            } else {
                // Unsupported bit depth — try raw anyway
                pcmData
            }

            WavInfo(finalPcm, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "parseWav failed", e)
            null
        }
    }

    private suspend fun playPcm(
        pcmBytes  : ByteArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit,
        onDone    : () -> Unit
    ) = withContext(Dispatchers.IO) {
        val channelCfg = AudioFormat.CHANNEL_OUT_MONO
        val encoding   = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelCfg, encoding)
            .coerceAtLeast(Constants.GEMINI_TTS_MIN_BUFFER_SIZE)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
