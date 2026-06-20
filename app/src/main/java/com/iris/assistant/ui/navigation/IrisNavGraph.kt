package com.iris.assistant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.iris.assistant.util.Constants

/**
 * Background Box wraps NavHost so there is a single stable background layer.
 * Screens now use opaque Scaffold backgrounds for performance; during
 * navigation the fade animation handles the visual transition.
 */

/**
 * Onboarding transitions: horizontal slide + fade.
 * Used for sequential step-by-step onboarding flow where forward/backward
 * spatial direction reinforces progress through the flow.
 */
private fun onboardingEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainEnter(): EnterTransition =
    fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainExit(): ExitTransition =
    fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopEnter(): EnterTransition =
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopExit(): ExitTransition =
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale = Constants.NAV_SCALE_EXIT_TO
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController    = navController,
            startDestination = startDestination
        ) {
            // --- Onboarding ---
            composable(
                route             = NavRoute.OnboardingWelcome.route,
                enterTransition   = { onboardingEnter() },
                exitTransition    = { onboardingExit() }
            ) {
                val userName by onboardingViewModel.userName.collectAsStateWithLifecycle()
                OnboardingWelcomeScreen(
                    userName         = userName,
                    onUserNameChange  = onboardingViewModel::setUserName,
                    onNext            = { navController.navigate(NavRoute.OnboardingMic.route) }
                )
            }
            composable(
                route              = NavRoute.OnboardingMic.route,
                enterTransition    = { onboardingEnter() },
                exitTransition     = { onboardingExit() },
                popEnterTransition = { onboardingPopEnter() },
                popExitTransition  = { onboardingPopExit() }
            ) {
                OnboardingMicScreen(
                    onNext = { navController.navigate(NavRoute.OnboardingWakeWord.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route              = NavRoute.OnboardingWakeWord.route,
                enterTransition    = { onboardingEnter() },
                exitTransition     = { onboardingExit() },
                popEnterTransition = { onboardingPopEnter() },
                popExitTransition  = { onboardingPopExit() }
            ) {
                OnboardingWakeWordScreen(
                    onNext = { navController.navigate(NavRoute.OnboardingDemo.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route              = NavRoute.OnboardingDemo.route,
                enterTransition    = { onboardingEnter() },
                exitTransition     = { onboardingExit() },
                popEnterTransition = { onboardingPopEnter() },
                popExitTransition  = { onboardingPopExit() }
            ) {
                OnboardingDemoScreen(
                    onNext = { navController.navigate(NavRoute.OnboardingAssistant.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route              = NavRoute.OnboardingAssistant.route,
                enterTransition    = { onboardingEnter() },
                exitTransition     = { onboardingExit() },
                popEnterTransition = { onboardingPopEnter() },
                popExitTransition  = { onboardingPopExit() }
            ) {
                OnboardingAssistantScreen(
                    onNext = { navController.navigate(NavRoute.OnboardingBattery.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route              = NavRoute.OnboardingBattery.route,
                enterTransition    = { onboardingEnter() },
                // Exit uses main scale+fade since the only forward destination
                // from here is Home (a main-flow route).
                exitTransition     = { mainExit() },
                popEnterTransition = { onboardingPopEnter() },
                popExitTransition  = { onboardingPopExit() }
            ) {
                OnboardingBatteryScreen(
                    onFinish = {
                        navController.navigate(NavRoute.Home.route) {
                            popUpTo(NavRoute.OnboardingWelcome.route) { inclusive = true }
                        }
                    },
                    onBack    = { navController.popBackStack() },
                    viewModel = onboardingViewModel
                )
            }

            // --- Main ---
            // Per-destination transitions set explicitly for reliable behavior.
            composable(
                route             = NavRoute.Home.route,
                enterTransition   = { mainEnter() },
                exitTransition    = { mainExit() },
                popEnterTransition = { mainPopEnter() },
                popExitTransition  = { mainPopExit() }
            ) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(NavRoute.Settings.route) }
                )
            }
            composable(
                route              = NavRoute.Settings.route,
                enterTransition    = { mainEnter() },
                exitTransition     = { mainExit() },
                popEnterTransition = { mainPopEnter() },
                popExitTransition  = { mainPopExit() }
            ) {
                SettingsScreen(
                    onBack            = { navController.popBackStack() },
                    onOpenLocalModels = { navController.navigate(NavRoute.LocalModels.route) }
                )
            }
            composable(
                route              = NavRoute.LocalModels.route,
                enterTransition    = { mainEnter() },
                exitTransition     = { mainExit() },
                popEnterTransition = { mainPopEnter() },
                popExitTransition  = { mainPopExit() }
            ) {
                LocalModelScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}