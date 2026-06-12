package com.iris.assistant.ui

import androidx.compose.runtime.Composable
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.IrisTheme

// ---------------------------------------------------------------------------
// IrisApp — root composable, called from MainActivity.setContent {}
// Navigation graph will be wired here in Phase 1 (onboarding + home)
// ColorSchemeOption will come from ViewModel/DataStore in Phase 1
// ---------------------------------------------------------------------------
import androidx.navigation.compose.rememberNavController
import com.iris.assistant.ui.navigation.IrisNavGraph

@Composable
fun IrisApp(
    colorSchemeOption: ColorSchemeOption = ColorSchemeOption.LAVENDER
) {
    val navController = rememberNavController()
    IrisTheme(colorSchemeOption = colorSchemeOption) {
        IrisNavGraph(navController = navController)
    }
}