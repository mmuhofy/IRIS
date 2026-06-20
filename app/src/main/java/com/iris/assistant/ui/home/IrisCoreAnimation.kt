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

/**
 * PERFORMANCE NOTE (verified against official Compose phases docs —
 * developer.android.com/develop/ui/compose/phases):
 *
 * The continuously-incrementing `time` state used to live in this function's
 * top-level composable body. Because Compose phases run Composition -> Layout
 * -> Drawing, a state read inside the composable body (Composition phase)
 * forces ALL code in that body to re-run on every change — including the
 * eight `when (state) { ... }` target calculations and the seven
 * `animateFloatAsState` calls above, none of which actually depend on `time`.
 * At a 16ms tick rate, this meant the entire composable was recomposing at
 * ~60fps even when idle, which is the most likely source of frame drops
 * observed during nav transitions (e.g. Home -> Settings).
 *
 * Fix: `time` is now read ONLY inside the Canvas draw-scope lambda below.
 * Per Compose docs, a state read inside Canvas()/drawBehind/drawWithContent
 * only triggers the Drawing phase, skipping Composition and Layout entirely.
 * This is a read-location change only — every formula, constant, and draw
 * call below is identical to the previous version. Visual output is
 * byte-for-byte the same; only recomposition behavior changes.
 *
 * UNTESTED — verify on-device (Layout Inspector recomposition counts) before
 * considering this confirmed. The theory is verified against official docs;
 * the measured improvement on this exact Canvas is not yet confirmed.
 */
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

    // `time` is declared here (remember survives recomposition) but is
    // intentionally NOT read in the composable body anymore — see note above.
    // It is only written to from LaunchedEffect and only read inside Canvas.
    val timeState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            timeState.floatValue += 0.016f
        }
    }

    val targetScale = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.02f
        IrisCoreState.THINKING -> 1f
        IrisCoreState.SPEAKING -> 1.01f
    }
    val targetBaseAlpha = when (state) {
        IrisCoreState.IDLE -> 0.6f
        IrisCoreState.LISTENING -> 0.85f
        IrisCoreState.THINKING -> 0.9f
        IrisCoreState.SPEAKING -> 0.75f
    }
    val targetIntensity = when (state) {
        IrisCoreState.IDLE -> 0.5f
        IrisCoreState.LISTENING -> 0.7f
        IrisCoreState.THINKING -> 0.85f
        IrisCoreState.SPEAKING -> 0.65f
    }
    val targetFlowSpeed = when (state) {
        IrisCoreState.IDLE -> 1f
        IrisCoreState.LISTENING -> 1.5f
        IrisCoreState.THINKING -> 2.4f
        IrisCoreState.SPEAKING -> 1.8f
    }
    val targetSweep = when (state) {
        IrisCoreState.THINKING -> 270f
        else -> 360f
    }
    val targetThickness = when (state) {
        IrisCoreState.LISTENING -> 4.5f
        else -> 3f
    }
    val targetGlow = when (state) {
        IrisCoreState.IDLE -> 0.15f
        IrisCoreState.LISTENING -> 0.25f
        IrisCoreState.THINKING -> 0.3f
        IrisCoreState.SPEAKING -> 0.2f
    }

    val ringScale by animateFloatAsState(targetScale, tween(500), label = "scale")
    val baseAlpha by animateFloatAsState(targetBaseAlpha, tween(400), label = "baseAlpha")
    val intensity by animateFloatAsState(targetIntensity, tween(500), label = "intensity")
    val flowSpeed by animateFloatAsState(targetFlowSpeed, tween(600), label = "flowSpeed")
    val ringSweep by animateFloatAsState(targetSweep, tween(600), label = "sweep")
    val ringThickness by animateFloatAsState(targetThickness, tween(400), label = "thickness")
    val glowIntensity by animateFloatAsState(targetGlow, tween(400), label = "glowIntensity")

    val ringColor = when (state) {
        IrisCoreState.IDLE -> primary
        IrisCoreState.LISTENING -> gradientEnd
        IrisCoreState.THINKING -> primary
        IrisCoreState.SPEAKING -> secondary
    }

    Canvas(modifier = modifier.size(coreSize)) {
        // `time` read happens here — inside the draw-scope lambda — which is
        // the Drawing phase. This is the only place `timeState` is read, so
        // updates to it no longer trigger Composition/Layout for this function.
        val time = timeState.floatValue

        val idlePulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.03f else 0f
        val idleAlphaPulse = if (state == IrisCoreState.IDLE) sin(time * 2f) * 0.08f else 0f
        val thinkingRotation = if (state == IrisCoreState.THINKING) (time * 90f) % 360f else 0f
        val speakingWave = if (state == IrisCoreState.SPEAKING) {
            val prog = ttsProgress.coerceIn(0f, 1f)
            sin(time * 12f + prog * 6f) * 0.5f
        } else 0f

        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        val baseRadius = minDim * 0.42f
        val currentRadius = baseRadius * (ringScale + idlePulse + speakingWave * 0.02f)
        val currentThickness = (ringThickness + speakingWave * 1.5f).coerceAtLeast(1f)
        val alpha = (baseAlpha + idleAlphaPulse).coerceIn(0f, 1f)
        val innerR = (currentRadius - currentThickness / 2f).coerceAtLeast(1f)
        val fillI = (intensity + idleAlphaPulse * 0.2f).coerceIn(0f, 1f)
        val flow = flowSpeed

        // =====================================================================
        // OUTER GLOW
        // =====================================================================
        val glowR = currentRadius + currentThickness * 4
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = alpha * glowIntensity * 0.8f),
                    ringColor.copy(alpha = alpha * glowIntensity * 0.2f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(cx, cy)
        )

        // =====================================================================
        // FLUID CORE — single continuous gradient field
        // =====================================================================
        val s = flow
        val ir = innerR
        val fi = fillI

        // Lissajous motion creates complex organic flow patterns.
        // Each gradient source moves on a 2-frequency Lissajous curve,
        // producing non-repeating, fluid-like motion.

        // Source A — dominant gradient center
        val aCx = cx + (sin(time * 0.37f * s) * 0.35f + sin(time * 0.53f * s * 1.7f) * 0.12f) * ir
        val aCy = cy + (cos(time * 0.53f * s) * 0.35f + cos(time * 0.43f * s * 1.7f) * 0.12f) * ir

        // Source B — counter-phase secondary
        val bCx = cx + (cos(time * 0.43f * s + 2.1f) * 0.32f + cos(time * 0.61f * s * 1.5f + 1.2f) * 0.1f) * ir
        val bCy = cy + (sin(time * 0.61f * s + 2.1f) * 0.32f + sin(time * 0.37f * s * 1.5f + 1.2f) * 0.1f) * ir

        // Source C — tertiary accent (faster, smaller orbit)
        val cCx = cx + sin(time * 0.71f * s + 3.8f) * ir * 0.24f
        val cCy = cy + cos(time * 0.49f * s + 3.8f) * ir * 0.24f

        // Source D — micro accent (small, fast, for shimmer)
        val dCx = cx + cos(time * 0.79f * s + 5.2f) * ir * 0.15f
        val dCy = cy + sin(time * 0.83f * s + 5.2f) * ir * 0.15f

        // --- Neural firing modulation ---
        // Compound sine creates pseudo-random firing events
        val neuralFire = (
            sin(time * 5.3f * s) * 0.5f +
            sin(time * 11.7f * s + 1.4f) * 0.3f +
            sin(time * 19.3f * s + 3.7f) * 0.2f
        ) / (0.5f + 0.3f + 0.2f) * 0.5f + 0.5f

        val fireBoost = 1f + neuralFire * 0.35f * fi

        // --- Ambient base fill ---
        val ambA = (fi * 0.15f).coerceAtMost(0.18f)
        drawCircle(
            color = ringColor.copy(alpha = ambA * alpha),
            radius = ir,
            center = Offset(cx, cy)
        )

        // --- Main gradient field (Source A) ---
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to ringColor.copy(alpha = (0.5f * fi * fireBoost).coerceIn(0f, 0.65f)),
                    0.3f to gradientEnd.copy(alpha = (0.3f * fi * fireBoost).coerceIn(0f, 0.4f)),
                    0.6f to ringColor.copy(alpha = (0.12f * fi).coerceAtMost(0.15f)),
                    1.0f to Color.Transparent
                ),
                center = Offset(aCx, aCy),
                radius = ir
            ),
            radius = ir,
            center = Offset(cx, cy)
        )

        // --- Secondary field (Source B) ---
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to gradientEnd.copy(alpha = (0.45f * fi * fireBoost).coerceIn(0f, 0.55f)),
                    0.4f to ringColor.copy(alpha = (0.2f * fi * fireBoost).coerceIn(0f, 0.3f)),
                    0.7f to gradientEnd.copy(alpha = (0.08f * fi).coerceAtMost(0.12f)),
                    1.0f to Color.Transparent
                ),
                center = Offset(bCx, bCy),
                radius = ir * 0.9f
            ),
            radius = ir * 0.9f,
            center = Offset(cx, cy)
        )

        // --- Accent layer (Source C) ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (0.3f * fi).coerceIn(0f, 0.35f)),
                    Color.Transparent
                ),
                center = Offset(cCx, cCy),
                radius = ir * 0.55f
            ),
            radius = ir * 0.55f,
            center = Offset(cCx, cCy)
        )

        // --- Shimmer dot (Source D) ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = (0.5f * fi * (neuralFire * 0.5f + 0.5f)).coerceIn(0f, 0.55f)),
                    Color.Transparent
                ),
                center = Offset(dCx, dCy),
                radius = ir * 0.2f
            ),
            radius = ir * 0.2f,
            center = Offset(dCx, dCy)
        )

        // =====================================================================
        // NEURAL WAVE SHIMMER — concentric wave rings
        // =====================================================================
        val waveAlpha = (0.06f + sin(time * 1.8f * s) * 0.03f) * fi * alpha
        for (i in 0..3) {
            val phase = i * 1.6f
            val wR = ir * (0.15f + i * 0.24f + sin(time * 1.8f * s + phase) * 0.06f)
            val wA = (0.05f + sin(time * 2.2f * s + phase) * 0.025f) * fi * alpha
            drawCircle(
                color = ringColor.copy(alpha = wA.coerceIn(0f, 0.1f)),
                radius = wR,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
        }

        // =====================================================================
        // LISTENING RIPPLE
        // =====================================================================
        if (state == IrisCoreState.LISTENING && amplitude > 0.01f) {
            val rippleR = currentRadius + amplitude * minDim * 0.15f
            val rippleA = (alpha * amplitude * 0.4f).coerceIn(0f, 0.28f)
            drawCircle(
                color = gradientEnd.copy(alpha = rippleA),
                radius = rippleR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }

        // =====================================================================
        // OUTER RING
        // =====================================================================
        val sweepAngle = if (state == IrisCoreState.IDLE) 360f else ringSweep
        val startAngle = thinkingRotation

        drawArc(
            brush = Brush.linearGradient(
                colors = listOf(
                    ringColor.copy(alpha = alpha * 0.9f),
                    gradientEnd.copy(alpha = alpha * 0.5f),
                    ringColor.copy(alpha = alpha * 0.9f)
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
                color = ringColor.copy(alpha = (alpha * 0.9f).coerceIn(0f, 1f)),
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