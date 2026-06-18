package com.iris.assistant.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.iris.assistant.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

// UNTESTED — verify before use
@Singleton
class AudioRecorder @Inject constructor() {

    companion object {
        private const val SAMPLE_RATE       = 16000  // Hz — Whisper optimal
        private const val CHANNEL_CONFIG    = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT      = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_RMS_THRESHOLD = 300f   // tune via testing
    }

    /**
     * Records audio until VAD detects [Constants.VAD_SILENCE_THRESHOLD_MS] ms of silence.
     * Calls [onAmplitude] with normalized 0..1 amplitude each chunk for UI feedback.
     * Returns raw PCM bytes (16kHz, mono, 16-bit) wrapped in WAV.
     */
    @SuppressLint("MissingPermission") // Permission checked by caller before invoking
    suspend fun recordUntilSilence(
        onAmplitude: (Float) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val pcmOut      = ByteArrayOutputStream()
        val buffer      = ShortArray(bufferSize / 2)
        var silenceMs   = 0L
        var lastChunkMs = System.currentTimeMillis()
        var hasSpeech   = false

        recorder.startRecording()

        try {
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val now     = System.currentTimeMillis()
                val elapsed = now - lastChunkMs
                lastChunkMs = now

                // RMS amplitude for this chunk
                val rms = sqrt(buffer.take(read).map { it.toLong() * it.toLong() }.average()).toFloat()

                // Normalized 0..1 for UI — clamp at 3000 RMS as "full"
                onAmplitude((rms / 3000f).coerceIn(0f, 1f))

                // Write PCM to output
                for (i in 0 until read) {
                    val s = buffer[i]
                    pcmOut.write(s.toInt() and 0xFF)
                    pcmOut.write((s.toInt() shr 8) and 0xFF)
                }

                // VAD: track consecutive silence
                if (rms < SILENCE_RMS_THRESHOLD) {
                    silenceMs += elapsed
                    if (silenceMs >= Constants.VAD_SILENCE_THRESHOLD_MS) break
                } else {
                    hasSpeech = true
                    silenceMs = 0
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        onAmplitude(0f)

        // If no speech detected at all, return empty array (caller should discard)
        if (!hasSpeech) return@withContext byteArrayOf()

        pcmOut.toByteArray().toWav()
    }

    // ---------------------------------------------------------------------------
    // Wrap raw PCM bytes in a minimal WAV header (required by Whisper API)
    // ---------------------------------------------------------------------------
    private fun ByteArray.toWav(): ByteArray {
        val pcmSize    = size
        val totalSize  = pcmSize + 36
        val out        = ByteArrayOutputStream(totalSize + 8)

        fun Int.le4(): ByteArray = byteArrayOf(
            (this and 0xFF).toByte(),
            (this shr 8 and 0xFF).toByte(),
            (this shr 16 and 0xFF).toByte(),
            (this shr 24 and 0xFF).toByte()
        )
        fun Short.le2(): ByteArray = byteArrayOf(
            (this.toInt() and 0xFF).toByte(),
            (this.toInt() shr 8 and 0xFF).toByte()
        )

        out.write("RIFF".toByteArray())
        out.write(totalSize.le4())
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(16.le4())                    // chunk size
        out.write(1.toShort().le2())           // PCM format
        out.write(1.toShort().le2())           // mono
        out.write(SAMPLE_RATE.le4())
        out.write((SAMPLE_RATE * 2).le4())     // byte rate
        out.write(2.toShort().le2())           // block align
        out.write(16.toShort().le2())          // bits per sample
        out.write("data".toByteArray())
        out.write(pcmSize.le4())
        out.write(this)

        return out.toByteArray()
    }
}