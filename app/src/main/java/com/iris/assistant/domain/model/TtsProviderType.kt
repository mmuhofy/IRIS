package com.iris.assistant.domain.model

enum class TtsProviderType(
    val displayName: String,
    val description: String
) {
    GEMINI("Gemini TTS", "Google Gemini, yüksek kalite, 8 ses"),
    MMS("MMS-TTS", "Meta MMS, ücretsiz HF API, sadece Türkçe"),
    ANDROID("Android TTS", "Cihaz içi, offline, düşük kalite");

    companion object {
        const val KEY_GEMINI  = "gemini_tts"
        const val KEY_MMS     = "mms_tts"
        const val KEY_ANDROID = "android_tts"

        fun fromKey(key: String): TtsProviderType = when (key) {
            KEY_GEMINI  -> GEMINI
            KEY_MMS     -> MMS
            KEY_ANDROID -> ANDROID
            else        -> GEMINI
        }

        fun keyOf(type: TtsProviderType): String = when (type) {
            GEMINI  -> KEY_GEMINI
            MMS     -> KEY_MMS
            ANDROID -> KEY_ANDROID
        }
    }
}
