package com.iris.assistant.ui.home

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// AGSL shader — Android 13+ (API 33+), runs entirely on GPU.
//
// Uniforms (set each frame from Compose):
//   iTime        – elapsed seconds
//   iResolution  – canvas size px
//   uPrimary     – theme primary RGBA
//   uGradEnd     – theme gradientEnd RGBA
//   uSecondary   – theme secondary RGBA
//   uState       – discrete 0/1/2/3 for effect gating
//   uAnimState   – smoothly animated 0.0–3.0 for color crossfade
//   uAmplitude   – mic amplitude 0–1 (LISTENING)
//   uTtsProgress – TTS progress 0–1 (SPEAKING)
//   uFlowSpeed   – animated speed multiplier
//   uBaseAlpha   – animated master opacity
//   uRingScale   – animated ring size
//   uGlowIntens  – animated outer glow strength
//
// Improvements over previous version:
//   + fBm (3-octave) organic noise — nebula/cloud texture on core
//   + Smooth state color crossfade via uAnimState (no more hard if-branches)
//   + Chromatic aberration — subtle RGB fringe at radius edge
//   + LISTENING: 3 expanding sonar rings keyed to amplitude
//   + SPEAKING:  8 radial frequency bars + wave shimmer
//   + IDLE:      slow breath using smooth sin
//   + Vignette:  centre depth darkening for 3D iris feel
//   + Hard radial clip (0.005 instead of 0.01) — no corona bleed
// ---------------------------------------------------------------------------
private val IRIS_AGSL_SHADER = """
uniform float  iTime;
uniform float2 iResolution;
uniform float4 uPrimary;
uniform float4 uGradEnd;
uniform float4 uSecondary;
uniform float  uState;
uniform float  uAnimState;
uniform float  uAmplitude;
uniform float  uTtsProgress;
uniform float  uFlowSpeed;
uniform float  uBaseAlpha;
uniform float  uRingScale;
uniform float  uGlowIntens;

// ── noise ──────────────────────────────────────────────────────────────────

float hash2(float2 p) {
    p = fract(p * float2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash2(i),                   hash2(i + float2(1.0, 0.0)), u.x),
        mix(hash2(i + float2(0.0, 1.0)), hash2(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

// 3-octave fBm — organic nebula texture (kept lightweight for mobile GPU)
float fbm(float2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p  = p * 2.1 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// ── color helpers ──────────────────────────────────────────────────────────

float4 mix4(float4 a, float4 b, float t) {
    return a + (b - a) * clamp(t, 0.0, 1.0);
}

// Smooth sequential blend through all 4 states using uAnimState:
//   0 → IDLE (primary)
//   1 → LISTENING (gradEnd)
//   2 → THINKING (primary)
//   3 → SPEAKING (secondary)
float4 stateColor(float st) {
    float4 col = uPrimary;
    col = mix4(col, uGradEnd,   clamp(st,       0.0, 1.0));
    col = mix4(col, uPrimary,   clamp(st - 1.0, 0.0, 1.0));
    col = mix4(col, uSecondary, clamp(st - 2.0, 0.0, 1.0));
    return col;
}

// ── main ───────────────────────────────────────────────────────────────────
half4 main(float2 fragCoord) {
    float2 uv   = fragCoord / iResolution;
    float2 p    = uv - float2(0.5);
    float  ar   = iResolution.x / iResolution.y;
    p.x        *= ar;

    float t    = iTime;
    float s    = uFlowSpeed;
    float ir   = 0.42 * uRingScale;
    float dist = length(p);

    float4 col = stateColor(uAnimState);

    // ── fBm organic core texture ──────────────────────────────────────────
    // Two fbm passes (domain-warped) — slow drift, no repeating pattern
    float2 noiseP = p * 2.8 + float2(t * 0.07 * s, t * 0.05 * s);
    float  fbmVal = fbm(noiseP + fbm(noiseP + float2(t * 0.035 * s, 0.0)));
    float  organic = fbmVal * 0.38 + 0.62; // 0.62..1.0 subtle modulation

    // ── Lissajous fluid sources ───────────────────────────────────────────
    float2 aP = float2(
        sin(t * 0.37 * s) * 0.35 + sin(t * 0.90 * s) * 0.12,
        cos(t * 0.53 * s) * 0.35 + cos(t * 0.73 * s) * 0.12
    ) * ir;
    float2 bP = float2(
        cos(t * 0.43 * s + 2.1) * 0.32 + cos(t * 0.92 * s + 1.2) * 0.10,
        sin(t * 0.61 * s + 2.1) * 0.32 + sin(t * 0.56 * s + 1.2) * 0.10
    ) * ir;
    float2 cP = float2(sin(t * 0.71 * s + 3.8), cos(t * 0.49 * s + 3.8)) * ir * 0.24;
    float2 dP = float2(cos(t * 0.79 * s + 5.2), sin(t * 0.83 * s + 5.2)) * ir * 0.15;

    float neuralFire = (
        sin(t * 5.3 * s) * 0.5 +
        sin(t * 11.7 * s + 1.4) * 0.3 +
        sin(t * 19.3 * s + 3.7) * 0.2
    ) * 0.5 + 0.5;
    float fireBoost = 1.0 + neuralFire * 0.35;

    float dA = 1.0 - clamp(length(p - aP) / ir,          0.0, 1.0); dA *= dA;
    float dB = 1.0 - clamp(length(p - bP) / (ir * 0.9),  0.0, 1.0); dB *= dB;
    float dC = 1.0 - clamp(length(p - cP) / (ir * 0.55), 0.0, 1.0); dC *= dC;
    float dD = 1.0 - clamp(length(p - dP) / (ir * 0.20), 0.0, 1.0); dD *= dD;

    // Hard radial clip — no bleed outside circle
    float clip = 1.0 - clamp((dist - ir) / 0.005, 0.0, 1.0);

    float coreAlpha =
        dA * 0.55 * fireBoost
      + dB * 0.45 * fireBoost
      + dC * 0.30
      + dD * 0.50 * (neuralFire * 0.5 + 0.5)
      + 0.12;
    coreAlpha *= clip * uBaseAlpha * organic;
    coreAlpha  = clamp(coreAlpha, 0.0, 0.72);

    float4 coreColor = mix4(col, uGradEnd, dB / (dA + dB + 0.001));
    float4 result    = coreColor * coreAlpha;

    // ── IDLE: slow breath ─────────────────────────────────────────────────
    float idleT = clamp(1.0 - uAnimState, 0.0, 1.0);
    result *= 1.0 + sin(t * 1.8) * 0.05 * idleT;

    // ── neural wave rings ─────────────────────────────────────────────────
    for (int i = 0; i < 4; i++) {
        float phase  = float(i) * 1.6;
        float wR     = ir * (0.15 + float(i) * 0.24 + sin(t * 1.8 * s + phase) * 0.06);
        float rSDF   = abs(dist - wR) - 0.002;
        float ringA  = (1.0 - clamp(rSDF / 0.004, 0.0, 1.0)) * 0.07 * uBaseAlpha;
        result += col * ringA;
    }

    // ── LISTENING: 3 expanding sonar rings ───────────────────────────────
    float listenT = clamp(1.0 - abs(uAnimState - 1.0), 0.0, 1.0);
    if (listenT > 0.01 && uAmplitude > 0.01) {
        for (int i = 0; i < 3; i++) {
            float fi    = float(i);
            float phase = fract(t * 0.45 + fi * 0.333);          // staggered expand
            float ringR = ir + phase * ir * 0.55 * (0.4 + uAmplitude * 0.6);
            float fade  = (1.0 - phase) * uAmplitude * listenT;
            float rSDF  = abs(dist - ringR) - 0.003;
            float rA    = (1.0 - clamp(rSDF / 0.005, 0.0, 1.0)) * fade * 0.55;
            result += uGradEnd * rA;
        }
    }

    // ── THINKING: handled on Compose side (arc + spark) ──────────────────
    // No additional shader work needed — arc rotation is a CPU draw call.

    // ── SPEAKING: 8 radial frequency bars + wave shimmer ─────────────────
    float speakT = clamp(uAnimState - 2.0, 0.0, 1.0);
    if (speakT > 0.01) {
        float angle  = atan(p.y, p.x);                            // -PI..PI
        float barIdx = floor((angle + 3.14159265) / (6.28318530 / 8.0));
        float barH   = sin(t * 9.0 + barIdx * 0.785 + uTtsProgress * 5.0) * 0.5 + 0.5;
        float barR   = ir * (0.82 + barH * 0.20);
        float inBar  = step(ir * 0.82, dist) * step(dist, barR) * clip;
        result += col * (inBar * 0.22 * speakT * uBaseAlpha);

        // Wave shimmer ring over bars
        float waveR = ir * (1.0 + (sin(t * 11.0 + uTtsProgress * 6.0) * 0.5 + 0.5) * 0.035);
        float wSDF  = abs(dist - waveR) - 0.003;
        float wA    = (1.0 - clamp(wSDF / 0.005, 0.0, 1.0)) * 0.18 * speakT;
        result += col * wA;
    }

    // ── outer glow (outside clip radius) ─────────────────────────────────
    float glowDist  = dist - ir;
    float glowAlpha = clamp(1.0 - glowDist / (ir * 0.35), 0.0, 1.0);
    glowAlpha = glowAlpha * glowAlpha * uGlowIntens * uBaseAlpha * 0.6;
    if (glowDist > 0.0) result += col * glowAlpha;

    // ── chromatic aberration — subtle RGB fringe towards edge ─────────────
    // Warm center (red+), cool edge (blue-) — 1-line premium effect
    float aberr  = clamp(dist / ir - 0.55, 0.0, 1.0) * 0.022;
    result.r    *= 1.0 + aberr;
    result.b    *= 1.0 - aberr * 0.65;

    // ── centre depth vignette — 3D iris feel ─────────────────────────────
    float centerDark = 1.0 - clamp((0.18 - dist / ir * 0.4), 0.0, 0.18);
    result.rgb *= centerDark;

    return half4(result.rgb, clamp(result.a, 0.0, 1.0));
}
""".trimIndent()

