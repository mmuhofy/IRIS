package com.iris.animtest.transitions

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

data class TransitionSet(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

enum class TransitionPreset(
    val label: String,
    val get: TransitionSet,
) {
    TaskManager(
        label = "TaskManager (half slide + fade)",
        get = TransitionSet(
            enter = fadeIn(tween(250)) + slideInHorizontally { it / 2 },
            exit = fadeOut(tween(200)) + slideOutHorizontally { -it / 2 },
            popEnter = fadeIn(tween(250)) + slideInHorizontally { -it / 2 },
            popExit = fadeOut(tween(200)) + slideOutHorizontally { it / 2 },
        ),
    ),
    FullSlide(
        label = "Full Slide (no fade)",
        get = TransitionSet(
            enter = slideInHorizontally(tween(400)) { it },
            exit = slideOutHorizontally(tween(400)) { -it },
            popEnter = slideInHorizontally(tween(400)) { -it },
            popExit = slideOutHorizontally(tween(400)) { it },
        ),
    ),
    HalfSlide(
        label = "Half Slide (no fade)",
        get = TransitionSet(
            enter = slideInHorizontally(tween(400)) { it / 2 },
            exit = slideOutHorizontally(tween(400)) { -it / 2 },
            popEnter = slideInHorizontally(tween(400)) { -it / 2 },
            popExit = slideOutHorizontally(tween(400)) { it / 2 },
        ),
    ),
    Crossfade(
        label = "Crossfade only",
        get = TransitionSet(
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(300)),
            popEnter = fadeIn(tween(400)),
            popExit = fadeOut(tween(300)),
        ),
    ),
    SlideUp(
        label = "Slide Up (vertical)",
        get = TransitionSet(
            enter = slideInVertically(tween(400)) { it },
            exit = slideOutVertically(tween(400)) { -it },
            popEnter = slideInVertically(tween(400)) { -it },
            popExit = slideOutVertically(tween(400)) { it },
        ),
    ),
}
