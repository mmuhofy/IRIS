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
    const val GROQ_STT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val GROQ_STT_MODEL    = "whisper-large-v3"
    const val GROQ_STT_LANGUAGE = "tr"

    // --- LLM ---
    // Verified: https://ai.google.dev/gemini-api/docs/models — June 2026
    const val GEMINI_MODEL    = "gemini-3.5-flash"
    const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GROQ_LLM_MODEL  = "llama-3.3-70b-versatile" // TODO: confirm at implementation time

    // --- TTS — Gemini ---
    // Model: gemini-3.1-flash-tts-preview (current stable TTS model per official docs, 2026-05-18)
    // Endpoint: /v1beta/models/{model}:generateContent — same as standard generateContent API
    // Output: PCM 16-bit signed, 24000 Hz, mono — base64 in candidates[0].content.parts[0].inlineData.data
    // Language: auto-detected from input text — Turkish (tr) confirmed supported
    // Verified against: https://ai.google.dev/gemini-api/docs/speech-generation
    const val GEMINI_TTS_MODEL    = "gemini-3.1-flash-tts-preview"
    const val GEMINI_TTS_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GEMINI_TTS_VOICE    = "Aoede" // Breezy tone — 30 voices available, see docs for full list
    const val GEMINI_TTS_SAMPLE_RATE = 24000   // Hz, per Gemini TTS spec
    const val GEMINI_TTS_MIN_BUFFER_SIZE = 8192 // bytes, safety floor for AudioTrack buffer

    // Retry config — Gemini TTS occasionally returns HTTP 500 with text tokens instead of audio
    const val GEMINI_TTS_MAX_RETRIES   = 3
    const val GEMINI_TTS_RETRY_DELAY_MS = 1000L

    // --- VAD ---
    const val VAD_SILENCE_THRESHOLD_MS = 1500L

    // --- Notifications ---
    const val NOTIFICATION_CHANNEL_ID_WAKE   = "iris_wake_word_channel"
    const val NOTIFICATION_CHANNEL_NAME_WAKE = "IRIS Arka Plan"

    // --- Room ---
    const val DATABASE_NAME    = "iris_database"
    const val DATABASE_VERSION = 1

    // --- DataStore ---
    const val DATASTORE_NAME = "iris_preferences"
}