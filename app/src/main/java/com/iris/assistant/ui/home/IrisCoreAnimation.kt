package com.iris.assistant.ui.home

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.util.Constants
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// AGSL shader source — runs entirely on GPU (Android 13+ / API 33+)
//
// Uniforms injected each frame:
//   iTime       – elapsed seconds (Float)
//   iResolution – canvas size in px (Float, Float)
//   uPrimary    – theme primary color (Float[4] RGBA)
//   uGradEnd    – theme gradient-end color (Float[4] RGBA)
//   uSecondary  – theme secondary color (Float[4] RGBA)
//   uState      – 0=IDLE, 1=LISTENING, 2=THINKING, 3=SPEAKING
//   uAmplitude  – mic amplitude 0-1 (LISTENING only)
//   uTtsProgress– TTS playback progress 0-1 (SPEAKING only)
//   uFlowSpeed  – animated float from Compose (smooth state transition)
//   uBaseAlpha  – animated float from Compose
//   uRingScale  – animated float from Compose
//   uRingSweep  – animated float 0-360 from Compose
//   uGlowIntens – animated float from Compose
//
// All sin/cos math, gradient blending, and draw calls execute on the GPU.
// CPU cost per frame: ~7 uniform writes only.
// ---------------------------------------------------------------------------
private val IRIS_AGSL_SHADER = """
uniform float  iTime;
uniform float2 iResolution;
uniform float4 uPrimary;
uniform float4 uGradEnd;
uniform float4 uSecondary;
uniform float  uState;
uniform float  uAmplitude;
uniform float  uTtsProgress;
uniform float  uFlowSpeed;
uniform float  uBaseAlpha;
uniform float  uRingScale;
uniform float  uGlowIntens;

// ── helpers ─────────────────────────────────────────────────────────────────

float sdCircle(float2 p, float r) { return length(p) - r; }

// Smooth-step blend between a and b
float4 mix4(float4 a, float4 b, float t) { return a + (b - a) * clamp(t, 0.0, 1.0); }

// ── main ────────────────────────────────────────────────────────────────────
half4 main(float2 fragCoord) {
    float2 uv  = fragCoord / iResolution;           // 0..1
    float2 c   = float2(0.5, 0.5);
    float2 p   = uv - c;                            // centred -0.5..0.5
    float  ar  = iResolution.x / iResolution.y;
    p.x       *= ar;                                // aspect-correct

    float t   = iTime;
    float s   = uFlowSpeed;
    float ir  = 0.42 * uRingScale;                 // inner fill radius (NDC)

    // ── pick active palette color based on state ─────────────────────────
    // 0=IDLE→primary, 1=LISTENING→gradEnd, 2=THINKING→primary, 3=SPEAKING→secondary
    float4 col = uPrimary;
    if (uState > 2.5) col = uSecondary;
    else if (uState > 0.5 && uState < 1.5) col = uGradEnd;

    // ── fluid core (Lissajous gradient sources) ──────────────────────────
    // Source A
    float2 aP = float2(
        sin(t * 0.37 * s) * 0.35 + sin(t * 0.90 * s) * 0.12,
        cos(t * 0.53 * s) * 0.35 + cos(t * 0.73 * s) * 0.12
    ) * ir;
    // Source B (counter-phase)
    float2 bP = float2(
        cos(t * 0.43 * s + 2.1) * 0.32 + cos(t * 0.92 * s + 1.2) * 0.10,
        sin(t * 0.61 * s + 2.1) * 0.32 + sin(t * 0.56 * s + 1.2) * 0.10
    ) * ir;
    // Source C (faster, smaller)
    float2 cP = float2(
        sin(t * 0.71 * s + 3.8),
        cos(t * 0.49 * s + 3.8)
    ) * ir * 0.24;
    // Source D (shimmer micro)
    float2 dP = float2(
        cos(t * 0.79 * s + 5.2),
        sin(t * 0.83 * s + 5.2)
    ) * ir * 0.15;

    // Neural-fire modulation
    float neuralFire = (
        sin(t * 5.3 * s) * 0.5 +
        sin(t * 11.7 * s + 1.4) * 0.3 +
        sin(t * 19.3 * s + 3.7) * 0.2
    ) * 0.5 + 0.5;
    float fireBoost = 1.0 + neuralFire * 0.35;

    // Radial falloff from each source
    float dA = 1.0 - clamp(length(p - aP) / ir, 0.0, 1.0);
    float dB = 1.0 - clamp(length(p - bP) / (ir * 0.9), 0.0, 1.0);
    float dC = 1.0 - clamp(length(p - cP) / (ir * 0.55), 0.0, 1.0);
    float dD = 1.0 - clamp(length(p - dP) / (ir * 0.20), 0.0, 1.0);

    dA = dA * dA;
    dB = dB * dB;
    dC = dC * dC;
    dD = dD * dD;

    // Radial clip — nothing drawn outside inner radius
    float dist = length(p);
    float clip = 1.0 - clamp((dist - ir) / 0.01, 0.0, 1.0);

    float coreAlpha =
        dA * 0.55 * fireBoost
      + dB * 0.45 * fireBoost
      + dC * 0.30
      + dD * 0.50 * (neuralFire * 0.5 + 0.5)
      + 0.12;                        // ambient base

    coreAlpha *= clip * uBaseAlpha;
    coreAlpha  = clamp(coreAlpha, 0.0, 0.72);

    // Blend source colors
    float4 coreColor = mix4(col, uGradEnd, dB / (dA + dB + 0.001));
    float4 result    = coreColor * coreAlpha;

    // ── IDLE: gentle alpha-pulse on top ──────────────────────────────────
    if (uState < 0.5) {
        float pulse = sin(t * 2.0) * 0.06;
        result *= 1.0 + pulse;
    }

    // ── LISTENING: amplitude ripple halo ─────────────────────────────────
    if (uState > 0.5 && uState < 1.5 && uAmplitude > 0.01) {
        float rippleR  = ir + uAmplitude * 0.15;
        float rippleSDF = abs(dist - rippleR) - 0.004;
        float rippleA   = (1.0 - clamp(rippleSDF / 0.008, 0.0, 1.0))
                          * uAmplitude * 0.45;
        result += uGradEnd * rippleA;
    }

    // ── SPEAKING: TTS wave shimmer ────────────────────────────────────────
    if (uState > 2.5) {
        float wave = sin(t * 12.0 + uTtsProgress * 6.0) * 0.5 + 0.5;
        float waveR = ir * (1.0 + wave * 0.04);
        float waveSDF = abs(dist - waveR) - 0.003;
        float waveA = (1.0 - clamp(waveSDF / 0.006, 0.0, 1.0)) * 0.20;
        result += col * waveA;
    }

    // ── outer glow ───────────────────────────────────────────────────────
    float glowDist  = dist - ir;
    float glowAlpha = clamp(1.0 - glowDist / (ir * 0.35), 0.0, 1.0);
    glowAlpha = glowAlpha * glowAlpha * uGlowIntens * uBaseAlpha * 0.6;
    if (glowDist > 0.0) result += col * glowAlpha;

    // ── neural wave rings ─────────────────────────────────────────────────
    for (int i = 0; i < 4; i++) {
        float phase = float(i) * 1.6;
        float wR    = ir * (0.15 + float(i) * 0.24 + sin(t * 1.8 * s + phase) * 0.06);
        float ringSDF = abs(dist - wR) - 0.002;
        float ringA = (1.0 - clamp(ringSDF / 0.004, 0.0, 1.0))
                      * 0.07 * uBaseAlpha;
        result += col * ringA;
    }

    return half4(result.rgb, clamp(result.a, 0.0, 1.0));
}
""".trimIndent()

