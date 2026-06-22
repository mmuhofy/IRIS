package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 220

    // --- Navigation ---
    // Shared transition timing/scale values for IrisNavGraph.kt.
    //
    // History:
    //  1. First attempt: 0.95f/0.92f at 280ms scale+fade only — confirmed by
    //     Muhofy as "barely visible, sometimes nothing."
    //  2. Widened to 0.90f/0.90f at 300ms (Peristyle-matched) — but root
    //     cause (opaque per-screen Scaffold backgrounds painting over the
    //     transition) made any scale-only value invisible regardless.
    //  3. Root cause fixed (Scaffold containerColor = Transparent across all
    //     main-flow screens). A second AI pass reverted screens to opaque
    //     "for performance," re-breaking visibility — reverted back to
    //     Transparent. Visible animation is a hard requirement.
    //  4. Compared against Muhofy's other app (Still): its NoteEditorScreen
    //     uses an OPAQUE Scaffold yet its slide transition reads clearly on
    //     device, while IRIS's scale-only transition (also opaque at the
    //     time) did not. Conclusion: slide (a position change) is visually
    //     legible even over an opaque background, because the moving edges
    //     themselves carry the signal — pure scale/fade does not, since the
    //     screen bounds stay fixed and only size/opacity shift subtly.
    //  5. Final approach: slide + scale combined. Slide guarantees
    //     visibility (matches Still's proven, working pattern) and a subtle
    //     scale (0.96f, not Peristyle's dramatic 0.90f/1.1f) adds depth
    //     without reading as "playful" — keeping IRIS's calm, Apple-style
    //     "trusted assistant" identity rather than a wallpaper-app energy.
    const val NAV_ANIM_DURATION_MS  = 300
    // Main-flow (Home/Settings/LocalModels/PermissionManager/VoiceSettings)
    // slide+scale+fade transition bounds. Forward and back navigation use
    // the same scale delta and mirrored slide direction for a consistent,
    // symmetric feel in both directions.
    const val NAV_SCALE_ENTER_FROM  = 0.96f
    const val NAV_SCALE_EXIT_TO     = 0.96f
    // Horizontal slide distance as a divisor of screen width (width / N).
    // Smaller divisor = larger, more dramatic slide. N=4 matches a moderate,
    // clearly-visible-but-not-excessive distance (Still uses width/4 for its
    // push transitions, width/6 for the receding screen).
    const val NAV_SLIDE_ENTER_DIVISOR = 4
    const val NAV_SLIDE_EXIT_DIVISOR  = 6

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

    // --- TTS — MMS (HuggingFace) ---
    // Model: facebook/mms-tts-tur (Meta MMS, only Turkish)
    // Endpoint: https://api-inference.huggingface.co/models/{model}
    // Output: WAV, parsed to PCM for AudioTrack playback
    // API key: BuildConfig.HF_API_KEY
    const val HF_API_ENDPOINT  = "https://api-inference.huggingface.co/models"
    const val MMS_TTS_MODEL    = "facebook/mms-tts-tur"

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
    const val DATABASE_VERSION = 2

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4 — Power Mode / Embedded Shell
    // ─────────────────────────────────────────────────────────────────────────

    // Maximum number of terminal lines kept in memory for the UI.
    // Older lines are dropped from the head (ring-buffer semantics via takeLast).
    const val TERMINAL_MAX_LINES = 500

    // Termux bootstrap is sourced from termux/termux-packages GitHub Releases.
    // Asset naming convention (verified 2025-12):
    //   bootstrap-aarch64.zip  → arm64-v8a
    //   bootstrap-arm.zip      → armeabi-v7a
    //   bootstrap-x86_64.zip   → x86_64
    //   bootstrap-i686.zip     → x86
    //
    // We fetch the latest release tag dynamically via the GitHub Releases API
    // so the URL never needs to be hardcoded or updated manually.
    const val BOOTSTRAP_GITHUB_API =
        "https://api.github.com/repos/termux/termux-packages/releases"

    // Subdirectory inside filesDir where the Termux environment lives.
    //   filesDir/termux/usr/   = TERMUX_PREFIX
    //   filesDir/termux/home/  = TERMUX_HOME
    const val TERMUX_DIR_NAME    = "termux"
    const val TERMUX_PREFIX_NAME = "usr"
    const val TERMUX_HOME_NAME   = "home"

    // Subdirectory inside filesDir used for temporary bootstrap download files.
    const val BOOTSTRAP_DIR_NAME = "bootstrap"

    // Shell security levels (stored as string in DataStore)
    const val SHELL_SECURITY_UNRESTRICTED = "UNRESTRICTED"
    const val SHELL_SECURITY_CONFIRM_EACH = "CONFIRM_EACH"
    const val SHELL_SECURITY_RESTRICTED   = "RESTRICTED"

    // Default shell security level — UNRESTRICTED per Muhofy's explicit decision.
    // One-time warning dialog is shown on first Power Mode activation.
    const val SHELL_SECURITY_DEFAULT = SHELL_SECURITY_UNRESTRICTED

    // Timeout for a single shell command when running in AI tool-fallback mode.
    // User-driven terminal commands have no timeout (session is persistent).
    const val SHELL_TOOL_TIMEOUT_MS = 30_000L

    // Max bytes of stdout/stderr captured per AI shell tool call.
    // Prevents unbounded output from flooding the LLM context.
    const val SHELL_TOOL_OUTPUT_MAX_BYTES = 8 * 1024 // 8 KB
}    
