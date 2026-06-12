package com.iris.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.ui.chat.ChatScreen
import com.iris.assistant.ui.home.HomeScreen
import com.iris.assistant.ui.onboarding.OnboardingBatteryScreen
import com.iris.assistant.ui.onboarding.OnboardingDemoScreen
import com.iris.assistant.ui.onboarding.OnboardingMicScreen
import com.iris.assistant.ui.onboarding.OnboardingWakeWordScreen
import com.iris.assistant.ui.onboarding.OnboardingWelcomeScreen
import com.iris.assistant.ui.settings.SettingsScreen

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        // --- Onboarding ---
        composable(NavRoute.OnboardingWelcome.route) {
            OnboardingWelcomeScreen(
                onNext = { navController.navigate(NavRoute.OnboardingMic.route) }
            )
        }
        composable(NavRoute.OnboardingMic.route) {
            OnboardingMicScreen(
                onNext = { navController.navigate(NavRoute.OnboardingWakeWord.route) }
            )
        }
        composable(NavRoute.OnboardingWakeWord.route) {
            OnboardingWakeWordScreen(
                onNext = { navController.navigate(NavRoute.OnboardingDemo.route) }
            )
        }
        composable(NavRoute.OnboardingDemo.route) {
            OnboardingDemoScreen(
                onNext = { navController.navigate(NavRoute.OnboardingBattery.route) }
            )
        }
        composable(NavRoute.OnboardingBattery.route) {
            OnboardingBatteryScreen(
                onFinish = {
                    navController.navigate(NavRoute.Home.route) {
                        // Clear entire onboarding backstack
                        popUpTo(NavRoute.OnboardingWelcome.route) { inclusive = true }
                    }
                }
            )
        }

        // --- Main ---
        composable(NavRoute.Home.route) {
            HomeScreen(
                onOpenChat     = { navController.navigate(NavRoute.Chat.route) },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) }
            )
        }
        composable(NavRoute.Chat.route) {
            ChatScreen(
                onBack         = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(NavRoute.Settings.route) }
            )
        }
        composable(NavRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}