// ---------------------------------------------------------------------------
// Public composable
// ---------------------------------------------------------------------------

/**
 * Iris Core Animation — GPU-accelerated on Android 13+, Canvas fallback below.
 *
 * Changes from previous version:
 *   FIXED  — `by lazy` replaced with plain `val` (lazy never updated on recompose)
 *   FIXED  — `withFrameNanos` replaces `delay(16)` for frame-accurate delta time
 *   FIXED  — `clipPath` now reuses a `remember`ed Path — no allocation per frame
 *   ADDED  — `uAnimState` smooth float drives state color crossfade in shader
 *   ADDED  — fBm organic noise texture on core
 *   ADDED  — Chromatic aberration at radius edge
 *   ADDED  — LISTENING: 3 sonar rings expand with amplitude
 *   ADDED  — SPEAKING: 8 radial frequency bars
 *   ADDED  — THINKING: spark dot at arc leading edge (Compose draw call)
 *   ADDED  — Centre depth vignette
 *   ADDED  — FastOutSlowInEasing on all animated values
 *
 * UNTESTED — verify on-device (Adreno / Mali / PowerVR may render shader subtly differently).
 */
@Composable
fun IrisCoreAnimation(
    modifier    : Modifier        = Modifier,
    state       : IrisCoreState   = IrisCoreState.IDLE,
    amplitude   : Float           = 0f,
    ttsProgress : Float           = 0f,
    coreSize    : Dp              = Constants.IRIS_CORE_SIZE.dp
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val secondary   = IrisTheme.colors.secondary

    // ── per-state target values — plain val, recomputed each recomposition ──
    // BUG FIX: was `by lazy` which only computed once and never updated.
    val targetBaseAlpha = when (state) {
        IrisCoreState.IDLE      -> 0.62f
        IrisCoreState.LISTENING -> 0.88f
        IrisCoreState.THINKING  -> 0.92f
        IrisCoreState.SPEAKING  -> 0.78f
    }
    val targetFlowSpeed = when (state) {
        IrisCoreState.IDLE      -> 1.0f
        IrisCoreState.LISTENING -> 1.6f
        IrisCoreState.THINKING  -> 2.6f
        IrisCoreState.SPEAKING  -> 1.9f
    }
    val targetRingScale = when (state) {
        IrisCoreState.IDLE      -> 1.00f
        IrisCoreState.LISTENING -> 1.03f
        IrisCoreState.THINKING  -> 1.00f
        IrisCoreState.SPEAKING  -> 1.02f
    }
    val targetRingSweep = when (state) {
        IrisCoreState.THINKING  -> 270f
        else                    -> 360f
    }
    val targetGlow = when (state) {
        IrisCoreState.IDLE      -> 0.18f
        IrisCoreState.LISTENING -> 0.30f
        IrisCoreState.THINKING  -> 0.36f
        IrisCoreState.SPEAKING  -> 0.24f
    }
    val targetThickness = when (state) {
        IrisCoreState.LISTENING -> 5.0f
        else                    -> 3.0f
    }

    val easing  = FastOutSlowInEasing
    val baseAlpha  by animateFloatAsState(targetBaseAlpha,           tween(400, easing = easing), label = "alpha")
    val flowSpeed  by animateFloatAsState(targetFlowSpeed,           tween(600, easing = easing), label = "flow")
    val ringScale  by animateFloatAsState(targetRingScale,           tween(500, easing = easing), label = "scale")
    val ringSweep  by animateFloatAsState(targetRingSweep,           tween(600, easing = easing), label = "sweep")
    val glowIntens by animateFloatAsState(targetGlow,                tween(400, easing = easing), label = "glow")
    val ringThick  by animateFloatAsState(targetThickness,           tween(400, easing = easing), label = "thick")
    // Smooth ordinal — drives color crossfade in shader (0.0 IDLE → 3.0 SPEAKING)
    val animState  by animateFloatAsState(state.ordinal.toFloat(),   tween(600, easing = easing), label = "state")

    // Frame-accurate time via withFrameNanos — no drift, no JVM scheduler jitter.
    // BUG FIX: replaces `delay(16L) + += 0.016f` which accumulated drift over time.
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    timeState.floatValue += (nanos - lastNanos) / 1_000_000_000f
                }
                lastNanos = nanos
            }
        }
    }

    // Remembered Path for circular clip — reset+reuse each frame, zero allocation.
    // BUG FIX: previous clipToCircle() created a new Path() every draw call.
    val circleClipPath = remember { Path() }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // ── AGSL path (Android 13+) ──────────────────────────────────────
        val shader = remember { android.graphics.RuntimeShader(IRIS_AGSL_SHADER) }

        Canvas(modifier = modifier.size(coreSize)) {
            val time = timeState.floatValue
            val cx   = size.width  / 2f
            val cy   = size.height / 2f

            shader.setFloatUniform("iTime",        time)
            shader.setFloatUniform("iResolution",  size.width, size.height)
            shader.setFloatUniform("uPrimary",     primary.red,     primary.green,     primary.blue,     primary.alpha)
            shader.setFloatUniform("uGradEnd",     gradientEnd.red, gradientEnd.green, gradientEnd.blue, gradientEnd.alpha)
            shader.setFloatUniform("uSecondary",   secondary.red,   secondary.green,   secondary.blue,   secondary.alpha)
            shader.setFloatUniform("uState",       state.ordinal.toFloat())
            shader.setFloatUniform("uAnimState",   animState)
            shader.setFloatUniform("uAmplitude",   amplitude.coerceIn(0f, 1f))
            shader.setFloatUniform("uTtsProgress", ttsProgress.coerceIn(0f, 1f))
            shader.setFloatUniform("uFlowSpeed",   flowSpeed)
            shader.setFloatUniform("uBaseAlpha",   baseAlpha)
            shader.setFloatUniform("uRingScale",   ringScale)
            shader.setFloatUniform("uGlowIntens",  glowIntens)

            // Reuse remembered Path — no new object created this frame
            circleClipPath.reset()
            circleClipPath.addOval(Rect(center = Offset(cx, cy), radius = size.minDimension / 2f))

            clipPath(circleClipPath) {
                drawRect(brush = ShaderBrush(shader), size = size)
            }

            // ── Outer ring arc (1 CPU draw call on top of shader) ─────────
            val arcRadius = size.minDimension * 0.42f * ringScale
            val arcAngle  = if (state == IrisCoreState.THINKING) (time * 90f) % 360f else 0f
            val sweep     = if (state == IrisCoreState.IDLE) 360f else ringSweep

            drawIrisRingArc(
                cx          = cx,         cy          = cy,
                radius      = arcRadius,  thickness   = ringThick,
                sweepAngle  = sweep,      startAngle  = arcAngle,
                primary     = primary,    gradientEnd = gradientEnd,
                alpha       = baseAlpha,  isThinking  = state == IrisCoreState.THINKING
            )

            // ── THINKING: spark at arc leading edge ───────────────────────
            if (state == IrisCoreState.THINKING) {
                val sparkRad = Math.toRadians((arcAngle + sweep).toDouble())
                val sparkX   = cx + arcRadius * cos(sparkRad).toFloat()
                val sparkY   = cy + arcRadius * sin(sparkRad).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = (baseAlpha * 1.0f).coerceAtMost(1f)),
                            primary.copy(alpha = baseAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(sparkX, sparkY),
                        radius = ringThick * 3.5f
                    ),
                    radius = ringThick * 3.5f,
                    center = Offset(sparkX, sparkY)
                )
            }
        }

    } else {
        // ── Canvas fallback (Android 12 and below) ───────────────────────
        IrisCoreCanvasFallback(
            modifier       = modifier,
            state          = state,
            amplitude      = amplitude,
            ttsProgress    = ttsProgress,
            coreSize       = coreSize,
            primary        = primary,
            gradientEnd    = gradientEnd,
            secondary      = secondary,
            baseAlpha      = baseAlpha,
            flowSpeed      = flowSpeed,
            ringScale      = ringScale,
            ringSweep      = ringSweep,
            glowIntens     = glowIntens,
            ringThick      = ringThick,
            timeState      = timeState,
            circleClipPath = circleClipPath
        )
    }
}

