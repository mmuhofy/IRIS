package com.iris.assistant.ui

import androidx.compose.runtime.Composable
import com.iris.assistant.ui.theme.ColorSchemeOption
import com.iris.assistant.ui.theme.IrisTheme

// ---------------------------------------------------------------------------
// IrisApp — root composable, called from MainActivity.setContent {}
// Navigation graph will be wired here in Phase 1 (onboarding + home)
// ColorSchemeOption will come from ViewModel/DataStore in Phase 1
// ---------------------------------------------------------------------------
@Composable
fun IrisApp(
    colorSchemeOption: ColorSchemeOption = ColorSchemeOption.LAVENDER
) {
    IrisTheme(colorSchemeOption = colorSchemeOption) {
        // TODO: NavHost (onboarding → home) — Phase 1
    }
}