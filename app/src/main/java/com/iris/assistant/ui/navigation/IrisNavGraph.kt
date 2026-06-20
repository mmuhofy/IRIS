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
 * ROOT CAUSE FOUND (confirmed against Peristyle's Home.kt as a working
 * reference implementation — github.com/Hamza417/Peristyle, not assumed):
 *
 * Each main-flow screen (HomeScreen.kt, SettingsScreen.kt, LocalModelScreen.kt)
 * wraps its content in Scaffold(containerColor = MaterialTheme.colorScheme.background, ...).
 * That paints a fully OPAQUE rectangle the instant the destination enters
 * composition. Navigation Compose's AnimatedContent renders the entering and
 * exiting screen in the same z-stack during a transition — but because the
 * entering screen's Scaffold is opaque, it instantly covers the exiting
 * screen. Our mainEnter()/mainExit()/mainPopEnter()/mainPopExit() scale+fade
 * specs were running correctly, but had nothing visible to animate through:
 * the new opaque Scaffold simply appears on top, frame one. This is also why
 * only the system's predictive-back gesture preview was visible (it animates
 * the outgoing screen's content as a snapshot, before our own Scaffold paints
 * over it) while our own transitions never were — exactly what Muhofy
 * observed.
 *
 * Peristyle's Home() composable (ui/screens/Home.kt) has NO Scaffold and NO
 * per-screen background paint — it starts directly with
 * Column(Modifier.fillMaxSize()...), relying on a single background source
 * higher in the view hierarchy. We apply the same fix: the real background
 * color now lives ONCE, in the Box wrapping NavHost below. Each screen's
 * Scaffold is kept as-is (snackbarHost etc. still work) but its
 * containerColor must be Color.Transparent — see HomeScreen.kt /
 * SettingsScreen.kt / LocalModelScreen.kt (containerColor changed, nothing
 * else in those files touched).
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

/**
 * Main app transitions.
 *
 * Forward (slide + fade — reliable in Navigation Compose, Apple-style):
 *   Enter: new screen slides in from right while fading in.
 *   Exit:  current screen slides out to left while fading out.
 *
 * Back / predictive-back (scale + fade — confirmed working by Muhofy):
 *   Pop enter: previous screen scales up from NAV_SCALE_ENTER_FROM while fading in.
 *   Pop exit:  current screen scales down to NAV_SCALE_EXIT_TO while fading out.
 */
private fun mainEnter(): EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it / 3 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainExit(): ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 3 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS / 2))

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

    // Single background paint source for the whole nav graph — see root
    // cause note above. NavHost itself stays transparent; this Box behind it
    // is what every screen renders against during transitions.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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