// app/src/main/java/com/iris/assistant/ui/theme/Color.kt
package com.iris.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Base surfaces — shared across all schemes
// ---------------------------------------------------------------------------
val ColorBackground    = Color(0xFF0F0F10)
val ColorSurface       = Color(0xFF1A1A1C)
val ColorSurfaceHigh   = Color(0xFF242426)
val ColorError         = Color(0xFFF87171)
val ColorTextPrimary   = Color(0xFFFAFAFA)
val ColorTextSecondary = Color(0xFF71717A)

// ---------------------------------------------------------------------------
// Themes — accent-only approach
// Base surfaces above never change; only the accent triplet changes per theme.
// ---------------------------------------------------------------------------

// Slate (default) — warm lavender-violet, sophisticated default
val SlatePrimary   = Color(0xFFC7B9FF)
val SlateGradient  = Color(0xFF9D8AFF)
val SlateSecondary = Color(0xFFFFD166)

// Rose Quartz — soft pink with mauve accent
val RoseQuartzPrimary   = Color(0xFFF9A8D4)
val RoseQuartzGradient  = Color(0xFFF472B6)
val RoseQuartzSecondary = Color(0xFFD8B4FE)

// Sage — soft green, warm secondary
val SagePrimary   = Color(0xFF86EFAC)
val SageGradient  = Color(0xFF4ADE80)
val SageSecondary = Color(0xFFFCD34D)

// Cobalt — pastel blue with lavender contrast
val CobaltPrimary   = Color(0xFF93C5FD)
val CobaltGradient  = Color(0xFF60A5FA)
val CobaltSecondary = Color(0xFFC4B5FD)

// Ember — warm orange, honey accent
val EmberPrimary   = Color(0xFFFB923C)
val EmberGradient  = Color(0xFFF97316)
val EmberSecondary = Color(0xFFFBBF24)

// Monochrome — pure greyscale
val MonochromePrimary   = Color(0xFFE4E4E7)
val MonochromeGradient  = Color(0xFFA1A1AA)
val MonochromeSecondary = Color(0xFF71717A)