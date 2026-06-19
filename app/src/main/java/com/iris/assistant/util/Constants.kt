package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 180

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

    data class LlmModelInfo(
        val apiName     : String,
        val displayName : String,
        val provider    : String // "gemini" or "groq"
    )

    val LLM_PROVIDER_GEMINI = "gemini"
    val LLM_PROVIDER_GROQ   = "groq"
    val LLM_PROVIDER_LOCAL  = "local"

    val LLM_PROVIDERS = listOf(
        LLM_PROVIDER_GEMINI,
        LLM_PROVIDER_GROQ,
        LLM_PROVIDER_LOCAL,
    )

    fun providerDisplayName(provider: String): String = when (provider) {
        LLM_PROVIDER_GEMINI -> "Gemini"
        LLM_PROVIDER_GROQ   -> "Groq"
        LLM_PROVIDER_LOCAL  -> "Yerel"
        else                -> provider
    }

    val LLM_MODELS = listOf(
        // Gemini models
        LlmModelInfo("gemini-3.5-flash", "3.5 Flash",     LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-3.1-flash", "3.1 Flash",     LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-2.5-flash", "2.5 Flash",     LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-3.5-pro",   "3.5 Pro",       LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-2.5-pro",   "2.5 Pro",       LLM_PROVIDER_GEMINI),

        // Groq — Production models
        LlmModelInfo("llama-3.3-70b-versatile",          "Llama 3.3 70B",       LLM_PROVIDER_GROQ),
        LlmModelInfo("llama-3.1-8b-instant",             "Llama 3.1 8B",        LLM_PROVIDER_GROQ),
        LlmModelInfo("openai/gpt-oss-120b",               "GPT-OSS 120B",        LLM_PROVIDER_GROQ),
        LlmModelInfo("openai/gpt-oss-20b",                "GPT-OSS 20B",         LLM_PROVIDER_GROQ),

        // Groq — Preview models
        LlmModelInfo("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B", LLM_PROVIDER_GROQ),
        LlmModelInfo("qwen/qwen3-32b",                    "Qwen3 32B",           LLM_PROVIDER_GROQ),
        LlmModelInfo("qwen/qwen3.6-27b",                  "Qwen3.6 27B",         LLM_PROVIDER_GROQ),
    )

    fun modelsForProvider(provider: String): List<LlmModelInfo> =
        LLM_MODELS.filter { it.provider == provider }

    fun defaultModelForProvider(provider: String): LlmModelInfo? =
        modelsForProvider(provider).firstOrNull()

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
    const val WAKE_WORD_COOLDOWN_MS = 1500L

    val WAKE_WORD_MODELS = listOf(
        WakeWordModelEntry("hey_jarvis", "hey_jarvis.onnx", 0.5f),
        WakeWordModelEntry("hey_iris",   "hey_iris.onnx",   0.01f),
    )

    data class WakeWordModelEntry(
        val name: String,
        val file: String,
        val threshold: Float,
    )

    // --- Room ---
    const val DATABASE_NAME    = "iris_database"
    const val DATABASE_VERSION = 1

    // --- DataStore ---
    const val DATASTORE_NAME = "iris_preferences"
}