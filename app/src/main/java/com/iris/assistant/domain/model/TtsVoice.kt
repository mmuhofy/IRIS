package com.iris.assistant.domain.model

/**
 * Gemini TTS prebuilt voice options.
 * Source: https://ai.google.dev/gemini-api/docs/speech-generation#voices
 * All voices support Turkish (language auto-detected from input text).
 *
 * [apiName]       — exact string sent to the API in prebuiltVoiceConfig.voiceName
 * [displayName]   — Turkish label shown in Settings UI
 * [description]   — short character description in Turkish
 */
enum class TtsVoice(
    val apiName    : String,
    val displayName: String,
    val description: String
) {
    AOEDE  ("Aoede",   "Aoede",   "Akıcı, doğal"),       // default
    KORE   ("Kore",    "Kore",    "Kararlı, net"),
    PUCK   ("Puck",    "Puck",    "Enerjik, neşeli"),
    CHARON ("Charon",  "Charon",  "Derin, otoriter"),
    FENRIR ("Fenrir",  "Fenrir",  "Güçlü, ciddi"),
    LEDA   ("Leda",    "Leda",    "Yumuşak, samimi"),
    ORUS   ("Orus",    "Orus",    "Sakin, dengeli"),
    SULAFAT("Sulafat", "Sulafat", "Sıcak, arkadaşça");

    companion object {
        val DEFAULT = AOEDE

        /** Safe parse — falls back to DEFAULT if stored value is unrecognized. */
        fun fromApiName(name: String): TtsVoice =
            entries.firstOrNull { it.apiName == name } ?: DEFAULT
    }
}