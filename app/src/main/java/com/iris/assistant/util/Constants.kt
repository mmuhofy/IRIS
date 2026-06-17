package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 400

    // --- User ---
    const val USER_NAME = "Muhofy"

    // --- STT ---
    const val GROQ_STT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_STT_MODEL    = "whisper-large-v3"
    const val GROQ_STT_LANGUAGE = "tr"

    // --- LLM ---
    const val GEMINI_MODEL       = "gemini-3.5-flash"
    const val GEMINI_ENDPOINT    = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GROQ_LLM_ENDPOINT  = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_LLM_MODEL     = "llama-3.3-70b-versatile"

    // Model strings prefixed with "groq:" route to GroqLlmRepository.
    // All others go to GeminiRepository.
    val LLM_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-flash",
        "gemini-2.5-flash",
        "gemini-3.5-pro",
        "gemini-2.5-pro",
        "groq:llama-3.3-70b-versatile",
    )

    const val LLM_PROVIDER_PREFIX_GROQ = "groq:"

    // --- TTS — Gemini ---
    // Model: gemini-3.1-flash-tts-preview (current stable TTS model, verified 2026-05)
    // Endpoint: /v1beta/models/{model}:generateContent
    // Output: PCM 16-bit signed, 24000 Hz, mono — base64 in candidates[0].content.parts[0].inlineData.data
    // Language: auto-detected — Turkish confirmed supported
    const val GEMINI_TTS_MODEL           = "gemini-3.1-flash-tts-preview"
    const val GEMINI_TTS_BASE_URL        = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GEMINI_TTS_SAMPLE_RATE     = 24000   // Hz, per Gemini TTS spec
    const val GEMINI_TTS_MIN_BUFFER_SIZE = 4096    // bytes, safety floor for AudioTrack buffer
    const val GEMINI_TTS_DEFAULT_VOICE   = "Aoede" // fallback if preference not yet set
    const val GEMINI_TTS_MAX_RETRIES     = 3
    const val GEMINI_TTS_RETRY_DELAY_MS  = 1000L

    // --- Info Tools ---
    const val TAVILY_SEARCH_ENDPOINT = "https://api.tavily.com/search"
    const val NEWS_API_ENDPOINT     = "https://newsapi.org/v2"
    const val NEWS_API_COUNTRY    = "tr"
    const val NEWS_API_PAGE_SIZE  = 5

    // --- VAD ---
    const val VAD_SILENCE_THRESHOLD_MS = 1500L

    // --- Wake Word ---
    // Library: xyz.rementia:openwakeword:0.1.5
    // Source: https://github.com/Re-MENTIA/openwakeword-android-kt
    //
    // Required ONNX assets in app/src/main/assets/:
    //   - hey_jarvis.onnx         (prebuilt from openWakeWord v0.5.1)
    //   - hey_iris.onnx           (custom-trained via tools/train_hey_iris.ipynb)
    //   - melspectrogram.onnx     (openWakeWord v0.5.1)
    //   - embedding_model.onnx    (openWakeWord v0.5.1)
    //
    // WAKE_WORD_COOLDOWN_MS: prevents repeated triggers from a single detection event.
    // Delay between sending ACTION_PAUSE to WakeWordService and opening AudioRecorder.
    // Intent dispatch to a service is async — this gives WakeWordEngine time to call
    // engine.release() and free AudioRecord before AudioRecorder.startRecording() is called.
    // 150ms is conservative; tune down if latency is noticeable.
    const val WAKE_WORD_PAUSE_DELAY_MS = 150L

    const val WAKE_WORD_COOLDOWN_MS   = 1500L

    val WAKE_WORD_MODELS = listOf(
        WakeWordModelEntry("hey_jarvis", "hey_jarvis.onnx", 0.5f),
        WakeWordModelEntry("hey_iris",   "hey_iris.onnx",   0.01f),
    )

    data class WakeWordModelEntry(
        val name: String,
        val file: String,
        val threshold: Float,
    )

    // --- Notifications ---
    const val NOTIFICATION_CHANNEL_ID_WAKE   = "iris_wake_word_channel"
    const val NOTIFICATION_CHANNEL_NAME_WAKE = "IRIS Arka Plan"
    const val NOTIFICATION_TITLE_WAKE        = "IRIS aktif"
    const val NOTIFICATION_TEXT_WAKE         = "\"Hey Jarvis\" / \"Hey IRIS\" dinleniyor"
    const val NOTIFICATION_ID_WAKE           = 1001

    // --- Room ---
    const val DATABASE_NAME    = "iris_database"
    const val DATABASE_VERSION = 1

    // --- DataStore ---
    const val DATASTORE_NAME = "iris_preferences"
}