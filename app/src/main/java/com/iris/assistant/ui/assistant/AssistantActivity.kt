package com.iris.assistant.ui.assistant

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.service.voice.VoiceInteractionEntryPoint
import com.iris.assistant.ui.theme.IrisTheme
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PaperPlaneRightFill
import com.phosphor.icons.regular.X
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.*

private const val TAG = "AssistantActivity"

// ---------------------------------------------------------------------------
// Sweep animation phases
// ---------------------------------------------------------------------------
private enum class SweepPhase {
    SWEEPING,   // corner arcs animating
    COLLAPSING, // arcs collapsing into pill
    DONE        // pill visible, sweep canvas hidden
}

class AssistantActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── BUG FIX: Translucent activity focus issue ──────────────────────
        // windowIsTranslucent=true causes Android to sometimes not hand focus
        // to this window, making TextField untappable until screen lock/unlock.
        // Fix: explicitly take focus and ensure touch events are not blocked.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv())
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        Log.d(TAG, "onCreate")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VoiceInteractionEntryPoint::class.java
        )

        val viewModel = AssistantViewModel(
            context                = applicationContext,
            audioRecorder          = entryPoint.audioRecorder(),
            transcribeAudioUseCase = entryPoint.transcribeAudioUseCase(),
            sendMessageUseCase     = entryPoint.sendMessageUseCase(),
            ttsProvider            = entryPoint.ttsProvider()
        )

        setContent {
            IrisTheme {
                AssistantScreen(
                    viewModel = viewModel,
                    onClose   = { finish() }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root screen — scrim + sweep + pill
// ---------------------------------------------------------------------------
@Composable
private fun AssistantScreen(
    viewModel: AssistantViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    // Sweep phase state machine
    var sweepPhase by remember { mutableStateOf(SweepPhase.SWEEPING) }

    // Sweep progress 0→1
    val sweepProgress = remember { Animatable(0f) }

    // Collapse progress 0→1 (arcs → pill)
    val collapseProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Run sweep
        sweepProgress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(750, easing = FastOutSlowInEasing)
        )
        sweepPhase = SweepPhase.COLLAPSING
        // 2. Collapse arcs into pill
        collapseProgress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        )
        sweepPhase = SweepPhase.DONE
    }

    // Auto-close
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            delay(320)
            onClose()
        }
    }

    // Scrim — fades in after sweep
    val scrimAlpha by animateFloatAsState(
        targetValue   = if (sweepPhase == SweepPhase.DONE && !state.isDone) 0.45f else 0f,
        animationSpec = tween(300),
        label         = "scrim"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Scrim layer
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        viewModel.stop()
                        onClose()
                    }
            )
        }

        // Sweep canvas — covers full screen during sweep/collapse
        if (sweepPhase != SweepPhase.DONE) {
            SweepCanvas(
                progress         = sweepProgress.value,
                collapseProgress = collapseProgress.value,
                isCollapsing     = sweepPhase == SweepPhase.COLLAPSING,
                primary          = primary,
                gradientEnd      = gradientEnd,
                modifier         = Modifier.fillMaxSize()
            )
        }

        // Pill — appears after sweep
        AnimatedVisibility(
            visible  = sweepPhase == SweepPhase.DONE && !state.isDone,
            enter    = scaleIn(
                initialScale  = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(tween(200)),
            exit     = scaleOut(targetScale = 0.85f) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding()
        ) {
            AssistantPill(
                state         = state,
                onTextChanged = viewModel::onTextInputChanged,
                onSendText    = { viewModel.sendText() },
                onMicClick    = viewModel::startVoicePipeline,
                onClose       = { viewModel.stop(); onClose() }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sweep canvas — Android 13+ AGSL, else Canvas
// Draws 4 corner arcs sweeping inward, then collapses to pill center.
// ---------------------------------------------------------------------------
@Composable
private fun SweepCanvas(
    progress         : Float,
    collapseProgress : Float,
    isCollapsing     : Boolean,
    primary          : Color,
    gradientEnd      : Color,
    modifier         : Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SweepCanvasAgsl(
            progress         = progress,
            collapseProgress = collapseProgress,
            isCollapsing     = isCollapsing,
            primary          = primary,
            gradientEnd      = gradientEnd,
            modifier         = modifier
        )
    } else {
        SweepCanvasCompat(
            progress         = progress,
            collapseProgress = collapseProgress,
            isCollapsing     = isCollapsing,
            primary          = primary,
            gradientEnd      = gradientEnd,
            modifier         = modifier
        )
    }
}

// ---------------------------------------------------------------------------
// AGSL sweep — Android 13+ (API 33+)
// ---------------------------------------------------------------------------
private val SWEEP_AGSL_SHADER = """
uniform float2 iResolution;
uniform float  uProgress;
uniform float  uCollapse;
uniform float4 uPrimary;
uniform float4 uGradEnd;

half4 main(float2 fragCoord) {
    float2 uv  = fragCoord / iResolution;
    float2 p   = uv - float2(0.5);           // -0.5..0.5

    // ── Corner arc logic ──────────────────────────────────────────────────
    // Each corner: top-left, top-right, bottom-left, bottom-right
    // Arc sweeps from corner inward. "sweep angle" grows with uProgress.

    float2 corners[4];
    corners[0] = float2(-0.5, -0.5);   // TL
    corners[1] = float2( 0.5, -0.5);   // TR
    corners[2] = float2(-0.5,  0.5);   // BL
    corners[3] = float2( 0.5,  0.5);   // BR

    // Each corner arc start angle (pointing inward)
    float startAngles[4];
    startAngles[0] = 0.0;              // TL: 0°..90°
    startAngles[1] = 90.0;            // TR: 90°..180°
    startAngles[2] = 270.0;           // BL: 270°..360°
    startAngles[3] = 180.0;           // BR: 180°..270°

    float arcRadius = 0.48 * (1.0 - uCollapse * 0.9);
    float arcThick  = 0.006 * (1.0 - uCollapse * 0.5);
    float sweep     = 90.0 * uProgress;

    float alpha = 0.0;
    float colorT = 0.0;

    for (int i = 0; i < 4; i++) {
        float2 toCorner = p - corners[i];
        float  dist     = length(toCorner);
        float  angleDeg = degrees(atan(toCorner.y, toCorner.x));
        if (angleDeg < 0.0) angleDeg += 360.0;

        float start = startAngles[i];
        float end   = start + sweep;

        // Normalize angle into arc range
        float a = angleDeg;
        // Wrap for corner ranges that cross 0/360
        if (start > a) a += 360.0;

        bool inArc   = (a >= start && a <= end);
        bool onRing  = (abs(dist - arcRadius) < arcThick);

        if (inArc && onRing) {
            float arcT  = (a - start) / max(sweep, 0.001);
            float fadeT = arcT;             // fade tip
            float glow  = 1.0 - clamp(abs(dist - arcRadius) / arcThick, 0.0, 1.0);
            glow = glow * glow;
            alpha  = max(alpha, glow * (0.75 + fadeT * 0.25));
            colorT = max(colorT, arcT);
        }

        // Outer glow halo
        float haloDist = abs(dist - arcRadius);
        if (inArc && haloDist < arcThick * 4.0) {
            float haloA = clamp(1.0 - haloDist / (arcThick * 4.0), 0.0, 1.0);
            haloA = haloA * haloA * 0.18 * (1.0 - uCollapse);
            alpha = max(alpha, haloA);
        }
    }

    // Collapse: fade out as arcs disappear
    alpha *= (1.0 - uCollapse);

    float4 col = uPrimary + (uGradEnd - uPrimary) * clamp(colorT, 0.0, 1.0);
    return half4(col.rgb * alpha, alpha);
}
""".trimIndent()

@Composable
private fun SweepCanvasAgsl(
    progress         : Float,
    collapseProgress : Float,
    isCollapsing     : Boolean,
    primary          : Color,
    gradientEnd      : Color,
    modifier         : Modifier = Modifier
) {
    val shader = remember { android.graphics.RuntimeShader(SWEEP_AGSL_SHADER) }

    Canvas(modifier = modifier) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("uProgress",  progress)
        shader.setFloatUniform("uCollapse",  collapseProgress)
        shader.setFloatUniform("uPrimary",
            primary.red, primary.green, primary.blue, primary.alpha)
        shader.setFloatUniform("uGradEnd",
            gradientEnd.red, gradientEnd.green, gradientEnd.blue, gradientEnd.alpha)

        drawRect(brush = ShaderBrush(shader), size = size)
    }
}

// ---------------------------------------------------------------------------
// Canvas compat sweep — Android 12 and below
// Same visual, CPU-drawn with drawArc + Paint.
// ---------------------------------------------------------------------------
@Composable
private fun SweepCanvasCompat(
    progress         : Float,
    collapseProgress : Float,
    isCollapsing     : Boolean,
    primary          : Color,
    gradientEnd      : Color,
    modifier         : Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val arcRadius  = minOf(w, h) * 0.48f * (1f - collapseProgress * 0.9f)
        val arcThickPx = minOf(w, h) * 0.006f * (1f - collapseProgress * 0.5f)
        val sweepAngle = 90f * progress
        val globalAlpha = (1f - collapseProgress).coerceIn(0f, 1f)

        if (sweepAngle <= 0f || globalAlpha <= 0f) return@Canvas

        // Corner definitions: center, startAngle for drawArc
        data class Corner(val cx: Float, val cy: Float, val startAngle: Float)
        val corners = listOf(
            Corner(0f,  0f,  180f),   // TL — arc sweeps right+down
            Corner(w,   0f,  270f),   // TR — arc sweeps down+left
            Corner(0f,  h,   90f),    // BL — arc sweeps up+right
            Corner(w,   h,   0f)      // BR — arc sweeps up+left
        )

        corners.forEach { corner ->
            val left   = corner.cx - arcRadius
            val top    = corner.cy - arcRadius
            val rect   = Size(arcRadius * 2f, arcRadius * 2f)

            // Glow halo pass
            val haloPaint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = arcThickPx * 8f
                    shader = android.graphics.RadialGradient(
                        corner.cx, corner.cy, arcRadius,
                        intArrayOf(
                            primary.copy(alpha = 0.12f * globalAlpha).toArgb(),
                            android.graphics.Color.TRANSPARENT
                        ),
                        floatArrayOf(0.9f, 1.0f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
            }
            drawContext.canvas.nativeCanvas.drawArc(
                left, top, left + arcRadius * 2, top + arcRadius * 2,
                corner.startAngle, sweepAngle, false,
                haloPaint.asFrameworkPaint()
            )

            // Main arc — gradient from primary to gradientEnd along sweep
            drawArcGradient(
                cx          = corner.cx,
                cy          = corner.cy,
                radius      = arcRadius,
                thickness   = arcThickPx,
                startAngle  = corner.startAngle,
                sweepAngle  = sweepAngle,
                primary     = primary.copy(alpha = globalAlpha * 0.85f),
                gradientEnd = gradientEnd.copy(alpha = globalAlpha * 0.85f)
            )
        }
    }
}

// Helper: draws a gradient arc via segmented drawArc (CPU compat)
private fun DrawScope.drawArcGradient(
    cx: Float, cy: Float,
    radius: Float, thickness: Float,
    startAngle: Float, sweepAngle: Float,
    primary: Color, gradientEnd: Color
) {
    val segments = 12
    val segSweep = sweepAngle / segments
    for (i in 0 until segments) {
        val t     = i.toFloat() / segments
        val color = androidx.compose.ui.graphics.lerp(primary, gradientEnd, t)
        val left  = cx - radius
        val top   = cy - radius
        drawArc(
            color      = color,
            startAngle = startAngle + i * segSweep,
            sweepAngle = segSweep + 0.5f, // slight overlap, no gaps
            useCenter  = false,
            topLeft    = Offset(left, top),
            size       = Size(radius * 2f, radius * 2f),
            style      = Stroke(width = thickness, cap = StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// AssistantPill — the compact overlay after sweep
// Layout: [StateOrb] [Content] [CloseBtn]
// Content switches: Idle → Listening → Thinking → Reply text
// ---------------------------------------------------------------------------
@Composable
private fun AssistantPill(
    state        : AssistantUiState,
    onTextChanged: (String) -> Unit,
    onSendText   : () -> Unit,
    onMicClick   : () -> Unit,
    onClose      : () -> Unit
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape         = RoundedCornerShape(40.dp),
        color         = Color(0xFF1C1C1E),
        tonalElevation = 0.dp,
        shadowElevation = 24.dp,
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Gradient top border illusion
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            primary.copy(alpha = 0.0f),
                            primary.copy(alpha = 0.5f),
                            gradientEnd.copy(alpha = 0.5f),
                            primary.copy(alpha = 0.0f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State orb
            PillStateOrb(state = state, primary = primary, gradientEnd = gradientEnd)

            Spacer(Modifier.width(10.dp))

            // Content area
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState   = pillContentState(state),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "pillContent"
                ) { contentState ->
                    when (contentState) {
                        PillContent.IDLE      -> PillIdle()
                        PillContent.LISTENING -> PillListening(amplitude = state.amplitude)
                        PillContent.THINKING  -> PillThinking()
                        PillContent.REPLY     -> PillReply(
                            lastReply = state.messages.lastOrNull { !it.isUser }?.text ?: ""
                        )
                        PillContent.INPUT     -> PillInput(
                            text          = state.textInput,
                            onTextChanged = onTextChanged,
                            onSend        = {
                                onSendText()
                                keyboardController?.hide()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action button: mic / send
            PillActionButton(
                state      = state,
                primary    = primary,
                onMicClick = onMicClick,
                onSend     = {
                    onSendText()
                    keyboardController?.hide()
                }
            )

            Spacer(Modifier.width(6.dp))

            // Close
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = PhIcons.Regular.X,
                    contentDescription = "Kapat",
                    tint               = ColorTextSecondary,
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pill content state enum
// ---------------------------------------------------------------------------
private enum class PillContent { IDLE, LISTENING, THINKING, REPLY, INPUT }

private fun pillContentState(state: AssistantUiState): PillContent = when {
    state.isListening                                  -> PillContent.LISTENING
    state.isThinking                                   -> PillContent.THINKING
    state.isSpeaking                                   -> PillContent.REPLY
    state.messages.any { !it.isUser }                  -> PillContent.REPLY
    state.textInput.isNotEmpty()                       -> PillContent.INPUT
    else                                               -> PillContent.IDLE
}

// ---------------------------------------------------------------------------
// State orb — animated circle that reacts to state
// ---------------------------------------------------------------------------
@Composable
private fun PillStateOrb(
    state: AssistantUiState,
    primary: Color,
    gradientEnd: Color
) {
    val infinite = rememberInfiniteTransition(label = "orb")

    val orbScale by animateFloatAsState(
        targetValue   = when {
            state.isListening -> 1f + state.amplitude * 0.4f
            state.isThinking  -> 1f
            state.isSpeaking  -> 1f + state.amplitude * 0.25f
            else              -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "orbScale"
    )

    val pulseAlpha by infinite.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val orbColor = when {
        state.isListening -> Color(0xFFF87171) // red
        state.isThinking  -> Color(0xFFFCD34D) // yellow
        state.isSpeaking  -> Color(0xFF34D399) // green
        else              -> primary
    }

    Box(
        modifier = Modifier
            .size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse ring — visible when active
        if (!state.isDone && (state.isListening || state.isThinking || state.isSpeaking)) {
            Box(
                modifier = Modifier
                    .size((36 * orbScale).dp)
                    .background(
                        color = orbColor.copy(alpha = 0.15f * pulseAlpha),
                        shape = CircleShape
                    )
            )
        }

        // Core orb
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(orbColor, orbColor.copy(alpha = 0.6f))
                    ),
                    shape = CircleShape
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Pill content composables
// ---------------------------------------------------------------------------

@Composable
private fun PillIdle() {
    Text(
        text      = "Nasıl yardımcı olabilirim?",
        color     = ColorTextSecondary,
        fontSize  = 13.sp,
        maxLines  = 1,
        overflow  = TextOverflow.Ellipsis
    )
}

@Composable
private fun PillListening(amplitude: Float) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val barCount    = 5
    val infinite    = rememberInfiniteTransition(label = "listeningBars")

    val bars = (0 until barCount).map { i ->
        infinite.animateFloat(
            initialValue = 0.15f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(380 + i * 55, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val barW    = 3.dp.toPx()
        val gap     = (size.width - barW * barCount) / (barCount + 1)

        bars.forEachIndexed { i, anim ->
            val combined = (anim.value * 0.35f + amplitude * 0.65f).coerceIn(0.08f, 1f)
            val barH     = size.height * combined
            val x        = gap + i * (barW + gap)
            val topY     = (size.height - barH) / 2f
            val t        = i.toFloat() / (barCount - 1)
            val color    = androidx.compose.ui.graphics.lerp(primary, gradientEnd, t)

            drawRoundRect(
                color        = color.copy(alpha = 0.9f),
                topLeft      = Offset(x, topY),
                size         = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
            )
        }
    }
}

@Composable
private fun PillThinking() {
    val primary  = IrisTheme.colors.primary
    val infinite = rememberInfiniteTransition(label = "thinking")

    val dots = (0 until 3).map { i ->
        infinite.animateFloat(
            initialValue = 0.25f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(480, delayMillis = i * 140, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$i"
        )
    }

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        dots.forEach { alpha ->
            val scale by animateFloatAsState(
                targetValue   = if (alpha.value > 0.6f) 1f else 0.7f,
                animationSpec = tween(140),
                label         = "dotScale"
            )
            Box(
                modifier = Modifier
                    .size((8 * scale).dp)
                    .background(
                        color = primary.copy(alpha = alpha.value),
                        shape = CircleShape
                    )
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text     = "Düşünüyor",
            color    = ColorTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PillReply(lastReply: String) {
    Text(
        text      = lastReply,
        color     = Color(0xFFFAFAFA),
        fontSize  = 13.sp,
        maxLines  = 2,
        overflow  = TextOverflow.Ellipsis,
        lineHeight = 17.sp
    )
}

@Composable
private fun PillInput(
    text         : String,
    onTextChanged: (String) -> Unit,
    onSend       : () -> Unit
) {
    val primary       = IrisTheme.colors.primary
    val focusRequester = remember { FocusRequester() }

    // ── BUG FIX: request focus programmatically after composition ──────────
    // Translucent activity doesn't automatically give focus to the first
    // focusable child. We explicitly request it after the composition settles.
    LaunchedEffect(Unit) {
        delay(150) // wait for window to be fully ready
        runCatching { focusRequester.requestFocus() }
    }

    BasicTextField(
        value         = text,
        onValueChange = onTextChanged,
        modifier      = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle     = androidx.compose.ui.text.TextStyle(
            fontSize  = 13.sp,
            color     = Color(0xFFFAFAFA)
        ),
        cursorBrush   = SolidColor(primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        singleLine    = true,
        decorationBox = { inner ->
            if (text.isEmpty()) {
                Text(
                    text     = "Mesaj yaz...",
                    color    = ColorTextSecondary,
                    fontSize = 13.sp
                )
            }
            inner()
        }
    )
}

// ---------------------------------------------------------------------------
// Pill action button — mic / send FAB
// ---------------------------------------------------------------------------
@Composable
private fun PillActionButton(
    state     : AssistantUiState,
    primary   : Color,
    onMicClick: () -> Unit,
    onSend    : () -> Unit
) {
    val isDisabled = state.isListening || state.isThinking
    val showSend   = state.textInput.isNotBlank() && !isDisabled

    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "fabScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isDisabled) Color(0xFF27272A)
                else Brush.linearGradient(listOf(primary, IrisTheme.colors.gradientEnd))
            )
            .clickable(
                enabled           = !isDisabled,
                interactionSource = interactionSource,
                indication        = null
            ) {
                if (showSend) onSend() else onMicClick()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState   = showSend,
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            },
            label = "fabIcon"
        ) { send ->
            Icon(
                imageVector        = if (send) PhIcons.Filled.PaperPlaneRightFill
                                     else PhIcons.Filled.MicrophoneFill,
                contentDescription = if (send) "Gönder" else "Sesli giriş",
                tint               = if (isDisabled) Color(0xFF52525B) else Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}