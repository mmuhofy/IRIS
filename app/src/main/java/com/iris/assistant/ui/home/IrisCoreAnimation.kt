package com.iris.assistant.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IrisCoreAnimation(
    modifier: Modifier = Modifier,
    state: IrisCoreState = IrisCoreState.IDLE,
    amplitude: Float = 0f,
    ttsProgress: Float = 0f,
    coreSize: Dp = Constants.IRIS_CORE_SIZE.dp
) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val secondary = IrisTheme.colors.secondary
    val error = MaterialTheme.colorScheme.error

    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            time += 0.016f
        }
    }

    val targetScale = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.02f
        IrisCoreState.THINKING -> 1f
        IrisCoreState.SPEAKING -> 1.01f
    }
    val targetAlpha = when (state) {
        IrisCoreState.IDLE -> 0.45f
        IrisCoreState.LISTENING -> 0.75f
        IrisCoreState.THINKING -> 0.85f
        IrisCoreState.SPEAKING -> 0.7f
    }
    val targetFillIntensity = when (state) {
        IrisCoreState.IDLE -> 0.5f
        IrisCoreState.LISTENING -> 0.75f
        IrisCoreState.THINKING -> 0.85f
        IrisCoreState.SPEAKING -> 0.65f
    }
    val targetFluidSpeed = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.6f
        IrisCoreState.THINKING -> 2.8f
        IrisCoreState.SPEAKING -> 2f
    }
    val targetSweep = when (state) {
        IrisCoreState.THINKING -> 280f
        else -> 360f
    }
    val targetThickness = when (state) {
        IrisCoreState.LISTENING -> 4.5f
        else -> 3f
    }
    val targetGlowAlpha = when (state) {
        IrisCoreState.IDLE -> 0.15f
        IrisCoreState.LISTENING -> 0.25f
        IrisCoreState.THINKING -> 0.3f
        IrisCoreState.SPEAKING -> 0.2f
    }

    val ringScale by animateFloatAsState(targetScale, tween(500), label = "scale")
    val ringAlpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")
    val fillIntensity by animateFloatAsState(targetFillIntensity, tween(500), label = "fillIntensity")
    val fluidSpeed by animateFloatAsState(targetFluidSpeed, tween(600), label = "fluidSpeed")
    val ringSweep by animateFloatAsState(targetSweep, tween(600), label = "sweep")
    val ringThickness by animateFloatAsState(targetThickness, tween(400), label = "thickness")
    val glowAlpha by animateFloatAsState(targetGlowAlpha, tween(400), label = "glowAlpha")

    val idlePulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.03f else 0f
    val idleAlphaPulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.08f else 0f
    val thinkingRotation = if (state == IrisCoreState.THINKING) (time * 100f) % 360f else 0f
    val speakingWave = if (state == IrisCoreState.SPEAKING) {
        val prog = ttsProgress.coerceIn(0f, 1f)
        sin(time * 12f + prog * 6f) * 0.5f
    } else 0f

    val ringColor = when (state) {
        IrisCoreState.IDLE -> primary
        IrisCoreState.LISTENING -> gradientEnd
        IrisCoreState.THINKING -> primary
        IrisCoreState.SPEAKING -> secondary
    }

    Canvas(modifier = modifier.size(coreSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        val baseRadius = minDim * 0.42f
        val currentRadius = baseRadius * (ringScale + idlePulse + speakingWave * 0.02f)
        val currentThickness = (ringThickness + speakingWave * 1.5f).coerceAtLeast(1f)
        val baseAlpha = (ringAlpha + idleAlphaPulse).coerceIn(0f, 1f)
        val innerRadius = (currentRadius - currentThickness / 2f).coerceAtLeast(1f)
        val intensity = (fillIntensity + idleAlphaPulse * 0.3f).coerceIn(0f, 1f)
        val speed = fluidSpeed

        // Center glow
        val glowRadius = currentRadius + currentThickness * 4
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = baseAlpha * glowAlpha * 0.7f),
                    ringColor.copy(alpha = baseAlpha * glowAlpha * 0.2f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(cx, cy)
        )

        // --- Fluid core: overlapping gradient layers ---
        // Each layer orbits at a different speed/direction, creating
        // a continuous liquid-like blend when overlaid.

        val s = speed
        val ir = innerRadius

        // 4 fluid sources with incommensurate orbital periods for complex motion
        val src1x = sin(time * 0.37f * s) * ir * 0.35f
        val src1y = cos(time * 0.53f * s) * ir * 0.35f

        val src2x = cos(time * 0.43f * s + 1.8f) * ir * 0.3f
        val src2y = sin(time * 0.61f * s + 1.8f) * ir * 0.3f

        val src3x = sin(time * 0.71f * s + 3.2f) * ir * 0.25f
        val src3y = cos(time * 0.49f * s + 3.2f) * ir * 0.25f

        val src4x = cos(time * 0.29f * s + 4.7f) * ir * 0.18f
        val src4y = sin(time * 0.67f * s + 4.7f) * ir * 0.18f

        // Base ambient fill
        val ambientAlpha = (intensity * 0.15f).coerceIn(0f, 0.2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = ambientAlpha),
                    ringColor.copy(alpha = ambientAlpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = ir
            ),
            radius = ir,
            center = Offset(cx, cy)
        )

        // Layer 1 — large, dominant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (0.5f * intensity).coerceIn(0f, 0.55f)),
                    gradientEnd.copy(alpha = (0.2f * intensity).coerceAtMost(0.25f)),
                    Color.Transparent
                ),
                center = Offset(cx + src1x, cy + src1y),
                radius = ir * 0.65f
            ),
            radius = ir * 0.65f,
            center = Offset(cx + src1x, cy + src1y)
        )

        // Layer 2 — counter-orbit, secondary color
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    gradientEnd.copy(alpha = (0.4f * intensity).coerceIn(0f, 0.45f)),
                    ringColor.copy(alpha = (0.15f * intensity).coerceAtMost(0.2f)),
                    Color.Transparent
                ),
                center = Offset(cx + src2x, cy + src2y),
                radius = ir * 0.55f
            ),
            radius = ir * 0.55f,
            center = Offset(cx + src2x, cy + src2y)
        )

        // Layer 3 — accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (0.35f * intensity).coerceIn(0f, 0.4f)),
                    Color.Transparent
                ),
                center = Offset(cx + src3x, cy + src3y),
                radius = ir * 0.45f
            ),
            radius = ir * 0.45f,
            center = Offset(cx + src3x, cy + src3y)
        )

        // Layer 4 — bright core accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (0.6f * intensity).coerceIn(0f, 0.65f)),
                    Color.Transparent
                ),
                center = Offset(cx + src4x, cy + src4y),
                radius = ir * 0.18f
            ),
            radius = ir * 0.18f,
            center = Offset(cx + src4x, cy + src4y)
        )

        // Outer ripple for LISTENING
        if (state == IrisCoreState.LISTENING && amplitude > 0.01f) {
            val rippleRadius = currentRadius + amplitude * minDim * 0.15f
            val rippleAlpha = (baseAlpha * amplitude * 0.5f).coerceIn(0f, 0.3f)
            drawCircle(
                color = gradientEnd.copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }

        // Main ring
        val sweepAngle = if (state == IrisCoreState.IDLE) 360f else ringSweep
        val startAngle = thinkingRotation

        drawArc(
            brush = Brush.linearGradient(
                colors = listOf(
                    ringColor.copy(alpha = baseAlpha * 0.9f),
                    gradientEnd.copy(alpha = baseAlpha * 0.6f),
                    ringColor.copy(alpha = baseAlpha * 0.9f)
                ),
                start = Offset(cx - currentRadius, cy - currentRadius),
                end = Offset(cx + currentRadius, cy + currentRadius)
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(
                width = currentThickness,
                cap = StrokeCap.Round
            ),
            topLeft = Offset(cx - currentRadius, cy - currentRadius),
            size = Size(currentRadius * 2, currentRadius * 2)
        )

        // Bright leading edge for THINKING
        if (state == IrisCoreState.THINKING) {
            val headAngle = startAngle + ringSweep
            drawArc(
                color = ringColor.copy(alpha = (baseAlpha * 0.9f).coerceIn(0f, 1f)),
                startAngle = headAngle - 10f,
                sweepAngle = 10f,
                useCenter = false,
                style = Stroke(
                    width = currentThickness + 1.5f,
                    cap = StrokeCap.Round
                ),
                topLeft = Offset(cx - currentRadius, cy - currentRadius),
                size = Size(currentRadius * 2, currentRadius * 2)
            )
        }
    }
}
