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
import com.iris.assistant.ui.settings.PermissionScreen
import com.iris.assistant.ui.settings.SettingsScreen
import com.iris.assistant.ui.settings.VoiceSettingsScreen
import com.iris.assistant.util.Constants

/**
 * ROOT CAUSE (confirmed against Peristyle's Home.kt as a working reference —
 * github.com/Hamza417/Peristyle — not assumed):
 *
 * Every main-flow screen (HomeScreen.kt, SettingsScreen.kt,
 * LocalModelScreen.kt, PermissionScreen.kt, VoiceSettingsScreen.kt) wraps its
 * content in Scaffold(containerColor = MaterialTheme.colorScheme.background).
 * That paints a fully OPAQUE rectangle the instant a destination enters
 * composition. Navigation Compose's AnimatedContent renders the entering and
 * exiting screen in the same z-stack during a transition — an opaque
 * entering Scaffold instantly covers the exiting screen, so scale/fade specs
 * run correctly but have nothing visible to animate through.
 *
 * Peristyle's Home() composable (ui/screens/Home.kt) has NO Scaffold and NO
 * per-screen background paint — it starts directly with
 * Column(Modifier.fillMaxSize()...), relying on a single background source
 * higher in the view hierarchy. We follow the same pattern: the real
 * background color lives ONCE, in the Box wrapping NavHost below. Every
 * screen's Scaffold containerColor (and TopAppBar containerColor, where
 * present) MUST be Color.Transparent for transitions to be visible.
 *
 * IMPORTANT — do not "optimize" this back to opaque per-screen backgrounds.
 * That was tried once already (reverted here) on the reasoning that opaque
 * backgrounds render slightly cheaper than transparent ones. That trade-off
 * is not acceptable on its own: it makes every nav transition in the app
 * invisible, which defeats the entire feature. We additionally moved from a
 * scale+fade-only transition to slide+scale+fade (see mainEnter/mainExit
 * below) after comparing against Muhofy's other app (Still), where slide
 * transitions read clearly even over an opaque Scaffold — confirming that
 * position-based motion (slide) is the visually reliable part of this
 * combination, with scale as a secondary depth cue. If a real, measured
 * jank problem shows up later, fix it without reintroducing opaque
 * per-screen backgrounds or removing slide (e.g. investigate recomposition
 * scope first via Layout Inspector) — do not just flip these back.
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
 * Main app transitions: slide + scale + fade (Apple-style Modern Minimal).
 *
 * Why slide is included (not scale/fade alone): compared against Muhofy's
 * other app (Still — see StillNavHost.kt), whose NoteEditorScreen also uses
 * an opaque Scaffold yet reads clearly during its slide transition. Slide is
 * a position change, so the moving screen edges themselves carry the visual
 * signal regardless of background opacity. Pure scale/fade does not, since
 * the screen bounds stay fixed and only size/opacity shift — a much subtler,
 * easier-to-miss cue. Combining both gives a guaranteed-visible motion
 * (slide, proven by Still) plus added depth (scale) without reading as
 * "playful" — NAV_SCALE_ENTER_FROM is 0.96f, not Peristyle's more dramatic
 * 0.90f/1.1f range, to keep IRIS's calm, Apple-style "trusted assistant"
 * identity rather than a wallpaper-app energy.
 *
 * Forward and back navigation use the same scale delta and mirrored slide
 * direction (forward: enters from right, exits to left — standard
 * "push deeper" hierarchy direction; back: reversed) for a consistent,
 * symmetric feel in both directions.
 */
private fun mainEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> fullWidth / Constants.NAV_SLIDE_ENTER_DIVISOR } +
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale = Constants.NAV_SCALE_ENTER_FROM
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> -fullWidth / Constants.NAV_SLIDE_EXIT_DIVISOR } +
    scaleOut(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        targetScale = Constants.NAV_SCALE_EXIT_TO
    ) + fadeOut(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> -fullWidth / Constants.NAV_SLIDE_EXIT_DIVISOR } +
    scaleIn(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS),
        initialScale = Constants.NAV_SCALE_EXIT_TO
    ) + fadeIn(animationSpec = tween(Constants.NAV_ANIM_DURATION_MS))

private fun mainPopExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(Constants.NAV_ANIM_DURATION_MS)
    ) { fullWidth -> fullWidth / Constants.NAV_SLIDE_ENTER_DIVISOR } +
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
            // Per-destination transitions set explicitly (not relying on
            // NavHost-level defaults) for reliable, predictable behavior.
            composable(
                route              = NavRoute.Home.route,
                enterTransition    = { mainEnter() },
                exitTransition     = { mainExit() },
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
                    onBack                  = { navController.popBackStack() },
                    onOpenLocalModels       = { navController.navigate(NavRoute.LocalModels.route) },
                    onOpenPermissionManager = { navController.navigate(NavRoute.PermissionManager.route) },
                    onOpenVoiceSettings     = { navController.navigate(NavRoute.VoiceSettings.route) }
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
            composable(
                route              = NavRoute.PermissionManager.route,
                enterTransition    = { mainEnter() },
                exitTransition     = { mainExit() },
                popEnterTransition = { mainPopEnter() },
                popExitTransition  = { mainPopExit() }
            ) {
                PermissionScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route              = NavRoute.VoiceSettings.route,
                enterTransition    = { mainEnter() },
                exitTransition     = { mainExit() },
                popEnterTransition = { mainPopEnter() },
                popExitTransition  = { mainPopExit() }
            ) {
                VoiceSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}