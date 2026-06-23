package com.iris.animtest.transitions

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object TaskManagerTransitions {
    val popEnter = fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
    val popExit = fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
    val enter = fadeIn(tween(250)) + slideInHorizontally { it / 2 }
    val exit = fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
}