// ---------------------------------------------------------------------------
// Shared ring-arc helper — used by both AGSL and Canvas paths
// ---------------------------------------------------------------------------
private fun DrawScope.drawIrisRingArc(
    cx: Float, cy: Float,
    radius: Float, thickness: Float,
    sweepAngle: Float, startAngle: Float,
    primary: Color, gradientEnd: Color,
    alpha: Float, isThinking: Boolean
) {
    val tl  = Offset(cx - radius, cy - radius)
    val sz  = Size(radius * 2f, radius * 2f)

    drawArc(
        brush = Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha     = (alpha * 0.95f).coerceAtMost(1f)),
                gradientEnd.copy(alpha = (alpha * 0.55f).coerceAtMost(1f)),
                primary.copy(alpha     = (alpha * 0.95f).coerceAtMost(1f))
            ),
            start = Offset(cx - radius, cy - radius),
            end   = Offset(cx + radius, cy + radius)
        ),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter  = false,
        style      = Stroke(width = thickness, cap = StrokeCap.Round),
        topLeft    = tl,
        size       = sz
    )

    // Leading-edge brightener on THINKING arc
    if (isThinking) {
        drawArc(
            color      = primary.copy(alpha = alpha.coerceAtMost(1f)),
            startAngle = startAngle + sweepAngle - 10f,
            sweepAngle = 10f,
            useCenter  = false,
            style      = Stroke(width = thickness + 2f, cap = StrokeCap.Round),
            topLeft    = tl,
            size       = sz
        )
    }
}

