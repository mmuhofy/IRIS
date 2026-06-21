package com.iris.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// IrisColorScheme — holds primary, gradientEnd, secondary for each scheme
// ---------------------------------------------------------------------------
@Immutable
data class IrisColorScheme(
    val primary     : Color,
    val gradientEnd : Color,
    val secondary   : Color
)

val LocalIrisColorScheme = staticCompositionLocalOf {
    IrisColorSchemeLavender // default
}

// ---------------------------------------------------------------------------
// Predefined schemes
// ---------------------------------------------------------------------------
val IrisColorSchemeLavender    = IrisColorScheme(LavenderPrimary,    LavenderGradient,    LavenderSecondary)
val IrisColorSchemeSunset      = IrisColorScheme(SunsetPrimary,      SunsetGradient,      SunsetSecondary)
val IrisColorSchemeOcean       = IrisColorScheme(OceanPrimary,       OceanGradient,       OceanSecondary)
val IrisColorSchemeForest      = IrisColorScheme(ForestPrimary,      ForestGradient,      ForestSecondary)
val IrisColorSchemeRose        = IrisColorScheme(RosePrimary,        RoseGradient,        RoseSecondary)
val IrisColorSchemeMonochrome  = IrisColorScheme(MonochromePrimary,  MonochromeGradient,  MonochromeSecondary)

// ---------------------------------------------------------------------------
// ColorSchemeOption enum — used in Settings + DataStore
// ---------------------------------------------------------------------------
enum class ColorSchemeOption {
    LAVENDER, SUNSET, OCEAN, FOREST, ROSE, MONOCHROME;

    fun toIrisColorScheme(): IrisColorScheme = when (this) {
        LAVENDER   -> IrisColorSchemeLavender
        SUNSET     -> IrisColorSchemeSunset
        OCEAN      -> IrisColorSchemeOcean
        FOREST     -> IrisColorSchemeForest
        ROSE       -> IrisColorSchemeRose
        MONOCHROME -> IrisColorSchemeMonochrome
    }
}

// ---------------------------------------------------------------------------
// Material3 dark color scheme — wired to active IrisColorScheme
// ---------------------------------------------------------------------------
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
    surfaceVariant   = ColorSurface,
    onSurfaceVariant = ColorTextSecondary,
)

// ---------------------------------------------------------------------------
// IrisTheme — root composable, wrap entire app with this
// ---------------------------------------------------------------------------
@Composable
fun IrisTheme(
    colorSchemeOption: ColorSchemeOption = ColorSchemeOption.LAVENDER,
    fontFamily: AppFont = AppFont.Inter,
    content: @Composable () -> Unit
) {
    val irisColors = colorSchemeOption.toIrisColorScheme()

    CompositionLocalProvider(LocalIrisColorScheme provides irisColors) {
        MaterialTheme(
            colorScheme = buildDarkColorScheme(irisColors),
            typography  = fontFamily.toTypography(),
            content     = content
        )
    }
}

// ---------------------------------------------------------------------------
// Convenience accessor — use in composables: IrisTheme.colors.primary
// ---------------------------------------------------------------------------
object IrisTheme {
    val colors: IrisColorScheme
        @Composable get() = LocalIrisColorScheme.current
}