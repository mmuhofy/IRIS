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

// Slate (default) — off-white primary, single amber warmth
val SlatePrimary   = Color(0xFFE2E2E2)
val SlateGradient  = Color(0xFFA3A3A3)
val SlateSecondary = Color(0xFFF59E0B)

// Rose Quartz — soft pink
val RoseQuartzPrimary   = Color(0xFFF9A8D4)
val RoseQuartzGradient  = Color(0xFFFBCFE8)
val RoseQuartzSecondary = Color(0xFFF59E0B)

// Sage — soft green, warm secondary
val SagePrimary   = Color(0xFF86EFAC)
val SageGradient  = Color(0xFF4ADE80)
val SageSecondary = Color(0xFFFCD34D)

// Cobalt — pastel blue, cool
val CobaltPrimary   = Color(0xFF93C5FD)
val CobaltGradient  = Color(0xFF60A5FA)
val CobaltSecondary = Color(0xFFA5B4FC)

// Ember — orange accent, single warm tone
val EmberPrimary   = Color(0xFFFB923C)
val EmberGradient  = Color(0xFFF97316)
val EmberSecondary = Color(0xFFFBBF24)

// Monochrome — no color, only grey/white
val MonochromePrimary   = Color(0xFFE4E4E7)
val MonochromeGradient  = Color(0xFFA1A1AA)
val MonochromeSecondary = Color(0xFF71717A)