// ---------------------------------------------------------------------------
// Canvas fallback — Android 12 and below
// Matches AGSL visual design as closely as possible on CPU.
// ---------------------------------------------------------------------------
@Composable
private fun IrisCoreCanvasFallback(
    modifier       : Modifier,
    state          : IrisCoreState,
    amplitude      : Float,
    ttsProgress    : Float,
    coreSize       : Dp,
    primary        : Color,
    gradientEnd    : Color,
    secondary      : Color,
    baseAlpha      : Float,
    flowSpeed      : Float,
    ringScale      : Float,
    ringSweep      : Float,
    glowIntens     : Float,
    ringThick      : Float,
    timeState      : androidx.compose.runtime.MutableFloatState,
    circleClipPath : Path
) {
    val ringColor = remember(state, primary, gradientEnd, secondary) {
        when (state) {
            IrisCoreState.IDLE      -> primary
            IrisCoreState.LISTENING -> gradientEnd
            IrisCoreState.THINKING  -> primary
            IrisCoreState.SPEAKING  -> secondary
        }
    }

    Canvas(modifier = modifier.size(coreSize)) {
        val time   = timeState.floatValue
        val cx     = size.width  / 2f
        val cy     = size.height / 2f
        val minDim = size.minDimension
        val s      = flowSpeed

        val idlePulse  = if (state == IrisCoreState.IDLE)     sin(time * 1.8f) * 0.03f else 0f
        val idleAlpha  = if (state == IrisCoreState.IDLE)     sin(time * 1.8f) * 0.05f else 0f
        val thinkRot   = if (state == IrisCoreState.THINKING) (time * 90f) % 360f      else 0f
        val speakWave  = if (state == IrisCoreState.SPEAKING) sin(time * 11f + ttsProgress * 6f) * 0.5f else 0f

        val r     = (minDim * 0.42f * ringScale + idlePulse * minDim * 0.5f + speakWave * 0.02f * minDim).coerceAtLeast(1f)
        val ir    = (r - ringThick / 2f).coerceAtLeast(1f)
        val alpha = (baseAlpha + idleAlpha).coerceIn(0f, 1f)

        // Clip to circle — reuse remembered Path
        circleClipPath.reset()
        circleClipPath.addOval(Rect(center = Offset(cx, cy), radius = size.minDimension / 2f))

        clipPath(circleClipPath) {
            // ── outer glow ────────────────────────────────────────────
            val glowR = r + ringThick * 4f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = (alpha * glowIntens * 0.8f).coerceAtMost(1f)),
                        ringColor.copy(alpha = (alpha * glowIntens * 0.18f).coerceAtMost(1f)),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy), radius = glowR
                ),
                radius = glowR, center = Offset(cx, cy)
            )

            // ── neural fire modulation ─────────────────────────────────
            val neuralFire = (
                sin(time * 5.3f * s) * 0.5f +
                sin(time * 11.7f * s + 1.4f) * 0.3f +
                sin(time * 19.3f * s + 3.7f) * 0.2f
            ) * 0.5f + 0.5f
            val fireBoost = 1f + neuralFire * 0.35f

            val aCx = cx + (sin(time * 0.37f * s) * 0.35f + sin(time * 0.90f * s) * 0.12f) * ir
            val aCy = cy + (cos(time * 0.53f * s) * 0.35f + cos(time * 0.73f * s) * 0.12f) * ir
            val bCx = cx + (cos(time * 0.43f * s + 2.1f) * 0.32f + cos(time * 0.92f * s + 1.2f) * 0.10f) * ir
            val bCy = cy + (sin(time * 0.61f * s + 2.1f) * 0.32f + sin(time * 0.56f * s + 1.2f) * 0.10f) * ir
            val cCx = cx + sin(time * 0.71f * s + 3.8f) * ir * 0.24f
            val cCy = cy + cos(time * 0.49f * s + 3.8f) * ir * 0.24f
            val dCx = cx + cos(time * 0.79f * s + 5.2f) * ir * 0.15f
            val dCy = cy + sin(time * 0.83f * s + 5.2f) * ir * 0.15f

            // ambient base
            drawCircle(color = ringColor.copy(alpha = (alpha * 0.14f).coerceAtMost(0.18f)), radius = ir, center = Offset(cx, cy))

            // Source A
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to ringColor.copy(alpha  = (0.52f * fireBoost * alpha).coerceIn(0f, 0.68f)),
                        0.3f to gradientEnd.copy(alpha = (0.28f * fireBoost * alpha).coerceIn(0f, 0.42f)),
                        0.6f to ringColor.copy(alpha  = (0.12f * alpha).coerceAtMost(0.16f)),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(aCx, aCy), radius = ir
                ),
                radius = ir, center = Offset(cx, cy)
            )
            // Source B
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to gradientEnd.copy(alpha = (0.46f * fireBoost * alpha).coerceIn(0f, 0.58f)),
                        0.4f to ringColor.copy(alpha   = (0.22f * fireBoost * alpha).coerceIn(0f, 0.32f)),
                        0.7f to gradientEnd.copy(alpha = (0.08f * alpha).coerceAtMost(0.13f)),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(bCx, bCy), radius = ir * 0.9f
                ),
                radius = ir * 0.9f, center = Offset(cx, cy)
            )
            // Source C
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = (0.30f * alpha).coerceIn(0f, 0.36f)), Color.Transparent),
                    center = Offset(cCx, cCy), radius = ir * 0.55f
                ),
                radius = ir * 0.55f, center = Offset(cCx, cCy)
            )
            // Source D shimmer
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = (0.52f * (neuralFire * 0.5f + 0.5f) * alpha).coerceIn(0f, 0.58f)),
                        Color.Transparent
                    ),
                    center = Offset(dCx, dCy), radius = ir * 0.20f
                ),
                radius = ir * 0.20f, center = Offset(dCx, dCy)
            )

            // ── neural wave rings ──────────────────────────────────────
            for (i in 0..3) {
                val phase = i * 1.6f
                val wR    = ir * (0.15f + i * 0.24f + sin(time * 1.8f * s + phase) * 0.06f)
                val wA    = (0.05f + sin(time * 2.2f * s + phase) * 0.025f) * alpha
                drawCircle(
                    color  = ringColor.copy(alpha = wA.coerceIn(0f, 0.10f)),
                    radius = wR, center = Offset(cx, cy),
                    style  = Stroke(width = 1f)
                )
            }

            // ── LISTENING: 3 sonar ripples ─────────────────────────────
            if (state == IrisCoreState.LISTENING && amplitude > 0.01f) {
                for (i in 0..2) {
                    val phase  = ((time * 0.45f + i * 0.333f) % 1f)
                    val ripR   = r + phase * r * 0.55f * (0.4f + amplitude * 0.6f)
                    val ripA   = (1f - phase) * amplitude * 0.45f
                    drawCircle(
                        color  = gradientEnd.copy(alpha = ripA.coerceIn(0f, 0.35f)),
                        radius = ripR, center = Offset(cx, cy),
                        style  = Stroke(width = 1.5f)
                    )
                }
            }
        }

        // ── outer ring arc (outside clip so it sits on edge cleanly) ──
        val sweep = if (state == IrisCoreState.IDLE) 360f else ringSweep
        drawIrisRingArc(
            cx          = cx,     cy          = cy,
            radius      = r,      thickness   = ringThick + speakWave * 1.5f,
            sweepAngle  = sweep,  startAngle  = thinkRot,
            primary     = ringColor, gradientEnd = gradientEnd,
            alpha       = alpha,  isThinking  = state == IrisCoreState.THINKING
        )

        // ── THINKING: spark at arc leading edge ────────────────────────
        if (state == IrisCoreState.THINKING) {
            val sparkRad = Math.toRadians((thinkRot + sweep).toDouble())
            val sparkX   = cx + r * cos(sparkRad).toFloat()
            val sparkY   = cy + r * sin(sparkRad).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = alpha),
                        primary.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(sparkX, sparkY),
                    radius = ringThick * 3.5f
                ),
                radius = ringThick * 3.5f,
                center = Offset(sparkX, sparkY)
            )
        }
    }
}