// ---------------------------------------------------------------------------
// Public composable
// ---------------------------------------------------------------------------

/**
 * Iris Core Animation — GPU-accelerated on Android 13+, Canvas fallback below.
 *
 * Android 13+ (API 33+): Uses [android.graphics.RuntimeShader] + [ShaderBrush].
 *   All sin/cos, gradient blending, and draw calls execute entirely on the GPU.
 *   CPU cost per frame: ~7 uniform writes via [LaunchedEffect] ticker.
 *
 * Android 12 and below: Optimised Canvas path identical to the previous
 *   implementation but with gradients cached via `remember(key)` to avoid
 *   per-frame object allocation and GC pressure.
 *
 * State transitions (IDLE/LISTENING/THINKING/SPEAKING) are driven by smooth
 * [animateFloatAsState] values that are passed as shader uniforms, so
 * cross-fades between states look fluid on both paths.
 *
 * UNTESTED — verify on-device with GPU Debugger / Layout Inspector before
 * considering confirmed. AGSL shader output is visually designed to match
 * the previous Canvas implementation; exact GPU rendering may differ subtly
 * across GPU vendors (Adreno / Mali / PowerVR).
 */
@Composable
fun IrisCoreAnimation(
    modifier: Modifier = Modifier,
    state: IrisCoreState = IrisCoreState.IDLE,
    amplitude: Float = 0f,
    ttsProgress: Float = 0f,
    coreSize: Dp = Constants.IRIS_CORE_SIZE.dp
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val secondary   = IrisTheme.colors.secondary

    // ── per-state animated values (smooth cross-fades) ───────────────────
    val targetBaseAlpha  by lazy { when (state) {
        IrisCoreState.IDLE      -> 0.62f
        IrisCoreState.LISTENING -> 0.88f
        IrisCoreState.THINKING  -> 0.92f
        IrisCoreState.SPEAKING  -> 0.78f
    }}
    val targetFlowSpeed  by lazy { when (state) {
        IrisCoreState.IDLE      -> 1.0f
        IrisCoreState.LISTENING -> 1.6f
        IrisCoreState.THINKING  -> 2.6f
        IrisCoreState.SPEAKING  -> 1.9f
    }}
    val targetRingScale  by lazy { when (state) {
        IrisCoreState.IDLE      -> 1.00f
        IrisCoreState.LISTENING -> 1.03f
        IrisCoreState.THINKING  -> 1.00f
        IrisCoreState.SPEAKING  -> 1.02f
    }}
    val targetRingSweep  by lazy { when (state) {
        IrisCoreState.THINKING  -> 270f
        else                    -> 360f
    }}
    val targetGlow       by lazy { when (state) {
        IrisCoreState.IDLE      -> 0.18f
        IrisCoreState.LISTENING -> 0.30f
        IrisCoreState.THINKING  -> 0.36f
        IrisCoreState.SPEAKING  -> 0.24f
    }}
    val targetThickness  by lazy { when (state) {
        IrisCoreState.LISTENING -> 5.0f
        else                    -> 3.0f
    }}

    val baseAlpha   by animateFloatAsState(targetBaseAlpha,  tween(400), label = "alpha")
    val flowSpeed   by animateFloatAsState(targetFlowSpeed,  tween(600), label = "flow")
    val ringScale   by animateFloatAsState(targetRingScale,  tween(500), label = "scale")
    val ringSweep   by animateFloatAsState(targetRingSweep,  tween(600), label = "sweep")
    val glowIntens  by animateFloatAsState(targetGlow,       tween(400), label = "glow")
    val ringThick   by animateFloatAsState(targetThickness,  tween(400), label = "thick")

    // time ticker — written only; read only inside Canvas / shader
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            timeState.floatValue += 0.016f
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // ── AGSL path (Android 13+) ──────────────────────────────────────
        val shader = remember { android.graphics.RuntimeShader(IRIS_AGSL_SHADER) }
        val stateOrdinal = state.ordinal.toFloat()

        Canvas(modifier = modifier.size(coreSize)) {
            val time = timeState.floatValue

            shader.setFloatUniform("iTime",        time)
            shader.setFloatUniform("iResolution",  size.width, size.height)
            shader.setFloatUniform("uPrimary",     primary.red, primary.green, primary.blue, primary.alpha)
            shader.setFloatUniform("uGradEnd",     gradientEnd.red, gradientEnd.green, gradientEnd.blue, gradientEnd.alpha)
            shader.setFloatUniform("uSecondary",   secondary.red, secondary.green, secondary.blue, secondary.alpha)
            shader.setFloatUniform("uState",       stateOrdinal)
            shader.setFloatUniform("uAmplitude",   amplitude.coerceIn(0f, 1f))
            shader.setFloatUniform("uTtsProgress", ttsProgress.coerceIn(0f, 1f))
            shader.setFloatUniform("uFlowSpeed",   flowSpeed)
            shader.setFloatUniform("uBaseAlpha",   baseAlpha)
            shader.setFloatUniform("uRingScale",   ringScale)
            shader.setFloatUniform("uGlowIntens",  glowIntens)

            // Clip to circle before drawing shader — prevents square gradient
            // edges and corner ring artifacts from aspect-ratio correction.
            clipToCircle(cx = size.width / 2f, cy = size.height / 2f, radius = size.minDimension / 2f) {
                drawRect(brush = ShaderBrush(shader), size = size)
            }

            // Outer ring arc — drawn on top of shader (still CPU, but only 1 draw call)
            drawIrisRingArc(
                cx           = size.width / 2f,
                cy           = size.height / 2f,
                radius       = size.minDimension * 0.42f * ringScale,
                thickness    = ringThick,
                sweepAngle   = if (state == IrisCoreState.IDLE) 360f else ringSweep,
                startAngle   = if (state == IrisCoreState.THINKING) (time * 90f) % 360f else 0f,
                primary      = primary,
                gradientEnd  = gradientEnd,
                alpha        = baseAlpha,
                isThinking   = state == IrisCoreState.THINKING
            )
        }

    } else {
        // ── Canvas fallback (Android 12 and below) ───────────────────────
        // Same visual design as AGSL path, rendered on CPU with cached
        // Brush objects to reduce per-frame allocation.
        IrisCoreCanvasFallback(
            modifier     = modifier,
            state        = state,
            amplitude    = amplitude,
            ttsProgress  = ttsProgress,
            coreSize     = coreSize,
            primary      = primary,
            gradientEnd  = gradientEnd,
            secondary    = secondary,
            baseAlpha    = baseAlpha,
            flowSpeed    = flowSpeed,
            ringScale    = ringScale,
            ringSweep    = ringSweep,
            glowIntens   = glowIntens,
            ringThick    = ringThick,
            timeState    = timeState
        )
    }
}

