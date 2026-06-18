package com.iris.assistant.service.voice

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.iris.assistant.BuildConfig
import com.iris.assistant.util.Constants
import dagger.hilt.android.EntryPointAccessors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class IrisRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "IrisRecognitionService"
        private const val SAMPLE_RATE = 16000
    }

    private lateinit var okHttpClient: OkHttpClient

    private var audioRecorder: AudioRecord? = null
    private var pcmBuffer: ByteArrayOutputStream? = null
    private var recordingThread: Thread? = null
    private var activeCallback: Callback? = null

    override fun onStartListening(intent: Intent, callback: Callback) {
        Log.d(TAG, "onStartListening")
        if (!::okHttpClient.isInitialized) {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                VoiceInteractionEntryPoint::class.java
            )
            okHttpClient = entryPoint.okHttpClient()
        }
        activeCallback = callback

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize <= 0) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        pcmBuffer = ByteArrayOutputStream()

        audioRecorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            audioRecorder?.release()
            audioRecorder = null
            return
        }

        audioRecorder?.startRecording()
        callback.readyForSpeech(Bundle())
        callback.beginningOfSpeech()

        val buffer = ByteArray(bufferSize)
        recordingThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val recorder = audioRecorder
            while (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    synchronized(this) {
                        pcmBuffer?.write(buffer, 0, bytesRead)
                    }
                }
            }
        }.also { it.start() }
    }

    override fun onStopListening(callback: Callback) {
        Log.d(TAG, "onStopListening")
        stopRecording()
        callback.endOfSpeech()
        processAudio(callback)
    }

    override fun onCancel(callback: Callback) {
        Log.d(TAG, "onCancel")
        stopRecording()
        activeCallback = null
    }

    private fun stopRecording() {
        audioRecorder?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        audioRecorder = null
        recordingThread?.join(2000)
        recordingThread = null
    }

    private fun processAudio(callback: Callback) {
        val pcm = pcmBuffer?.toByteArray() ?: run {
            callback.error(SpeechRecognizer.ERROR_NO_MATCH)
            activeCallback = null
            return
        }
        pcmBuffer = null

        Thread {
            try {
                val wavData = pcmToWav(pcm)
                val text = transcribe(wavData)
                if (text.isNotBlank()) {
                    callback.results(Bundle().apply {
                        putStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION,
                            arrayListOf(text)
                        )
                        putFloatArray(
                            SpeechRecognizer.CONFIDENCE_SCORES,
                            floatArrayOf(1.0f)
                        )
                    })
                } else {
                    callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                callback.error(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
            }
            activeCallback = null
        }.start()
    }

    private fun transcribe(wavBytes: ByteArray): String {
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) return ""

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "audio.wav",
                body = wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", Constants.GROQ_STT_MODEL)
            .addFormDataPart("language", Constants.GROQ_STT_LANGUAGE)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0.0")
            .build()

        val request = Request.Builder()
            .url(Constants.GROQ_STT_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val bodyString = response.body?.string() ?: return ""

        if (response.code != 200) return ""

        return runCatching { JSONObject(bodyString).getString("text") }
            .getOrNull()
            ?.trim() ?: ""
    }

    private fun pcmToWav(pcm: ByteArray): ByteArray {
        val totalSize = 44 + pcm.size
        val wav = ByteArrayOutputStream(totalSize)
        wav.writeRIFFHeader(totalSize - 8)
        wav.writeFmtChunk()
        wav.writeDataChunk(pcm)
        return wav.toByteArray()
    }

    private fun OutputStream.writeRIFFHeader(dataSize: Int) {
        write("RIFF".toByteArray())
        write(intToBytes(dataSize, 4))
        write("WAVE".toByteArray())
    }

    private fun OutputStream.writeFmtChunk() {
        write("fmt ".toByteArray())
        write(intToBytes(16, 4))
        write(shortToBytes(1))
        write(shortToBytes(1))
        write(intToBytes(SAMPLE_RATE, 4))
        write(intToBytes(SAMPLE_RATE * 2, 4))
        write(shortToBytes(2))
        write(shortToBytes(16))
    }

    private fun OutputStream.writeDataChunk(pcm: ByteArray) {
        write("data".toByteArray())
        write(intToBytes(pcm.size, 4))
        write(pcm)
    }

    private fun intToBytes(value: Int, length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (i in 0 until length) {
            bytes[i] = (value shr (i * 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun shortToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte()
        )
    }
}
