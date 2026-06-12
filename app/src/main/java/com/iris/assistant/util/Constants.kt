package com.iris.assistant.util

object Constants {

    // --- UI ---
    const val BUTTON_HEIGHT        = 52
    const val CARD_CORNER_RADIUS   = 18
    const val BUTTON_CORNER_RADIUS = 16
    const val IRIS_CORE_SIZE       = 240

    // --- User ---
    const val USER_NAME = "Muhofy"

    // --- STT ---
    const val GROQ_STT_ENDPOINT    = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_STT_MODEL       = "whisper-large-v3"
    const val GROQ_STT_LANGUAGE    = "tr"

    // --- LLM ---
    // VERIFY current model string at https://ai.google.dev/gemini-api/docs/models before use
    const val GEMINI_MODEL         = "gemini-2.5-flash"  // TODO: confirm at implementation time
    const val GROQ_LLM_MODEL       = "llama-3.3-70b-versatile" // TODO: confirm at implementation time

    // --- TTS ---
    const val EDGE_TTS_VOICE_MALE   = "tr-TR-AhmetNeural"
    const val EDGE_TTS_VOICE_FEMALE = "tr-TR-EmelNeural"

    // --- VAD ---
    const val VAD_SILENCE_THRESHOLD_MS = 1500L

    // --- Notifications ---
    const val NOTIFICATION_CHANNEL_ID_WAKE   = "iris_wake_word_channel"
    const val NOTIFICATION_CHANNEL_NAME_WAKE = "IRIS Arka Plan"

    // --- Room ---
    const val DATABASE_NAME = "iris_database"
    const val DATABASE_VERSION = 1
}