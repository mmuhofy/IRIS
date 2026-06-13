package com.iris.assistant.data.local.datastore

import com.iris.assistant.domain.model.TtsVoice
import com.iris.assistant.ui.theme.ColorSchemeOption

/**
 * All user-configurable preferences stored in DataStore.
 */
data class UserPreferences(
    val colorScheme         : ColorSchemeOption = ColorSchemeOption.LAVENDER,
    val backgroundListening : Boolean           = true,
    val onboardingCompleted : Boolean           = false,
    val ttsVoice            : TtsVoice          = TtsVoice.DEFAULT
)