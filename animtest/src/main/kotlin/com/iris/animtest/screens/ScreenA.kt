package com.iris.animtest.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun ScreenA(navController: NavController, modifier: Modifier = Modifier) {
    TestScreen(
        color = Colors.A,
        label = "Screen A",
        modifier = modifier,
        onNext = { navController.navigate("screen_b") },
        onPrev = null,
    )
}

@Composable
fun ScreenB(navController: NavController, modifier: Modifier = Modifier) {
    TestScreen(
        color = Colors.B,
        label = "Screen B",
        modifier = modifier,
        onNext = { navController.navigate("screen_c") },
        onPrev = { navController.popBackStack() },
    )
}

@Composable
fun ScreenC(navController: NavController, modifier: Modifier = Modifier) {
    TestScreen(
        color = Colors.C,
        label = "Screen C",
        modifier = modifier,
        onNext = { navController.navigate("screen_d") },
        onPrev = { navController.popBackStack() },
    )
}

@Composable
fun ScreenD(navController: NavController, modifier: Modifier = Modifier) {
    TestScreen(
        color = Colors.D,
        label = "Screen D",
        modifier = modifier,
        onNext = null,
        onPrev = { navController.popBackStack() },
    )
}
