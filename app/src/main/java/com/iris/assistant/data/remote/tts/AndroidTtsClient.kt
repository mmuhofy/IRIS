package com.iris.assistant.data.remote.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.iris.assistant.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// UNTESTED — verify before use
@Singleton
class AndroidTtsClient @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsProvider {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale(Constants.GROQ_STT_LANGUAGE))
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                          result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    // UNTESTED — verify before use
    override suspend fun speak(
        text      : String,
        onProgress: (Float) -> Unit,
        onDone    : () -> Unit
    ) {
        if (!isReady || tts == null) {
            onDone()
            return
        }

        suspendCancellableCoroutine { cont ->
            val utteranceId = "iris_${System.currentTimeMillis()}"

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    onProgress(0.1f)
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        onProgress(1f)
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                // Progress range emulation — Android TTS doesn't give real-time progress
                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    val progress = if (text.isNotEmpty()) end.toFloat() / text.length else 0f
                    onProgress(progress.coerceIn(0f, 1f))
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

            cont.invokeOnCancellation { stop() }
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}