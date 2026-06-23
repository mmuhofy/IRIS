package com.iris.animtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iris.animtest.screens.ScreenA
import com.iris.animtest.screens.ScreenB
import com.iris.animtest.screens.ScreenC
import com.iris.animtest.screens.ScreenD
import com.iris.animtest.transitions.TaskManagerTransitions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "screen_a",
                        enterTransition = { TaskManagerTransitions.enter },
                        exitTransition = { TaskManagerTransitions.exit },
                        popEnterTransition = { TaskManagerTransitions.popEnter },
                        popExitTransition = { TaskManagerTransitions.popExit },
                    ) {
                        composable("screen_a") { ScreenA(navController) }
                        composable("screen_b") { ScreenB(navController) }
                        composable("screen_c") { ScreenC(navController) }
                        composable("screen_d") { ScreenD(navController) }
                    }
                }
            }
        }
    }
}
