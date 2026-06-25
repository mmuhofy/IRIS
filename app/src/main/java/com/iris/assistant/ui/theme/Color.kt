package com.iris.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Base surface/text colors shared across both modes
// ---------------------------------------------------------------------------
val ColorSurface       = Color(0xFF1A1A1C)
val ColorError         = Color(0xFFF87171)
val ColorTextPrimary   = Color(0xFFFAFAFA)
val ColorTextSecondary = Color(0xFF71717A)

// ---------------------------------------------------------------------------
// Scheme seed colors — each defines accent triplet + surface warmth hint
// warmth: -1..1, controls how much the base surface hue shifts toward the
//         scheme's dominant accent hue (negative = cool, positive = warm).
// ---------------------------------------------------------------------------

// Slate (default) — warm lavender-violet
val SlatePrimary   = Color(0xFFC7B9FF)
val SlateGradient  = Color(0xFF9D8AFF)
val SlateSecondary = Color(0xFFFFD166)
const val SlateWarmth   = -0.25f

// Rose Quartz — soft pink with mauve
val RoseQuartzPrimary   = Color(0xFFF9A8D4)
val RoseQuartzGradient  = Color(0xFFF472B6)
val RoseQuartzSecondary = Color(0xFFD8B4FE)
const val RoseQuartzWarmth   = 0.35f

// Sage — soft green, warm secondary
val SagePrimary   = Color(0xFF86EFAC)
val SageGradient  = Color(0xFF4ADE80)
val SageSecondary = Color(0xFFFCD34D)
const val SageWarmth   = 0.1f

// Cobalt — pastel blue with lavender contrast
val CobaltPrimary   = Color(0xFF93C5FD)
val CobaltGradient  = Color(0xFF60A5FA)
val CobaltSecondary = Color(0xFFC4B5FD)
const val CobaltWarmth   = -0.4f

// Ember — warm orange, honey accent
val EmberPrimary   = Color(0xFFFB923C)
val EmberGradient  = Color(0xFFF97316)
val EmberSecondary = Color(0xFFFBBF24)
const val EmberWarmth   = 0.55f

// Monochrome — pure greyscale
val MonochromePrimary   = Color(0xFFE4E4E7)
val MonochromeGradient  = Color(0xFFA1A1AA)
val MonochromeSecondary = Color(0xFF71717A)
const val MonochromeWarmth   = 0f
