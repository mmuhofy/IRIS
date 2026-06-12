package com.iris.assistant.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// IrisCoreAnimation
//
// size        — diameter of the ring canvas (default 240dp)
// state       — current IrisCoreState driven by ViewModel
// amplitude   — 0f..1f, used in LISTENING state (mic amplitude)
// ttsProgress — 0f..1f, used in SPEAKING state (TTS wave sync)
// ---------------------------------------------------------------------------
@Composable
fun IrisCoreAnimation(
    modifier    : Modifier      = Modifier,
    state       : IrisCoreState = IrisCoreState.IDLE,
    amplitude   : Float         = 0f,   // 0..1, LISTENING only
    ttsProgress : Float         = 0f,   // 0..1, SPEAKING only
    size        : Dp            = Constants.IRIS_CORE_SIZE.dp
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    val transition = rememberInfiniteTransition(label = "iris_core")

    // --- IDLE: slow pulse (opacity 30% → 60%, period 2.4s) ---
    val idlePulse by transition.animateFloat(
        initialValue   = 0.30f,
        targetValue    = 0.60f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // --- THINKING: rotation (360° / 1.2s) ---
    val rotation by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // --- LISTENING: secondary pulse ring ---
    val listenPulse by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listen_pulse"
    )

    // --- SPEAKING: wave offset ---
    val waveOffset by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing)
        ),
        label = "wave_offset"
    )

    Canvas(modifier = modifier.size(size)) {
        val center     = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius     = (this.size.minDimension / 2f) * 0.72f
        val strokeBase = this.size.minDimension * 0.045f

        when (state) {
            IrisCoreState.IDLE      -> drawIdle(center, radius, strokeBase, primary, gradientEnd, idlePulse)
            IrisCoreState.LISTENING -> drawListening(center, radius, strokeBase, primary, gradientEnd, amplitude, listenPulse)
            IrisCoreState.THINKING  -> drawThinking(center, radius, strokeBase, primary, gradientEnd, rotation)
            IrisCoreState.SPEAKING  -> drawSpeaking(center, radius, strokeBase, primary, gradientEnd, ttsProgress, waveOffset)
        }
    }
}

// ---------------------------------------------------------------------------
// IDLE — full gradient ring, pulsing opacity
// ---------------------------------------------------------------------------
private fun DrawScope.drawIdle(
    center     : Offset,
    radius     : Float,
    stroke     : Float,
    primary    : Color,
    gradientEnd: Color,
    pulse      : Float
) {
    drawCircle(
        brush  = Brush.sweepGradient(
            colors = listOf(primary.copy(alpha = pulse), gradientEnd.copy(alpha = pulse), primary.copy(alpha = pulse)),
            center = center
        ),
        radius = radius,
        center = center,
        style  = Stroke(width = stroke, cap = StrokeCap.Round)
    )
    // Soft inner glow
    drawCircle(
        color  = primary.copy(alpha = pulse * 0.12f),
        radius = radius * 0.85f,
        center = center
    )
}

// ---------------------------------------------------------------------------
// LISTENING — ring expands with amplitude, secondary ripple
// ---------------------------------------------------------------------------
private fun DrawScope.drawListening(
    center     : Offset,
    radius     : Float,
    stroke     : Float,
    primary    : Color,
    gradientEnd: Color,
    amplitude  : Float,
    pulse      : Float
) {
    val expandedRadius = radius + (radius * 0.18f * amplitude)
    val alpha          = 0.55f + (0.45f * amplitude)

    // Outer ripple
    drawCircle(
        color  = primary.copy(alpha = pulse * 0.25f),
        radius = expandedRadius + (radius * 0.15f * pulse),
        center = center,
        style  = Stroke(width = stroke * 0.5f)
    )
    // Main ring
    drawCircle(
        brush  = Brush.sweepGradient(
            colors = listOf(primary.copy(alpha = alpha), gradientEnd.copy(alpha = alpha), primary.copy(alpha = alpha)),
            center = center
        ),
        radius = expandedRadius,
        center = center,
        style  = Stroke(width = stroke + (stroke * 0.4f * amplitude), cap = StrokeCap.Round)
    )
    // Inner glow
    drawCircle(
        color  = primary.copy(alpha = alpha * 0.15f),
        radius = expandedRadius * 0.85f,
        center = center
    )
}

// ---------------------------------------------------------------------------
// THINKING — rotating arc (270° sweep, gradient)
// ---------------------------------------------------------------------------
private fun DrawScope.drawThinking(
    center     : Offset,
    radius     : Float,
    stroke     : Float,
    primary    : Color,
    gradientEnd: Color,
    rotation   : Float
) {
    // Faint full ring as background track
    drawCircle(
        color  = primary.copy(alpha = 0.12f),
        radius = radius,
        center = center,
        style  = Stroke(width = stroke)
    )
    // Rotating arc
    rotate(degrees = rotation, pivot = center) {
        drawArc(
            brush       = Brush.sweepGradient(
                colors  = listOf(Color.Transparent, gradientEnd, primary),
                center  = center
            ),
            startAngle  = -90f,
            sweepAngle  = 270f,
            useCenter   = false,
            topLeft     = Offset(center.x - radius, center.y - radius),
            size        = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style       = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// SPEAKING — wave-distorted ring (sinusoidal radius modulation)
// ---------------------------------------------------------------------------
private fun DrawScope.drawSpeaking(
    center     : Offset,
    radius     : Float,
    stroke     : Float,
    primary    : Color,
    gradientEnd: Color,
    ttsProgress: Float,
    waveOffset : Float
) {
    val waveCount     = 6
    val waveAmplitude = radius * 0.10f * (0.4f + 0.6f * ttsProgress)
    val segments      = 120
    val alpha         = 0.85f

    val path = androidx.compose.ui.graphics.Path()
    for (i in 0..segments) {
        val angle    = (i.toFloat() / segments) * 2f * PI.toFloat()
        val wave     = waveAmplitude * sin(waveCount * angle + waveOffset)
        val r        = radius + wave
        val x        = center.x + r * cos(angle)
        val y        = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path  = path,
        brush = Brush.sweepGradient(
            colors = listOf(primary.copy(alpha = alpha), gradientEnd.copy(alpha = alpha), primary.copy(alpha = alpha)),
            center = center
        ),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
    // Inner glow
    drawCircle(
        color  = primary.copy(alpha = 0.10f),
        radius = radius * 0.82f,
        center = center
    )
}