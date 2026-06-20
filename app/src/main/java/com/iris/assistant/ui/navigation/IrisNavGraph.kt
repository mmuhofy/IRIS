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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * IMPORTANT — Predictive Back, corrected (see commit history for prior
 * mistake): we previously added a manual PredictiveBackHandler() composable
 * here that registered its own android.window.OnBackInvokedCallback at the
 * NavGraph level.
 *
 * That was WRONG and has been removed. Per official Android docs
 * (developer.android.com/codelabs/predictive-back):
 * "If your app intercepts the back event with BackHandler,
 * PredictiveBackHandler, OnBackPressedCallback or OnBackInvokedCallback at
 * the root activity (e.g. MainActivity.kt) [or, as we were doing, in the nav
 * graph], your users will not see the predictive back-to-home animation."
 *
 * Navigation Compose 2.9.0 (confirmed in gradle/libs.versions.toml) already
 * auto-applies predictive back support to enterTransition/exitTransition/
 * popEnterTransition/popExitTransition as soon as
 * android:enableOnBackInvokedCallback="true" is set in AndroidManifest.xml
 * (already done — see app/src/main/AndroidManifest.xml). No manual callback
 * registration is needed or wanted. The manual callback we added was
 * fighting Navigation Compose's own back handling, which is the most likely
 * cause of the abrupt ("küt") transition Muhofy reported — confirmed present
 * on both forward and back navigation, on an API 33+ device.
 */

/**
 * Onboarding transitions: horizontal slide + fade.
 * Used for sequential step-by-step onboarding flow where forward/backward
 * spatial direction reinforces progress through the flow.
 */
private fun onboardingEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it / 4 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 4 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 4 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun onboardingPopExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it / 4 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

/**
 * Main app transitions: scale + fade (Apple-style Modern Minimal).
 * Inspired by Peristyle's scaleIntoContainer/scaleOutOfContainer pattern,
 * tuned to this project's animation rules (200-300ms, no bounce/elastic).
 *
 * Forward navigation: incoming screen scales up from NAV_SCALE_ENTER_FROM -> 1f
 * while fading in; outgoing screen scales down to NAV_SCALE_EXIT_TO while
 * fading out (recedes "behind" the new screen). Back navigation mirrors this
 * in reverse. Scale delta widened from an initial 0.95f/0.92f attempt — per
 * Muhofy's on-device testing, that delta was "barely visible, sometimes
 * nothing." Current values match Peristyle's perceptible range.
 */
private fun mainEnter(): EnterTransition =
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainExit(): ExitTransition =
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale = Constants.NAV_SCALE_EXIT_TO
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopEnter(): EnterTransition =
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale = Constants.NAV_SCALE_EXIT_TO
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopExit(): ExitTransition =
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        // Global defaults — applied whenever a composable() below does not
        // override enter/exit/popEnter/popExit itself. Per Navigation Compose
        // docs: a destination returning null for a transition causes it to
        // fall back to the parent (NavHost-level) transition. We define
        // per-destination transitions explicitly below instead of relying on
        // this fallback, so these act as a safety net only.
        enterTransition    = { mainEnter() },
        exitTransition      = { mainExit() },
        popEnterTransition  = { mainPopEnter() },
        popExitTransition   = { mainPopExit() }
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
        // No per-destination transition overrides here: these three routes
        // rely on the NavHost-level scale+fade defaults declared above.
        composable(NavRoute.Home.route) {
            HomeScreen(
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