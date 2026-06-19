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
import kotlin.math.PI

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
        IrisCoreState.IDLE -> 0.5f
        IrisCoreState.LISTENING -> 0.8f
        IrisCoreState.THINKING -> 0.9f
        IrisCoreState.SPEAKING -> 0.75f
    }
    val targetFluidAlpha = when (state) {
        IrisCoreState.IDLE -> 0.35f
        IrisCoreState.LISTENING -> 0.55f
        IrisCoreState.THINKING -> 0.65f
        IrisCoreState.SPEAKING -> 0.5f
    }
    val targetFluidSpeed = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.6f
        IrisCoreState.THINKING -> 2.5f
        IrisCoreState.SPEAKING -> 1.8f
    }
    val targetNeuralSpeed = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.5f
        IrisCoreState.THINKING -> 2.8f
        IrisCoreState.SPEAKING -> 2f
    }
    val targetSweep = when (state) {
        IrisCoreState.THINKING -> 270f
        else -> 360f
    }
    val targetThickness = when (state) {
        IrisCoreState.LISTENING -> 4.5f
        else -> 3f
    }
    val targetGlowAlpha = when (state) {
        IrisCoreState.IDLE -> 0.12f
        IrisCoreState.LISTENING -> 0.22f
        IrisCoreState.THINKING -> 0.28f
        IrisCoreState.SPEAKING -> 0.18f
    }

    val ringScale by animateFloatAsState(targetScale, tween(500), label = "scale")
    val ringAlpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")
    val fluidAlpha by animateFloatAsState(targetFluidAlpha, tween(500), label = "fluidAlpha")
    val fluidSpeed by animateFloatAsState(targetFluidSpeed, tween(600), label = "fluidSpeed")
    val neuralSpeed by animateFloatAsState(targetNeuralSpeed, tween(500), label = "neuralSpeed")
    val ringSweep by animateFloatAsState(targetSweep, tween(600), label = "sweep")
    val ringThickness by animateFloatAsState(targetThickness, tween(400), label = "thickness")
    val glowAlpha by animateFloatAsState(targetGlowAlpha, tween(400), label = "glowAlpha")

    val idlePulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.03f else 0f
    val idleAlphaPulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.08f else 0f
    val thinkingRotation = if (state == IrisCoreState.THINKING) (time * 90f) % 360f else 0f
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
        val fAlpha = (fluidAlpha + idleAlphaPulse * 0.3f).coerceIn(0f, 1f)
        val nSpeed = neuralSpeed
        val fSpeed = fluidSpeed

        // =====================================================================
        // LAYER 1 — Center glow
        // =====================================================================
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

        // =====================================================================
        // LAYER 2 — Fluid gradient base (3 orbiting sources)
        // =====================================================================
        val fs = fSpeed
        val ir = innerRadius

        val src1x = sin(time * 0.37f * fs) * ir * 0.4f
        val src1y = cos(time * 0.53f * fs) * ir * 0.4f

        val src2x = cos(time * 0.43f * fs + 2.1f) * ir * 0.32f
        val src2y = sin(time * 0.61f * fs + 2.1f) * ir * 0.32f

        val src3x = sin(time * 0.71f * fs + 3.8f) * ir * 0.24f
        val src3y = cos(time * 0.49f * fs + 3.8f) * ir * 0.24f

        // Ambient fill
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (fAlpha * 0.2f).coerceAtMost(0.2f)),
                    ringColor.copy(alpha = (fAlpha * 0.05f).coerceAtMost(0.08f)),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = ir
            ),
            radius = ir,
            center = Offset(cx, cy)
        )

        // Layer A — dominant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (fAlpha * 0.55f).coerceAtMost(0.55f)),
                    gradientEnd.copy(alpha = (fAlpha * 0.2f).coerceAtMost(0.25f)),
                    Color.Transparent
                ),
                center = Offset(cx + src1x, cy + src1y),
                radius = ir * 0.7f
            ),
            radius = ir * 0.7f,
            center = Offset(cx + src1x, cy + src1y)
        )

        // Layer B — counter-orbit
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    gradientEnd.copy(alpha = (fAlpha * 0.4f).coerceAtMost(0.45f)),
                    ringColor.copy(alpha = (fAlpha * 0.15f).coerceAtMost(0.2f)),
                    Color.Transparent
                ),
                center = Offset(cx + src2x, cy + src2y),
                radius = ir * 0.6f
            ),
            radius = ir * 0.6f,
            center = Offset(cx + src2x, cy + src2y)
        )

        // Layer C — accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (fAlpha * 0.35f).coerceAtMost(0.4f)),
                    Color.Transparent
                ),
                center = Offset(cx + src3x, cy + src3y),
                radius = ir * 0.45f
            ),
            radius = ir * 0.45f,
            center = Offset(cx + src3x, cy + src3y)
        )

        // =====================================================================
        // LAYER 3 — Neural wave rings (concentric pulsing)
        // =====================================================================
        val waveColor = ringColor
        for (i in 0..3) {
            val phase = i * 1.6f
            val waveRadius = ir * (0.15f + i * 0.22f + sin(time * 1.8f * fs + phase) * 0.08f)
            val waveAlpha = (0.04f + sin(time * 2.2f * fs + phase) * 0.025f + 0.025f) * fAlpha * 2f
            drawCircle(
                color = waveColor.copy(alpha = waveAlpha.coerceIn(0f, 0.12f)),
                radius = waveRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 0.8f)
            )
        }

        // =====================================================================
        // LAYER 4 — Neural mesh (nodes + connections)
        // =====================================================================
        val nodeRadius = ir * 0.55f
        val centerNode = Offset(cx, cy)
        val nodeCount = 6
        val nodeAngles = (0 until nodeCount).map { i ->
            i * (2f * PI.toFloat()) / nodeCount - PI.toFloat() / 2f
        }
        val outerNodes = nodeAngles.map { angle ->
            Offset(
                cx + cos(angle) * nodeRadius,
                cy + sin(angle) * nodeRadius
            )
        }

        // Activation wave cycles through 7 positions (6 outer + 1 center)
        val cycleDur = when (state) {
            IrisCoreState.IDLE -> 2.8f / nSpeed
            IrisCoreState.LISTENING -> 1.8f / nSpeed
            IrisCoreState.THINKING -> 1.2f / nSpeed
            IrisCoreState.SPEAKING -> 1.6f / nSpeed
        }
        val rawActivation = (time / cycleDur) % 1f
        val totalSteps = (nodeCount + 1).toFloat() // 7
        val rawPos = rawActivation * totalSteps
        val actIdx = rawPos.toInt().coerceAtMost(nodeCount) // 0..6
        val actProg = rawPos - actIdx.toFloat()

        val pulseCurve = sin(actProg * PI.toFloat()).coerceIn(0f, 1f)

        // --- Connections (behind nodes) ---
        // Radial: center → each outer
        for (i in 0 until nodeCount) {
            val isActive = i == actIdx || actIdx == nodeCount
            val baseConn = 0.06f
            val firingConn = pulseCurve * 0.65f
            val a = baseConn + firingConn * if (isActive) 1f else 0f

            drawLine(
                color = ringColor.copy(alpha = a.coerceIn(0f, 0.7f)),
                start = centerNode,
                end = outerNodes[i],
                strokeWidth = 1.5f
            )
        }

        // Ring: outer → adjacent
        for (i in 0 until nodeCount) {
            val j = (i + 1) % nodeCount
            val isActive = i == actIdx || j == actIdx || actIdx == nodeCount
            val baseConn = 0.04f
            val firingConn = pulseCurve * 0.45f
            val a = baseConn + firingConn * if (isActive) 1f else 0f

            drawLine(
                color = ringColor.copy(alpha = a.coerceIn(0f, 0.5f)),
                start = outerNodes[i],
                end = outerNodes[j],
                strokeWidth = 1f
            )
        }

        // --- Outer nodes ---
        for (i in 0 until nodeCount) {
            val isFiring = i == actIdx
            val nodeBaseAlpha = 0.25f
            val nodeFiringAlpha = nodeBaseAlpha + pulseCurve * 0.6f
            val nodeA = if (isFiring) nodeFiringAlpha else nodeBaseAlpha

            val nodeBaseR = ir * 0.045f
            val nodeFiringR = nodeBaseR * (1f + pulseCurve * 0.6f)
            val nodeR = if (isFiring) nodeFiringR else nodeBaseR

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = nodeA.coerceIn(0f, 0.85f)),
                        ringColor.copy(alpha = 0f)
                    ),
                    center = outerNodes[i],
                    radius = nodeR
                ),
                radius = nodeR,
                center = outerNodes[i]
            )

            // Firing glow
            if (isFiring && pulseCurve > 0.1f) {
                val glowR = nodeR * (1f + pulseCurve * 1.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = (pulseCurve * 0.2f).coerceIn(0f, 0.3f)),
                            ringColor.copy(alpha = 0f)
                        ),
                        center = outerNodes[i],
                        radius = glowR
                    ),
                    radius = glowR,
                    center = outerNodes[i]
                )
            }
        }

        // --- Center node ---
        val centerFiring = actIdx == nodeCount
        val cBaseAlpha = 0.3f
        val cFiringAlpha = cBaseAlpha + pulseCurve * 0.6f
        val cA = if (centerFiring) cFiringAlpha else cBaseAlpha

        val cBaseR = ir * 0.055f
        val cFiringR = cBaseR * (1f + pulseCurve * 0.6f)
        val cR = if (centerFiring) cFiringR else cBaseR

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = cA.coerceIn(0f, 0.9f)),
                    ringColor.copy(alpha = 0f)
                ),
                center = centerNode,
                radius = cR
            ),
            radius = cR,
            center = centerNode
        )

        if (centerFiring && pulseCurve > 0.1f) {
            val cGlowR = cR * (1f + pulseCurve * 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = (pulseCurve * 0.25f).coerceIn(0f, 0.35f)),
                        ringColor.copy(alpha = 0f)
                    ),
                    center = centerNode,
                    radius = cGlowR
                ),
                radius = cGlowR,
                center = centerNode
            )
        }

        // =====================================================================
        // LAYER 5 — Outer ripple (LISTENING only)
        // =====================================================================
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

        // =====================================================================
        // LAYER 6 — Main outer ring
        // =====================================================================
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
