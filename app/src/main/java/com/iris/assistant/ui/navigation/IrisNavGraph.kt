package com.iris.assistant.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.ui.chat.ChatScreen
import com.iris.assistant.ui.home.HomeScreen
import com.iris.assistant.ui.onboarding.OnboardingAssistantScreen
import com.iris.assistant.ui.onboarding.OnboardingBatteryScreen
import com.iris.assistant.ui.onboarding.OnboardingDemoScreen
import com.iris.assistant.ui.onboarding.OnboardingMicScreen
import com.iris.assistant.ui.onboarding.OnboardingViewModel
import com.iris.assistant.ui.onboarding.OnboardingWakeWordScreen
import com.iris.assistant.ui.onboarding.OnboardingWelcomeScreen
import com.iris.assistant.ui.settings.LocalModelScreen
import com.iris.assistant.ui.settings.SettingsScreen

private const val ANIM_DURATION = 300

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        // --- Onboarding ---
        composable(
            route          = NavRoute.OnboardingWelcome.route,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) + slideInHorizontally(tween(ANIM_DURATION)) },
            exitTransition  = { fadeOut(tween(ANIM_DURATION)) + slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } }
        ) {
            val userName by onboardingViewModel.userName.collectAsStateWithLifecycle()
            OnboardingWelcomeScreen(
                userName         = userName,
                onUserNameChange  = onboardingViewModel::setUserName,
                onNext            = { navController.navigate(NavRoute.OnboardingMic.route) }
            )
        }
        composable(
            route            = NavRoute.OnboardingMic.route,
            enterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION)) }
        ) {
            OnboardingMicScreen(
                onNext   = { navController.navigate(NavRoute.OnboardingWakeWord.route) },
                onBack   = { navController.popBackStack() }
            )
        }
        composable(
            route            = NavRoute.OnboardingWakeWord.route,
            enterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION)) }
        ) {
            OnboardingWakeWordScreen(
                onNext = { navController.navigate(NavRoute.OnboardingDemo.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route            = NavRoute.OnboardingDemo.route,
            enterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION)) }
        ) {
            OnboardingDemoScreen(
                onNext = { navController.navigate(NavRoute.OnboardingAssistant.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route            = NavRoute.OnboardingAssistant.route,
            enterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION)) }
        ) {
            OnboardingAssistantScreen(
                onNext = { navController.navigate(NavRoute.OnboardingBattery.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route            = NavRoute.OnboardingBattery.route,
            enterTransition  = { slideInHorizontally(tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION)) },
            exitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } + fadeIn(tween(ANIM_DURATION)) },
            popExitTransition   = { slideOutHorizontally(tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION)) }
        ) {
            OnboardingBatteryScreen(
                onFinish = {
                    navController.navigate(NavRoute.Home.route) {
                        popUpTo(NavRoute.OnboardingWelcome.route) { inclusive = true }
                    }
                },
                onBack   = { navController.popBackStack() },
                viewModel = onboardingViewModel
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
                onBack            = { navController.popBackStack() },
                onOpenLocalModels = { navController.navigate(NavRoute.LocalModels.route) }
            )
        }
        composable(NavRoute.LocalModels.route) {
            LocalModelScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
