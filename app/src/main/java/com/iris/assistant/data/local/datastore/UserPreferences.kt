package com.iris.assistant.data.local.datastore

import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.model.TtsProviderType
import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.util.Constants

data class UserPreferences(
    val colorScheme         : ColorSchemeOption = ColorSchemeOption.LAVENDER,
    val backgroundListening : Boolean           = true,
    val onboardingCompleted : Boolean           = false,
    val ttsVoice            : TtsVoice          = TtsVoice.DEFAULT,
    val userName            : String            = Constants.USER_NAME,
    val llmProvider         : String            = Constants.LLM_PROVIDER_GEMINI,
    val llmModel            : String            = Constants.GEMINI_MODEL,
    val autonomyLevel       : AutonomyLevel     = AutonomyLevel.SAFE,
    val localModelName      : String            = "",
    val localModelPath      : String            = "",
    val ttsProvider         : TtsProviderType   = TtsProviderType.GEMINI
)
