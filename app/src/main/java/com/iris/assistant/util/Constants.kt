package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 320

    // --- User ---
    const val USER_NAME = "Muhofy"

    // --- STT ---
    const val GROQ_STT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_STT_MODEL    = "whisper-large-v3"
    const val GROQ_STT_LANGUAGE = "tr"

    // --- LLM ---
    const val GEMINI_MODEL    = "gemini-3.5-flash"
    const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GROQ_LLM_MODEL  = "llama-3.3-70b-versatile" // TODO: confirm at implementation time

    // --- TTS — Gemini ---
    // Model: gemini-3.1-flash-tts-preview (current stable TTS model, verified 2026-05)
    // Endpoint: /v1beta/models/{model}:generateContent
    // Output: PCM 16-bit signed, 24000 Hz, mono — base64 in candidates[0].content.parts[0].inlineData.data
    // Language: auto-detected — Turkish confirmed supported
    const val GEMINI_TTS_MODEL           = "gemini-3.1-flash-tts-preview"
    const val GEMINI_TTS_BASE_URL        = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GEMINI_TTS_SAMPLE_RATE     = 24000   // Hz, per Gemini TTS spec
    const val GEMINI_TTS_MIN_BUFFER_SIZE = 8192    // bytes, safety floor for AudioTrack buffer
    const val GEMINI_TTS_DEFAULT_VOICE   = "Aoede" // fallback if preference not yet set
    const val GEMINI_TTS_MAX_RETRIES     = 3
    const val GEMINI_TTS_RETRY_DELAY_MS  = 1000L

    // --- VAD ---
    const val VAD_SILENCE_THRESHOLD_MS = 1500L

    // --- Wake Word ---
    // Library: xyz.rementia:openwakeword:0.1.5
    // Source: https://github.com/Re-MENTIA/openwakeword-android-kt
    //
    // Required ONNX assets in app/src/main/assets/:
    //   - hey_jarvis.onnx         (prebuilt MVP placeholder — download from openWakeWord repo)
    //   - melspectrogram.onnx     (download from openWakeWord repo)
    //   - embedding_model.onnx    (download from openWakeWord repo)
    //
    // Download from:
    //   https://github.com/dscripka/openWakeWord/tree/main/openwakeword/resources/models
    //
    // WAKE_WORD_THRESHOLD: tune empirically — 0.5f is docs default for hey_jarvis model.
    // Lower = more sensitive (more false positives). Higher = less sensitive (more misses).
    //
    // WAKE_WORD_COOLDOWN_MS: prevents repeated triggers from a single detection event.
    // 2000ms matches typical post-detection UX (show listening state, start recording).
    // Delay between sending ACTION_PAUSE to WakeWordService and opening AudioRecorder.
    // Intent dispatch to a service is async — this gives WakeWordEngine time to call
    // engine.release() and free AudioRecord before AudioRecorder.startRecording() is called.
    // 150ms is conservative; tune down if latency is noticeable.
    const val WAKE_WORD_PAUSE_DELAY_MS = 150L

    const val WAKE_WORD_MODEL_NAME    = "hey_jarvis"
    const val WAKE_WORD_MODEL_FILE    = "hey_jarvis.onnx"
    const val WAKE_WORD_THRESHOLD     = 0.3f
    const val WAKE_WORD_COOLDOWN_MS   = 1500L

    // --- Notifications ---
    const val NOTIFICATION_CHANNEL_ID_WAKE   = "iris_wake_word_channel"
    const val NOTIFICATION_CHANNEL_NAME_WAKE = "IRIS Arka Plan"
    const val NOTIFICATION_TITLE_WAKE        = "IRIS aktif"
    const val NOTIFICATION_TEXT_WAKE         = "\"Hey IRIS\" dinleniyor"
    const val NOTIFICATION_ID_WAKE           = 1001

    // --- Room ---
    const val DATABASE_NAME    = "iris_database"
    const val DATABASE_VERSION = 1

    // --- DataStore ---
    const val DATASTORE_NAME = "iris_preferences"
}