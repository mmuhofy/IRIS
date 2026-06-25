// app/src/main/java/com/iris/assistant/ui/theme/Theme.kt
package com.iris.assistant.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class IrisColorScheme(
    val primary     : Color,
    val gradientEnd : Color,
    val secondary   : Color,
)

val LocalIrisColorScheme = staticCompositionLocalOf { IrisColorSchemeSlate }

val IrisColorSchemeSlate      = IrisColorScheme(SlatePrimary,      SlateGradient,      SlateSecondary)
val IrisColorSchemeRoseQuartz = IrisColorScheme(RoseQuartzPrimary, RoseQuartzGradient, RoseQuartzSecondary)
val IrisColorSchemeSage       = IrisColorScheme(SagePrimary,       SageGradient,       SageSecondary)
val IrisColorSchemeCobalt     = IrisColorScheme(CobaltPrimary,     CobaltGradient,     CobaltSecondary)
val IrisColorSchemeEmber      = IrisColorScheme(EmberPrimary,      EmberGradient,      EmberSecondary)
val IrisColorSchemeMonochrome = IrisColorScheme(MonochromePrimary, MonochromeGradient, MonochromeSecondary)

enum class ColorSchemeOption {
    SLATE, ROSE_QUARTZ, SAGE, COBALT, EMBER, MONOCHROME;

    fun toIrisColorScheme(): IrisColorScheme = when (this) {
        SLATE       -> IrisColorSchemeSlate
        ROSE_QUARTZ -> IrisColorSchemeRoseQuartz
        SAGE        -> IrisColorSchemeSage
        COBALT      -> IrisColorSchemeCobalt
        EMBER       -> IrisColorSchemeEmber
        MONOCHROME  -> IrisColorSchemeMonochrome
    }
}

private fun buildDarkColorScheme(iris: IrisColorScheme) = darkColorScheme(
    primary          = iris.primary,
    secondary        = iris.secondary,
    tertiary         = iris.gradientEnd,
    background       = ColorBackground,
    surface          = ColorSurface,
    error            = ColorError,
    onPrimary        = ColorBackground,
    onSecondary      = ColorBackground,
    onTertiary       = ColorBackground,
    onBackground     = ColorTextPrimary,
    onSurface        = ColorTextPrimary,
    onError          = ColorBackground,
    surfaceVariant   = ColorSurfaceHigh,
    onSurfaceVariant = ColorTextSecondary,
)

// ---------------------------------------------------------------------------
// Normal theme — opaque Surface. Use for all regular screens.
//
// useMaterialYou behaviour (Android 12+ only):
//   background/surface come from the system DynamicColorScheme so the app
//   feels at home on the device, BUT primary/secondary/tertiary are always
//   the chosen IrisColorScheme values — Iris Core and branded elements are
//   never overridden by the wallpaper colour.
// ---------------------------------------------------------------------------
@Composable
fun IrisTheme(
    colorSchemeOption : ColorSchemeOption = ColorSchemeOption.SLATE,
    fontFamily        : AppFont           = AppFont.Inter,
    useMaterialYou    : Boolean           = false,
    content           : @Composable () -> Unit,
) {
    val irisColors = colorSchemeOption.toIrisColorScheme()
    val context    = LocalContext.current

    val m3Scheme = if (useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // UNTESTED — verify before use
        dynamicDarkColorScheme(context).copy(
            primary          = irisColors.primary,
            secondary        = irisColors.secondary,
            tertiary         = irisColors.gradientEnd,
            onPrimary        = ColorBackground,
            onSecondary      = ColorBackground,
            onTertiary       = ColorBackground,
        )
    } else {
        buildDarkColorScheme(irisColors)
    }

    CompositionLocalProvider(LocalIrisColorScheme provides irisColors) {
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
// IrisTheme's Surface paints the window background, defeating windowIsTranslucent.
// ---------------------------------------------------------------------------
@Composable
fun IrisThemeTransparent(
    colorSchemeOption : ColorSchemeOption = ColorSchemeOption.SLATE,
    fontFamily        : AppFont           = AppFont.Inter,
    useMaterialYou    : Boolean           = false,
    content           : @Composable () -> Unit,
) {
    val irisColors = colorSchemeOption.toIrisColorScheme()
    val context    = LocalContext.current

    val m3Scheme = if (useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // UNTESTED — verify before use
        dynamicDarkColorScheme(context).copy(
            primary   = irisColors.primary,
            secondary = irisColors.secondary,
            tertiary  = irisColors.gradientEnd,
        )
    } else {
        buildDarkColorScheme(irisColors)
    }

    CompositionLocalProvider(LocalIrisColorScheme provides irisColors) {
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