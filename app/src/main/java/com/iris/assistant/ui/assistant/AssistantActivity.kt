package com.iris.assistant.ui.assistant

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.service.voice.VoiceInteractionEntryPoint
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PaperPlaneRightFill
import com.phosphor.icons.regular.X
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private const val TAG = "AssistantActivity"

private enum class SweepPhase { SWEEPING, COLLAPSING, DONE }

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Translucent overlay: must NOT call enableEdgeToEdge().
        // Clear FLAG_NOT_FOCUSABLE so TextField and IME work.
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // clear this bit
        )

        Log.d(TAG, "onCreate")

        val ep = EntryPointAccessors.fromApplication(
            applicationContext, VoiceInteractionEntryPoint::class.java
        )
        val viewModel = AssistantViewModel(
            context                = applicationContext,
            audioRecorder          = ep.audioRecorder(),
            transcribeAudioUseCase = ep.transcribeAudioUseCase(),
            sendMessageUseCase     = ep.sendMessageUseCase(),
            ttsProvider            = ep.ttsProvider()
        )

        setContent {
            IrisTheme {
                AssistantScreen(viewModel = viewModel, onClose = { finish() })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root screen
// ---------------------------------------------------------------------------
@Composable
private fun AssistantScreen(viewModel: AssistantViewModel, onClose: () -> Unit) {
    val state       by viewModel.uiState.collectAsState()
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    var sweepPhase       by remember { mutableStateOf(SweepPhase.SWEEPING) }
    val sweepProgress    = remember { Animatable(0f) }
    val collapseProgress = remember { Animatable(0f) }

    // Track if user explicitly tapped pill to type
    var inputFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sweepProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        sweepPhase = SweepPhase.COLLAPSING
        collapseProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        sweepPhase = SweepPhase.DONE
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) { delay(300); onClose() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Tap-outside dismiss — NO background color, fully transparent
        if (sweepPhase == SweepPhase.DONE && !state.isDone) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.stop(); onClose() }
            )
        }

        // Sweep canvas — only during animation phases
        if (sweepPhase != SweepPhase.DONE) {
            SweepCanvas(
                progress         = sweepProgress.value,
                collapseProgress = collapseProgress.value,
                primary          = primary,
                gradientEnd      = gradientEnd,
                modifier         = Modifier.fillMaxSize()
            )
        }

        // Pill
        AnimatedVisibility(
            visible  = sweepPhase == SweepPhase.DONE && !state.isDone,
            enter    = scaleIn(
                initialScale  = 0.88f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(tween(180)),
            exit     = scaleOut(targetScale = 0.88f) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding()
        ) {
            AssistantPill(
                state         = state,
                inputFocused  = inputFocused,
                onPillTap     = { inputFocused = true },
                onTextChanged = viewModel::onTextInputChanged,
                onSendText    = { viewModel.sendText(); inputFocused = false },
                onMicClick    = viewModel::startVoicePipeline,
                onClose       = { viewModel.stop(); onClose() }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sweep canvas — dispatches to AGSL (API 33+) or Canvas compat
// ---------------------------------------------------------------------------
@Composable
private fun SweepCanvas(
    progress: Float, collapseProgress: Float,
    primary: Color, gradientEnd: Color,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SweepCanvasAgsl(progress, collapseProgress, primary, gradientEnd, modifier)
    } else {
        SweepCanvasCompat(progress, collapseProgress, primary, gradientEnd, modifier)
    }
}

// ---------------------------------------------------------------------------
// AGSL sweep — Android 13+ (API 33+)
//
// Design: 4 lines sweep from each screen EDGE toward center, like a
// rectangular border being drawn. Each side starts at its far corner and
// sweeps toward the middle. When collapse begins they fade out.
// ---------------------------------------------------------------------------
private val SWEEP_AGSL = """
uniform float2 iResolution;
uniform float  uProgress;   // 0→1 sweep growing
uniform float  uCollapse;   // 0→1 fading out

uniform float4 uPrimary;
uniform float4 uGradEnd;

// Distance from point (px,py) to the line segment (ax,ay)→(bx,by)
float distToSegment(float2 p, float2 a, float2 b) {
    float2 ab = b - a;
    float2 ap = p - a;
    float  t  = clamp(dot(ap, ab) / dot(ab, ab), 0.0, 1.0);
    return length(ap - ab * t);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord;                      // pixel coords
    float  W  = iResolution.x;
    float  H  = iResolution.y;

    // Each side sweeps from one corner toward the midpoint of that edge.
    // progress=0 → zero-length line; progress=1 → full half-edge drawn.
    // Two segments per edge (left half + right half) so they meet in center.

    float lineThick = 2.5;                      // px, core line
    float glowThick = 18.0;                     // px, soft glow
    float alpha     = 0.0;
    float colorT    = 0.0;

    // Helper lambda via array: [startX, startY, endX, endY, tColor]
    // We define 8 segments (2 per edge), each sweeping progress * halfLength.

    // Top edge: TL→mid and TR→mid
    float topHalf = W * 0.5 * uProgress;
    // top-left segment: (0,0) → (topHalf, 0)
    float d; float2 p = uv;

    // ── TOP edge ──────────────────────────────────────────────────────────
    d = distToSegment(p, float2(0.0, 0.0),       float2(topHalf, 0.0));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    colorT = max(colorT, (uProgress > 0.0) ? clamp(p.x / max(topHalf,1.0), 0.0, 1.0) : 0.0);
    // glow
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    d = distToSegment(p, float2(W, 0.0),          float2(W - topHalf, 0.0));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    // ── BOTTOM edge ───────────────────────────────────────────────────────
    d = distToSegment(p, float2(0.0, H),          float2(topHalf, H));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    d = distToSegment(p, float2(W, H),            float2(W - topHalf, H));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    // ── LEFT edge ─────────────────────────────────────────────────────────
    float sideHalf = H * 0.5 * uProgress;
    d = distToSegment(p, float2(0.0, 0.0),        float2(0.0, sideHalf));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    d = distToSegment(p, float2(0.0, H),          float2(0.0, H - sideHalf));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    // ── RIGHT edge ────────────────────────────────────────────────────────
    d = distToSegment(p, float2(W, 0.0),          float2(W, sideHalf));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    d = distToSegment(p, float2(W, H),            float2(W, H - sideHalf));
    alpha  = max(alpha,  smoothstep(lineThick, 0.0, d));
    alpha  = max(alpha,  smoothstep(glowThick, 0.0, d) * 0.22);

    // Collapse fade
    alpha *= (1.0 - uCollapse);

    // Color: lerp primary→gradEnd based on rough position
    colorT = clamp((uv.x / W + uv.y / H) * 0.5, 0.0, 1.0);
    float4 col = uPrimary + (uGradEnd - uPrimary) * colorT;

    return half4(col.rgb * alpha, alpha);
}
""".trimIndent()

@Composable
private fun SweepCanvasAgsl(
    progress: Float, collapseProgress: Float,
    primary: Color, gradientEnd: Color,
    modifier: Modifier = Modifier
) {
    // UNTESTED — verify before use
    val shader = remember { android.graphics.RuntimeShader(SWEEP_AGSL) }
    Canvas(modifier = modifier) {
        shader.setFloatUniform("iResolution",   size.width, size.height)
        shader.setFloatUniform("uProgress",     progress)
        shader.setFloatUniform("uCollapse",     collapseProgress)
        shader.setFloatUniform("uPrimary",      primary.red,     primary.green,     primary.blue,     primary.alpha)
        shader.setFloatUniform("uGradEnd",      gradientEnd.red, gradientEnd.green, gradientEnd.blue, gradientEnd.alpha)
        drawRect(brush = ShaderBrush(shader), size = size)
    }
}

// ---------------------------------------------------------------------------
// Canvas compat — Android 12 and below
// Draws the same rectangular edge sweep with drawLine + blur-like layering.
// ---------------------------------------------------------------------------
@Composable
private fun SweepCanvasCompat(
    progress: Float, collapseProgress: Float,
    primary: Color, gradientEnd: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fade = (1f - collapseProgress).coerceIn(0f, 1f)
        if (fade <= 0f || progress <= 0f) return@Canvas

        val halfW = w * 0.5f * progress
        val halfH = h * 0.5f * progress

        // Draw 8 line segments (2 per edge) with gradient via segmented lines
        data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
        val segs = listOf(
            Seg(0f,  0f,  halfW, 0f),        // top-left →
            Seg(w,   0f,  w - halfW, 0f),    // top-right ←
            Seg(0f,  h,   halfW, h),          // bot-left →
            Seg(w,   h,   w - halfW, h),      // bot-right ←
            Seg(0f,  0f,  0f, halfH),         // left-top ↓
            Seg(0f,  h,   0f, h - halfH),     // left-bot ↑
            Seg(w,   0f,  w, halfH),          // right-top ↓
            Seg(w,   h,   w, h - halfH)       // right-bot ↑
        )

        segs.forEach { seg ->
            // Glow pass (thick, low alpha)
            drawLineGradient(
                x1 = seg.x1, y1 = seg.y1, x2 = seg.x2, y2 = seg.y2,
                colorA    = primary.copy(alpha = fade * 0.18f),
                colorB    = gradientEnd.copy(alpha = fade * 0.18f),
                thickness = 18f
            )
            // Core line
            drawLineGradient(
                x1 = seg.x1, y1 = seg.y1, x2 = seg.x2, y2 = seg.y2,
                colorA    = primary.copy(alpha = fade * 0.9f),
                colorB    = gradientEnd.copy(alpha = fade * 0.9f),
                thickness = 2.5f
            )
        }
    }
}

private fun DrawScope.drawLineGradient(
    x1: Float, y1: Float, x2: Float, y2: Float,
    colorA: Color, colorB: Color, thickness: Float
) {
    val segments = 10
    val dx = (x2 - x1) / segments
    val dy = (y2 - y1) / segments
    for (i in 0 until segments) {
        val t  = i.toFloat() / segments
        val c  = lerp(colorA, colorB, t)
        drawLine(
            color       = c,
            start       = Offset(x1 + dx * i, y1 + dy * i),
            end         = Offset(x1 + dx * (i + 1), y1 + dy * (i + 1)),
            strokeWidth = thickness,
            cap         = StrokeCap.Round
        )
    }
}

// ---------------------------------------------------------------------------
// AssistantPill
// ---------------------------------------------------------------------------
@Composable
private fun AssistantPill(
    state        : AssistantUiState,
    inputFocused : Boolean,
    onPillTap    : () -> Unit,
    onTextChanged: (String) -> Unit,
    onSendText   : () -> Unit,
    onMicClick   : () -> Unit,
    onClose      : () -> Unit
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val kbd         = LocalSoftwareKeyboardController.current

    Surface(
        shape           = RoundedCornerShape(40.dp),
        color           = Color(0xFF1C1C1E),
        shadowElevation = 24.dp,
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // Tapping idle area → switch to INPUT
            .clickable(
                enabled           = !state.isListening && !state.isThinking,
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onPillTap() }
    ) {
        // Gradient top-border line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            primary.copy(alpha = 0f),
                            primary.copy(alpha = 0.55f),
                            gradientEnd.copy(alpha = 0.55f),
                            primary.copy(alpha = 0f)
                        )
                    )
                )
        )

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillStateOrb(state = state, primary = primary)

            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState   = pillContent(state, inputFocused),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label         = "pillContent"
                ) { cs ->
                    when (cs) {
                        PillContent.IDLE      -> PillIdle()
                        PillContent.LISTENING -> PillListening(state.amplitude)
                        PillContent.THINKING  -> PillThinking()
                        PillContent.REPLY     -> PillReply(
                            state.messages.lastOrNull { !it.isUser }?.text ?: ""
                        )
                        PillContent.INPUT     -> PillInput(
                            text          = state.textInput,
                            onTextChanged = onTextChanged,
                            onSend        = { onSendText(); kbd?.hide() }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            PillActionButton(
                state      = state,
                primary    = primary,
                gradientEnd = gradientEnd,
                onMicClick = onMicClick,
                onSend     = { onSendText(); kbd?.hide() }
            )

            Spacer(Modifier.width(6.dp))

            Box(
                modifier         = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
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
// Pill content state
// ---------------------------------------------------------------------------
private enum class PillContent { IDLE, LISTENING, THINKING, REPLY, INPUT }

private fun pillContent(state: AssistantUiState, inputFocused: Boolean): PillContent = when {
    state.isListening                         -> PillContent.LISTENING
    state.isThinking                          -> PillContent.THINKING
    state.isSpeaking                          -> PillContent.REPLY
    state.messages.any { !it.isUser }         -> PillContent.REPLY
    inputFocused || state.textInput.isNotEmpty() -> PillContent.INPUT
    else                                      -> PillContent.IDLE
}

// ---------------------------------------------------------------------------
// State orb
// ---------------------------------------------------------------------------
@Composable
private fun PillStateOrb(state: AssistantUiState, primary: Color) {
    val inf = rememberInfiniteTransition(label = "orb")
    val pulseAlpha by inf.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "pulse"
    )
    val orbColor = when {
        state.isListening -> Color(0xFFF87171)
        state.isThinking  -> Color(0xFFFCD34D)
        state.isSpeaking  -> Color(0xFF34D399)
        else              -> primary
    }
    val orbScale by animateFloatAsState(
        targetValue   = if (state.isListening) 1f + state.amplitude * 0.5f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "orbScale"
    )

    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (state.isListening || state.isThinking || state.isSpeaking) {
            Box(
                Modifier
                    .size((36 * orbScale).dp)
                    .background(orbColor.copy(alpha = 0.18f * pulseAlpha), CircleShape)
            )
        }
        Box(
            Modifier
                .size(18.dp)
                .background(
                    Brush.radialGradient(listOf(orbColor, orbColor.copy(alpha = 0.5f))),
                    CircleShape
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Content composables
// ---------------------------------------------------------------------------
@Composable
private fun PillIdle() {
    Text("Nasıl yardımcı olabilirim?", color = ColorTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PillListening(amplitude: Float) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val inf         = rememberInfiniteTransition(label = "bars")
    val barCount    = 5
    val anims = (0 until barCount).map { i ->
        inf.animateFloat(
            initialValue  = 0.15f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                tween(380 + i * 55, easing = FastOutSlowInEasing), RepeatMode.Reverse
            ),
            label = "b$i"
        )
    }
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val bw  = 3.dp.toPx()
        val gap = (size.width - bw * barCount) / (barCount + 1)
        anims.forEachIndexed { i, anim ->
            val h  = size.height * (anim.value * 0.35f + amplitude * 0.65f).coerceIn(0.08f, 1f)
            val x  = gap + i * (bw + gap)
            val c  = lerp(primary, gradientEnd, i.toFloat() / (barCount - 1))
            drawRoundRect(c.copy(alpha = 0.9f), Offset(x, (size.height - h) / 2f), Size(bw, h), CornerRadius(bw / 2f))
        }
    }
}

@Composable
private fun PillThinking() {
    val primary = IrisTheme.colors.primary
    val inf     = rememberInfiniteTransition(label = "think")
    val dots = (0 until 3).map { i ->
        inf.animateFloat(
            initialValue  = 0.25f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(480, delayMillis = i * 140), RepeatMode.Reverse),
            label         = "d$i"
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        dots.forEach { a ->
            val sc by animateFloatAsState(if (a.value > 0.6f) 1f else 0.7f, tween(140), label = "ds")
            Box(Modifier.size((8 * sc).dp).background(primary.copy(alpha = a.value), CircleShape))
        }
        Spacer(Modifier.width(4.dp))
        Text("Düşünüyor", color = ColorTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun PillReply(text: String) {
    Text(text, color = Color(0xFFFAFAFA), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
}

@Composable
private fun PillInput(text: String, onTextChanged: (String) -> Unit, onSend: () -> Unit) {
    val primary = IrisTheme.colors.primary
    val fr      = remember { FocusRequester() }

    // Request focus after window is ready — fixes translucent activity IME bug
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }

    BasicTextField(
        value           = text,
        onValueChange   = onTextChanged,
        modifier        = Modifier.fillMaxWidth().focusRequester(fr),
        textStyle       = TextStyle(fontSize = 13.sp, color = Color(0xFFFAFAFA)),
        cursorBrush     = SolidColor(primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        singleLine      = true,
        decorationBox   = { inner ->
            if (text.isEmpty()) Text("Mesaj yaz...", color = ColorTextSecondary, fontSize = 13.sp)
            inner()
        }
    )
}

// ---------------------------------------------------------------------------
// Action button
// ---------------------------------------------------------------------------
@Composable
private fun PillActionButton(
    state      : AssistantUiState,
    primary    : Color,
    gradientEnd: Color,
    onMicClick : () -> Unit,
    onSend     : () -> Unit
) {
    val disabled = state.isListening || state.isThinking
    val showSend = state.textInput.isNotBlank() && !disabled

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (disabled) Modifier.background(Color(0xFF27272A), CircleShape)
                else Modifier.background(Brush.linearGradient(listOf(primary, gradientEnd)), CircleShape)
            )
            .clickable(enabled = !disabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                if (showSend) onSend() else onMicClick()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState   = showSend,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label         = "fabIcon"
        ) { send ->
            Icon(
                imageVector        = if (send) PhIcons.Filled.PaperPlaneRightFill else PhIcons.Filled.MicrophoneFill,
                contentDescription = if (send) "Gönder" else "Sesli giriş",
                tint               = if (disabled) Color(0xFF52525B) else Color.White,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}