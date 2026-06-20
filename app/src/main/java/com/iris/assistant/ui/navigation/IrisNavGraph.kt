package com.iris.assistant.ui.navigation

import android.os.Build
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
 * Predictive Back (Android 13+ / TIRAMISU) registration.
 *
 * UNTESTED — verify before use. android.window.OnBackInvokedCallback /
 * OnBackInvokedDispatcher API surface confirmed to exist since API 33 per
 * official Android docs, but this exact registration pattern combined with
 * Navigation Compose 2.9.0's NavHost has not been runtime-verified in this
 * project. Test on a physical/emulated API 33+ device before relying on it.
 */
@Composable
private fun PredictiveBackHandler(navController: NavHostController) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    DisposableEffect(navController) {
        val activity = context as? ComponentActivity ?: return@DisposableEffect onDispose { }

        val callback = android.window.OnBackInvokedCallback {
            if (!navController.popBackStack()) {
                activity.finish()
            }
        }

        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )

        onDispose {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}

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
 * Forward navigation: incoming screen scales up from 0.95 -> 1f while fading in;
 * outgoing screen scales down to 0.92f while fading out (recedes "behind" the new screen).
 * Back navigation mirrors this in reverse.
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

/** Routes that belong to the onboarding flow (slide+fade transitions). */
private val onboardingRoutes = setOf(
    NavRoute.OnboardingWelcome.route,
    NavRoute.OnboardingMic.route,
    NavRoute.OnboardingWakeWord.route,
    NavRoute.OnboardingDemo.route,
    NavRoute.OnboardingAssistant.route
    // Note: OnboardingBattery intentionally excluded — it always navigates
    // forward into Home (a "main" route), so its exit transition must match
    // the main-flow scale+fade rather than onboarding's slide.
)

private fun NavBackStackEntry.routeOrEmpty(): String = destination.route.orEmpty()

@Composable
fun IrisNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoute.OnboardingWelcome.route
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    PredictiveBackHandler(navController)

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