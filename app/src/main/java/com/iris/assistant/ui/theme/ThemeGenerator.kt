package com.iris.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.*

// ---------------------------------------------------------------------------
// Color math utilities
// ---------------------------------------------------------------------------

private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val h = when {
        delta == 0f      -> 0f
        max == r         -> 60f * (((g - b) / delta) % 6f)
        max == g         -> 60f * (((b - r) / delta) + 2f)
        else             -> 60f * (((r - g) / delta) + 4f)
    }
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

    return floatArrayOf(((h + 360f) % 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun Color.Companion.fromHsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when {
        h < 60f   -> Triple(c, x, 0f)
        h < 120f  -> Triple(x, c, 0f)
        h < 180f  -> Triple(0f, c, x)
        h < 240f  -> Triple(0f, x, c)
        h < 300f  -> Triple(x, 0f, c)
        else      -> Triple(c, 0f, x)
    }

    return Color(
        red   = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue  = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private fun Color.relativeLuminance(): Double {
    fun linearize(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
}

private fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.relativeLuminance()
    val l2 = b.relativeLuminance()
    val lighter = maxOf(l1, l2)
    val darker  = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun contrastFloor(bg: Color, light: Color, dark: Color): Color {
    val cLight = contrastRatio(bg, light)
    val cDark  = contrastRatio(bg, dark)
    return if (cLight >= cDark) light else dark
}

private fun Color.desaturate(amount: Float): Color {
    val (h, s, l) = toHsl()
    return Color.fromHsl(h, s * (1f - amount.coerceIn(0f, 1f)), l)
}

private fun Color.adjustLightness(delta: Float): Color {
    val (h, s, l) = toHsl()
    return Color.fromHsl(h, s, (l + delta).coerceIn(0f, 1f))
}

private fun Color.hueShift(degrees: Float): Color {
    val (h, s, l) = toHsl()
    return Color.fromHsl(((h + degrees + 360f) % 360f), s, l)
}

private fun Color.hueOf(): Float = toHsl()[0]

// ---------------------------------------------------------------------------
// Surface warm-shift: move base surface hue toward the seed's character hue
// ---------------------------------------------------------------------------

private val DARK_BACKGROUND   = Color(0xFF0F0F10)
private val DARK_SURFACE      = Color(0xFF1A1A1C)
private val DARK_SURFACE_VARIANT = Color(0xFF242426)

private val LIGHT_BACKGROUND   = Color(0xFFF2F2F5)
private val LIGHT_SURFACE      = Color(0xFFFFFFFF)
private val LIGHT_SURFACE_VARIANT = Color(0xFFE8E8EC)

private val TEXT_ON_DARK  = Color(0xFFFAFAFA)
private val TEXT_ON_LIGHT = Color(0xFF1C1C1E)
private val TEXT_SECONDARY_DARK  = Color(0xFF71717A)
private val TEXT_SECONDARY_LIGHT = Color(0xFF8E8E93)

// ---------------------------------------------------------------------------
// Generate full IrisColorScheme from a seed
// ---------------------------------------------------------------------------

fun generateIrisColorScheme(
    seedPrimary   : Color,
    seedGradient  : Color,
    seedSecondary : Color,
    warmth        : Float,
    isDark        : Boolean,
): IrisColorScheme {
    return if (isDark) generateDark(seedPrimary, seedGradient, seedSecondary, warmth)
    else generateLight(seedPrimary, seedGradient, seedSecondary, warmth)
}

private fun generateDark(
    primary   : Color,
    gradient  : Color,
    secondary : Color,
    warmth    : Float,
): IrisColorScheme {
    val surfaceHue   = DARK_SURFACE.hueOf()
    val targetHue    = primary.hueOf()
    val hueDelta     = ((targetHue - surfaceHue + 180f) % 360f) - 180f
    val shift        = hueDelta * warmth.coerceIn(-1f, 1f) * 0.25f

    val surface    = DARK_SURFACE.hueShift(shift)
    val bg         = DARK_BACKGROUND.hueShift(shift * 0.7f)
    val srfcVariant = DARK_SURFACE_VARIANT.hueShift(shift)

    val onPrimary     = contrastFloor(primary, TEXT_ON_DARK, DARK_BACKGROUND)
    val onSecondary   = contrastFloor(secondary, TEXT_ON_DARK, DARK_BACKGROUND)
    val onGradient    = contrastFloor(gradient, TEXT_ON_DARK, DARK_BACKGROUND)

    val primaryContainer   = primary.desaturate(0.35f).adjustLightness(0.08f)
    val secondaryContainer = secondary.desaturate(0.35f).adjustLightness(0.08f)

    return IrisColorScheme(
        primary              = primary,
        onPrimary            = onPrimary,
        primaryContainer     = primaryContainer,
        onPrimaryContainer   = primary,
        gradientEnd          = gradient,
        onGradientEnd        = onGradient,
        secondary            = secondary,
        onSecondary          = onSecondary,
        secondaryContainer   = secondaryContainer,
        onSecondaryContainer = secondary,
        surface              = surface,
        onSurface            = TEXT_ON_DARK,
        surfaceVariant       = srfcVariant,
        onSurfaceVariant     = TEXT_SECONDARY_DARK,
        background           = bg,
        onBackground         = TEXT_ON_DARK,
        isDark               = true,
    )
}

private fun generateLight(
    seedPrimary   : Color,
    seedGradient  : Color,
    seedSecondary : Color,
    warmth        : Float,
): IrisColorScheme {
    val surfaceHue = LIGHT_SURFACE.hueOf()
    val targetHue  = seedPrimary.hueOf()
    val hueDelta   = ((targetHue - surfaceHue + 180f) % 360f) - 180f
    val shift      = hueDelta * warmth.coerceIn(-1f, 1f) * 0.25f

    val surface    = LIGHT_SURFACE.hueShift(shift)
    val bg         = LIGHT_BACKGROUND.hueShift(shift * 0.7f)
    val srfcVariant = LIGHT_SURFACE_VARIANT.hueShift(shift)

    val primary   = seedPrimary.desaturate(0.1f).adjustLightness(-0.3f)
    val gradient  = seedGradient.adjustLightness(-0.25f)
    val secondary = seedSecondary.adjustLightness(-0.25f).desaturate(0.1f)

    val white       = Color(0xFFFFFFFF)
    val darkText    = Color(0xFF1C1C1E)

    val onPrimary     = contrastFloor(primary, white, darkText)
    val onSecondary   = contrastFloor(secondary, white, darkText)
    val onGradient    = contrastFloor(gradient, white, darkText)

    val primaryContainer   = seedPrimary.desaturate(0.2f).adjustLightness(0.15f)
    val secondaryContainer = seedSecondary.desaturate(0.2f).adjustLightness(0.15f)

    return IrisColorScheme(
        primary              = primary,
        onPrimary            = onPrimary,
        primaryContainer     = primaryContainer,
        onPrimaryContainer   = primary,
        gradientEnd          = gradient,
        onGradientEnd        = onGradient,
        secondary            = secondary,
        onSecondary          = onSecondary,
        secondaryContainer   = secondaryContainer,
        onSecondaryContainer = secondary,
        surface              = surface,
        onSurface            = darkText,
        surfaceVariant       = srfcVariant,
        onSurfaceVariant     = TEXT_SECONDARY_LIGHT,
        background           = bg,
        onBackground         = darkText,
        isDark               = false,
    )
}
