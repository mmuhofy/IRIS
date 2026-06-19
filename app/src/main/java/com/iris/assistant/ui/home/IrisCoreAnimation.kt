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
        IrisCoreState.IDLE -> 0.4f
        IrisCoreState.LISTENING -> 0.7f
        IrisCoreState.THINKING -> 0.8f
        IrisCoreState.SPEAKING -> 0.65f
    }
    val targetNebulaAlpha = when (state) {
        IrisCoreState.IDLE -> 0.4f
        IrisCoreState.LISTENING -> 0.65f
        IrisCoreState.THINKING -> 0.75f
        IrisCoreState.SPEAKING -> 0.55f
    }
    val targetNebulaSpeed = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.5f
        IrisCoreState.THINKING -> 2.5f
        IrisCoreState.SPEAKING -> 1.8f
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
        IrisCoreState.IDLE -> 0.12f
        IrisCoreState.LISTENING -> 0.2f
        IrisCoreState.THINKING -> 0.25f
        IrisCoreState.SPEAKING -> 0.18f
    }

    val ringScale by animateFloatAsState(targetScale, tween(500), label = "scale")
    val ringAlpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")
    val nebulaAlpha by animateFloatAsState(targetNebulaAlpha, tween(500), label = "nebulaAlpha")
    val nebulaSpeed by animateFloatAsState(targetNebulaSpeed, tween(600), label = "nebulaSpeed")
    val ringSweep by animateFloatAsState(targetSweep, tween(600), label = "sweep")
    val ringThickness by animateFloatAsState(targetThickness, tween(400), label = "thickness")
    val glowAlpha by animateFloatAsState(targetGlowAlpha, tween(400), label = "glowAlpha")

    val idlePulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.03f else 0f
    val idleAlphaPulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.1f else 0f
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
        val innerRadius = currentRadius - currentThickness / 2f

        // Center glow
        val glowRadius = currentRadius + currentThickness * 4
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = baseAlpha * glowAlpha * 0.6f),
                    ringColor.copy(alpha = baseAlpha * glowAlpha * 0.15f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(cx, cy)
        )

        // Nebula blobs
        val blobAlpha = (nebulaAlpha + idleAlphaPulse).coerceIn(0f, 1f)
        val speed = nebulaSpeed

        val b1a = time * 0.8f * speed
        val b1x = cx + cos(b1a) * innerRadius * 0.5f
        val b1y = cy + sin(b1a * 1.15f) * innerRadius * 0.5f
        val b1r = innerRadius * 0.4f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = blobAlpha * 0.5f),
                    primary.copy(alpha = 0f)
                ),
                center = Offset(b1x, b1y),
                radius = b1r
            ),
            radius = b1r,
            center = Offset(b1x, b1y)
        )

        val b2a = -time * 0.5f * speed + 2.1f
        val b2x = cx + cos(b2a) * innerRadius * 0.35f
        val b2y = cy + sin(b2a * 0.85f) * innerRadius * 0.35f
        val b2r = innerRadius * 0.3f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    gradientEnd.copy(alpha = blobAlpha * 0.6f),
                    gradientEnd.copy(alpha = 0f)
                ),
                center = Offset(b2x, b2y),
                radius = b2r
            ),
            radius = b2r,
            center = Offset(b2x, b2y)
        )

        val b3a = time * 0.35f * speed + 4.3f
        val b3x = cx + cos(b3a) * innerRadius * 0.25f
        val b3y = cy + sin(b3a * 1.3f) * innerRadius * 0.25f
        val b3r = innerRadius * 0.2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = blobAlpha * 0.4f),
                    secondary.copy(alpha = 0f)
                ),
                center = Offset(b3x, b3y),
                radius = b3r
            ),
            radius = b3r,
            center = Offset(b3x, b3y)
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
