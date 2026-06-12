package com.iris.assistant.ui.navigation

sealed class NavRoute(val route: String) {
    // Onboarding
    data object OnboardingWelcome    : NavRoute("onboarding_welcome")
    data object OnboardingMic        : NavRoute("onboarding_mic")
    data object OnboardingWakeWord   : NavRoute("onboarding_wake_word")
    data object OnboardingDemo       : NavRoute("onboarding_demo")
    data object OnboardingBattery    : NavRoute("onboarding_battery")

    // Main
    data object Home                 : NavRoute("home")
    data object Chat                 : NavRoute("chat")
    data object Settings             : NavRoute("settings")
}