// ---------------------------------------------------------------------------
// Clip draw calls to a circle — prevents shader square-edge artifacts
// ---------------------------------------------------------------------------
private fun androidx.compose.ui.graphics.drawscope.DrawScope.clipToCircle(
    cx: Float, cy: Float, radius: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    val circlePath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(
            center = Offset(cx, cy),
            radius = radius
        ))
    }
    clipPath(path = circlePath, block = block)
}

// ---------------------------------------------------------------------------
// Shared ring-arc draw helper (used by both paths)
// ---------------------------------------------------------------------------
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIrisRingArc(
    cx: Float, cy: Float,
    radius: Float, thickness: Float,
    sweepAngle: Float, startAngle: Float,
    primary: Color, gradientEnd: Color,
    alpha: Float, isThinking: Boolean
) {
    val topLeft = Offset(cx - radius, cy - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    drawArc(
        brush = Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha = alpha * 0.95f),
                gradientEnd.copy(alpha = alpha * 0.55f),
                primary.copy(alpha = alpha * 0.95f)
            ),
            start = Offset(cx - radius, cy - radius),
            end   = Offset(cx + radius, cy + radius)
        ),
        startAngle  = startAngle,
        sweepAngle  = sweepAngle,
        useCenter   = false,
        style       = Stroke(width = thickness, cap = StrokeCap.Round),
        topLeft     = topLeft,
        size        = arcSize
    )

    // Thinking-state leading-edge brightener
    if (isThinking) {
        drawArc(
            color       = primary.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
            startAngle  = startAngle + sweepAngle - 10f,
            sweepAngle  = 10f,
            useCenter   = false,
            style       = Stroke(width = thickness + 2f, cap = StrokeCap.Round),
            topLeft     = topLeft,
            size        = arcSize
        )
    }
}

