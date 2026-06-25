package com.iris.assistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class IrisColorScheme(
    val primary           : Color,
    val onPrimary         : Color,
    val primaryContainer  : Color,
    val onPrimaryContainer: Color,
    val gradientEnd       : Color,
    val onGradientEnd     : Color,
    val secondary         : Color,
    val onSecondary       : Color,
    val secondaryContainer : Color,
    val onSecondaryContainer: Color,
    val surface           : Color,
    val onSurface         : Color,
    val surfaceVariant    : Color,
    val onSurfaceVariant  : Color,
    val background        : Color,
    val onBackground      : Color,
    val isDark            : Boolean,
) {
    fun toMaterial3(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary              = primary,
            onPrimary            = onPrimary,
            primaryContainer     = primaryContainer,
            onPrimaryContainer   = onPrimaryContainer,
            secondary            = secondary,
            onSecondary          = onSecondary,
            secondaryContainer   = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary             = gradientEnd,
            onTertiary           = onGradientEnd,
            surface              = surface,
            onSurface            = onSurface,
            surfaceVariant       = surfaceVariant,
            onSurfaceVariant     = onSurfaceVariant,
            background           = background,
            onBackground         = onBackground,
        )
    }
}
