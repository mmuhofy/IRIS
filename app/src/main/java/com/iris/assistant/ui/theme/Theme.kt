package com.iris.assistant.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val LocalIrisColorScheme = staticCompositionLocalOf { SlateDark }

// ---------------------------------------------------------------------------
// Scheme definition — each entry holds pre-built dark + light IrisColorScheme
// ---------------------------------------------------------------------------

enum class ColorSchemeOption(
    val dark : IrisColorScheme,
    val light: IrisColorScheme,
) {
    SLATE      (SlateDark,       SlateLight),
    ROSE_QUARTZ(RoseQuartzDark,  RoseQuartzLight),
    SAGE       (SageDark,        SageLight),
    COBALT     (CobaltDark,      CobaltLight),
    EMBER      (EmberDark,       EmberLight),
    MONOCHROME (MonochromeDark,  MonochromeLight);

    // Preview colors used by appearance settings theme cards
    val seedPrimary  : Color get() = dark.primary
    val seedGradient : Color get() = dark.gradientEnd
    val seedSecondary: Color get() = dark.secondary
}

// ---------------------------------------------------------------------------
// Normal theme — opaque Surface. Use for all regular screens.
// ---------------------------------------------------------------------------

@Composable
fun IrisTheme(
    colorSchemeOption : ColorSchemeOption = ColorSchemeOption.COBALT,
    fontFamily        : AppFont           = AppFont.Inter,
    isDark            : Boolean           = true,
    content           : @Composable () -> Unit,
) {
    val fullScheme = if (isDark) colorSchemeOption.dark else colorSchemeOption.light
    val m3Scheme   = fullScheme.toMaterial3()

    CompositionLocalProvider(LocalIrisColorScheme provides fullScheme) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography  = fontFamily.toTypography(),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Transparent theme — NO Surface. Use ONLY for AssistantActivity overlay.
// ---------------------------------------------------------------------------

@Composable
fun IrisThemeTransparent(
    colorSchemeOption : ColorSchemeOption = ColorSchemeOption.COBALT,
    fontFamily        : AppFont           = AppFont.Inter,
    isDark            : Boolean           = true,
    content           : @Composable () -> Unit,
) {
    val fullScheme = if (isDark) colorSchemeOption.dark else colorSchemeOption.light
    val m3Scheme   = fullScheme.toMaterial3()

    CompositionLocalProvider(LocalIrisColorScheme provides fullScheme) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography  = fontFamily.toTypography(),
        ) {
            content()
        }
    }
}

object IrisTheme {
    val colors: IrisColorScheme
        @Composable get() = LocalIrisColorScheme.current
}
