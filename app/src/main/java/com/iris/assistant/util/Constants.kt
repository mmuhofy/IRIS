package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 220

    // --- Navigation ---
    const val NAV_ANIM_DURATION_MS    = 300
    const val NAV_SCALE_ENTER_FROM    = 0.96f
    const val NAV_SCALE_EXIT_TO       = 0.96f
    const val NAV_SLIDE_ENTER_DIVISOR = 4
    const val NAV_SLIDE_EXIT_DIVISOR  = 6

    // --- User ---
    const val USER_NAME = "Muhofy"

    // --- STT ---
    const val GROQ_STT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_STT_MODEL    = "whisper-large-v3"
    const val GROQ_STT_LANGUAGE = "tr"

    // --- LLM ---
    const val GEMINI_MODEL      = "gemini-3.5-flash"
    const val GEMINI_ENDPOINT   = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GROQ_LLM_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_LLM_MODEL    = "llama-3.3-70b-versatile"

    data class LlmModelInfo(
        val apiName     : String,
        val displayName : String,
        val provider    : String,
    )

    val LLM_PROVIDER_GEMINI = "gemini"
    val LLM_PROVIDER_GROQ   = "groq"
    val LLM_PROVIDER_LOCAL  = "local"

    val LLM_PROVIDERS = listOf(LLM_PROVIDER_GEMINI, LLM_PROVIDER_GROQ, LLM_PROVIDER_LOCAL)

    fun providerDisplayName(provider: String): String = when (provider) {
        LLM_PROVIDER_GEMINI -> "Gemini"
        LLM_PROVIDER_GROQ   -> "Groq"
        LLM_PROVIDER_LOCAL  -> "Yerel"
        else                -> provider
    }

    val LLM_MODELS = listOf(
        LlmModelInfo("gemini-3.5-flash",                              "3.5 Flash",         LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-3.1-flash",                              "3.1 Flash",         LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-2.5-flash",                              "2.5 Flash",         LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-3.5-pro",                                "3.5 Pro",           LLM_PROVIDER_GEMINI),
        LlmModelInfo("gemini-2.5-pro",                                "2.5 Pro",           LLM_PROVIDER_GEMINI),
        LlmModelInfo("llama-3.3-70b-versatile",                       "Llama 3.3 70B",     LLM_PROVIDER_GROQ),
        LlmModelInfo("llama-3.1-8b-instant",                          "Llama 3.1 8B",      LLM_PROVIDER_GROQ),
        LlmModelInfo("openai/gpt-oss-120b",                           "GPT-OSS 120B",      LLM_PROVIDER_GROQ),
        LlmModelInfo("openai/gpt-oss-20b",                            "GPT-OSS 20B",       LLM_PROVIDER_GROQ),
        LlmModelInfo("meta-llama/llama-4-scout-17b-16e-instruct",     "Llama 4 Scout 17B", LLM_PROVIDER_GROQ),
        LlmModelInfo("qwen/qwen3-32b",                                "Qwen3 32B",         LLM_PROVIDER_GROQ),
        LlmModelInfo("qwen/qwen3.6-27b",                              "Qwen3.6 27B",       LLM_PROVIDER_GROQ),
    )

    fun modelsForProvider(provider: String): List<LlmModelInfo> =
        LLM_MODELS.filter { it.provider == provider }

    fun defaultModelForProvider(provider: String): LlmModelInfo? =
        modelsForProvider(provider).firstOrNull()

    // --- TTS — Gemini ---
    const val GEMINI_TTS_MODEL           = "gemini-3.1-flash-tts-preview"
    const val GEMINI_TTS_BASE_URL        = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GEMINI_TTS_SAMPLE_RATE     = 24000
    const val GEMINI_TTS_MIN_BUFFER_SIZE = 4096
    const val GEMINI_TTS_DEFAULT_VOICE   = "Aoede"
    const val GEMINI_TTS_MAX_RETRIES     = 3
    const val GEMINI_TTS_RETRY_DELAY_MS  = 1000L

    // --- TTS — MMS (HuggingFace) ---
    const val HF_API_ENDPOINT = "https://api-inference.huggingface.co/models"
    const val MMS_TTS_MODEL   = "facebook/mms-tts-tur"

    // --- Info Tools ---
    const val TAVILY_SEARCH_ENDPOINT = "https://api.tavily.com/search"
    const val NEWS_API_ENDPOINT      = "https://newsapi.org/v2"
    const val NEWS_API_COUNTRY       = "tr"
    const val NEWS_API_PAGE_SIZE     = 5

    // --- VAD ---
    const val VAD_SILENCE_THRESHOLD_MS = 1500L

    // --- Wake Word ---
    const val WAKE_WORD_COOLDOWN_MS = 1500L

    const val WAKE_WORD_ENABLED = false

    val WAKE_WORD_MODELS = listOf(
        WakeWordModelEntry("hey_jarvis", "hey_jarvis.onnx", 0.5f),
        WakeWordModelEntry("hey_iris",   "hey_iris.onnx",   0.01f),
    )

    data class WakeWordModelEntry(
        val name      : String,
        val file      : String,
        val threshold : Float,
    )

    // --- Room ---
    const val DATABASE_NAME    = "iris_database"
    const val DATABASE_VERSION = 2

    // --- DataStore ---
    // Separate name from DATABASE_NAME — DataStore and Room are different stores.
    const val DATASTORE_NAME = "iris_preferences"

    // --- Screen / Scroll gestures ---
    // Ratios of screen height used for gesture-based scroll fallback in ScrollTool.
    // Scroll-down gesture: finger moves from START (lower) → END (upper).
    // Scroll-up  gesture: finger moves from END  (upper) → START (lower).
    const val SCROLL_GESTURE_START_RATIO  = 0.70f  // 70% down the screen
    const val SCROLL_GESTURE_END_RATIO    = 0.30f  // 30% down the screen
    const val SCROLL_GESTURE_DURATION_MS  = 300L   // gesture stroke duration

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4 — Power Mode / Embedded Shell
    // ─────────────────────────────────────────────────────────────────────────

    // Maximum terminal output lines kept in ViewModel state (ring-buffer via takeLast).
    const val TERMINAL_MAX_LINES = 500

    // Layout inside filesDir:
    //   filesDir/usr/        = TERMUX_PREFIX  (extracted bootstrap)
    //   filesDir/home/       = TERMUX_HOME    (created empty, used as $HOME)
    //   filesDir/bootstrap/  = temp download dir

    // Shell security levels (persisted as String in DataStore).
    const val SHELL_SECURITY_UNRESTRICTED = "UNRESTRICTED"
    const val SHELL_SECURITY_CONFIRM_EACH = "CONFIRM_EACH"
    const val SHELL_SECURITY_RESTRICTED   = "RESTRICTED"

    // Default = UNRESTRICTED per Muhofy's explicit decision.
    // One-time warning dialog shown on first Power Mode activation.
    const val SHELL_SECURITY_DEFAULT = SHELL_SECURITY_UNRESTRICTED

    // Per-command timeout in AI tool-fallback mode.
    // User-driven terminal sessions have no timeout (persistent session).
    const val SHELL_TOOL_TIMEOUT_MS = 30_000L

    // Max stdout/stderr bytes captured per AI shell tool call.
    const val SHELL_TOOL_OUTPUT_MAX_BYTES = 8 * 1024
}