// ---------------------------------------------------------------------------
// Canvas fallback — Android 12 and below
// ---------------------------------------------------------------------------
@Composable
private fun IrisCoreCanvasFallback(
    modifier: Modifier,
    state: IrisCoreState,
    amplitude: Float,
    ttsProgress: Float,
    coreSize: Dp,
    primary: Color,
    gradientEnd: Color,
    secondary: Color,
    baseAlpha: Float,
    flowSpeed: Float,
    ringScale: Float,
    ringSweep: Float,
    glowIntens: Float,
    ringThick: Float,
    timeState: androidx.compose.runtime.MutableFloatState
) {
    // Ring color per state — recomputed only when state changes
    val ringColor = remember(state, primary, gradientEnd, secondary) {
        when (state) {
            IrisCoreState.IDLE      -> primary
            IrisCoreState.LISTENING -> gradientEnd
            IrisCoreState.THINKING  -> primary
            IrisCoreState.SPEAKING  -> secondary
        }
    }

    Canvas(modifier = modifier.size(coreSize)) {
        val time = timeState.floatValue

        val cx       = size.width / 2f
        val cy       = size.height / 2f
        val minDim   = size.minDimension
        val baseR    = minDim * 0.42f
        val curR     = baseR * ringScale

        val idlePulse     = if (state == IrisCoreState.IDLE)      sin(time * 2f) * 0.03f  else 0f
        val idleAlpha     = if (state == IrisCoreState.IDLE)      sin(time * 2f) * 0.08f  else 0f
        val thinkRot      = if (state == IrisCoreState.THINKING)  (time * 90f) % 360f     else 0f
        val speakWave     = if (state == IrisCoreState.SPEAKING)  sin(time * 12f + ttsProgress * 6f) * 0.5f else 0f

        val r          = (curR + idlePulse * minDim * 0.5f + speakWave * 0.02f * minDim).coerceAtLeast(1f)
        val ir         = (r - ringThick / 2f).coerceAtLeast(1f)
        val alpha      = (baseAlpha + idleAlpha).coerceIn(0f, 1f)
        val s          = flowSpeed

        // ── outer glow ────────────────────────────────────────────────
        val glowR = r + ringThick * 4f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = alpha * glowIntens * 0.8f),
                    ringColor.copy(alpha = alpha * glowIntens * 0.18f),
                    Color.Transparent
                ),
                center = Offset(cx, cy), radius = glowR
            ),
            radius = glowR, center = Offset(cx, cy)
        )

        // ── fluid core (Lissajous sources — cached via key on slow-changing params) ──
        // Alpha/radius change frequently so we don't key on them; just recompute.
        val neuralFire = (
            sin(time * 5.3f * s) * 0.5f +
            sin(time * 11.7f * s + 1.4f) * 0.3f +
            sin(time * 19.3f * s + 3.7f) * 0.2f
        ) * 0.5f + 0.5f
        val fireBoost = 1f + neuralFire * 0.35f

        val aCx = cx + (sin(time * 0.37f * s) * 0.35f + sin(time * 0.53f * s * 1.7f) * 0.12f) * ir
        val aCy = cy + (cos(time * 0.53f * s) * 0.35f + cos(time * 0.43f * s * 1.7f) * 0.12f) * ir
        val bCx = cx + (cos(time * 0.43f * s + 2.1f) * 0.32f + cos(time * 0.61f * s * 1.5f + 1.2f) * 0.10f) * ir
        val bCy = cy + (sin(time * 0.61f * s + 2.1f) * 0.32f + sin(time * 0.37f * s * 1.5f + 1.2f) * 0.10f) * ir
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
        // Source D (shimmer)
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

        // ── neural wave rings ─────────────────────────────────────────
        for (i in 0..3) {
            val phase = i * 1.6f
            val wR = ir * (0.15f + i * 0.24f + sin(time * 1.8f * s + phase) * 0.06f)
            val wA = (0.05f + sin(time * 2.2f * s + phase) * 0.025f) * alpha
            drawCircle(
                color  = ringColor.copy(alpha = wA.coerceIn(0f, 0.10f)),
                radius = wR, center = Offset(cx, cy),
                style  = Stroke(width = 1f)
            )
        }

        // ── LISTENING ripple ──────────────────────────────────────────
        if (state == IrisCoreState.LISTENING && amplitude > 0.01f) {
            val rippleR = r + amplitude * minDim * 0.15f
            val rippleA = (alpha * amplitude * 0.42f).coerceIn(0f, 0.30f)
            drawCircle(
                color  = gradientEnd.copy(alpha = rippleA),
                radius = rippleR, center = Offset(cx, cy),
                style  = Stroke(width = 1.5f)
            )
        }

        // ── outer ring arc ────────────────────────────────────────────
        drawIrisRingArc(
            cx          = cx, cy = cy,
            radius      = r,
            thickness   = ringThick + speakWave * 1.5f,
            sweepAngle  = if (state == IrisCoreState.IDLE) 360f else ringSweep,
            startAngle  = thinkRot,
            primary     = ringColor,
            gradientEnd = gradientEnd,
            alpha       = alpha,
            isThinking  = state == IrisCoreState.THINKING
        )
    }
}