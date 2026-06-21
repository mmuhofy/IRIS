package com.iris.assistant.ui.navigation

sealed class NavRoute(val route: String) {
    // Onboarding
    data object OnboardingWelcome    : NavRoute("onboarding_welcome")
    data object OnboardingMic        : NavRoute("onboarding_mic")
    data object OnboardingWakeWord   : NavRoute("onboarding_wake_word")
    data object OnboardingDemo       : NavRoute("onboarding_demo")
    data object OnboardingAssistant  : NavRoute("onboarding_assistant")
    data object OnboardingBattery    : NavRoute("onboarding_battery")

    // Main
    data object Home                     : NavRoute("home")
    data object Settings                 : NavRoute("settings")
    data object SettingsModel            : NavRoute("settings_model")
    data object SettingsAppearance       : NavRoute("settings_appearance")
    data object SettingsBackground       : NavRoute("settings_background")
    data object SettingsAutonomy         : NavRoute("settings_autonomy")
    data object SettingsSystem           : NavRoute("settings_system")
    data object SettingsData             : NavRoute("settings_data")
    data object LocalModels              : NavRoute("local_models")
    data object PermissionManager        : NavRoute("permission_manager")
    data object VoiceSettings            : NavRoute("voice_settings")

    // Chat — conversationId is required; 0 = create a new conversation on entry
    data object Chat : NavRoute("chat/{conversationId}") {
        const val ARG = "conversationId"
        fun withId(conversationId: Long) = "chat/$conversationId"
        const val NEW = "chat/0" // shorthand for "open with a new conversation"
    }
}
