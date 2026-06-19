package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.service.voice.VoiceInteractionEntryPoint
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PaperPlaneRightFill
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
private const val TAG = "AssistantActivity"
private val SHEET_TOP_RADIUS: Dp = 28.dp
private val SCRIM_ALPHA_TARGET = 0.55f

class AssistantActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor     = android.graphics.Color.TRANSPARENT

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
// Root screen
// ---------------------------------------------------------------------------
@Composable
private fun AssistantScreen(
    viewModel: AssistantViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-close after done + exit animation completes
    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            kotlinx.coroutines.delay(420)
            onClose()
        }
    }

    // Start voice pipeline on launch
    LaunchedEffect(Unit) {
        viewModel.startVoicePipeline()
    }

    // Scrim alpha
    val scrimAlpha by animateFloatAsState(
        targetValue   = if (state.isDone) 0f else SCRIM_ALPHA_TARGET,
        animationSpec = tween(350, easing = LinearEasing),
        label         = "scrim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Scrim (tap outside = dismiss) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0f, 0f, 0f, scrimAlpha))
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    viewModel.stop()
                    onClose()
                }
        )

        // --- Sheet: align on AnimatedVisibility, NOT inside it ---
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible  = !state.isDone,
            enter    = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = tween(380, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(280)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200))
        ) {
            AssistantSheet(
                state              = state,
                onTextChanged      = viewModel::onTextInputChanged,
                onSendText         = {
                    viewModel.sendText()
                    keyboardController?.hide()
                },
                onMicClick         = viewModel::startVoicePipeline,
                onSheetClick       = { /* consume — don't dismiss */ }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sheet
// ---------------------------------------------------------------------------
@Composable
private fun AssistantSheet(
    state: AssistantUiState,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    onMicClick: () -> Unit,
    onSheetClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 520.dp)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onSheetClick
            ),
        shape = RoundedCornerShape(topStart = SHEET_TOP_RADIUS, topEnd = SHEET_TOP_RADIUS),
        color = Color(0xFF1C1C1E),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Drag handle
            DragHandle()

            // Header: IRIS label + state pill
            SheetHeader(state = state)

            Spacer(modifier = Modifier.height(8.dp))

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Listening — big waveform
                    state.isListening -> {
                        ListeningWave(amplitude = state.amplitude)
                    }

                    // Thinking — animated dots
                    state.isThinking -> {
                        ThinkingDots()
                    }

                    // Has messages — conversation
                    state.messages.isNotEmpty() -> {
                        LazyColumn(
                            state             = listState,
                            modifier          = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding    = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                        ) {
                            items(state.messages) { bubble ->
                                MessageBubble(bubble = bubble)
                            }
                        }
                    }

                    // Idle — prompt text
                    else -> {
                        IdlePrompt()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input row
            InputRow(
                textInput  = state.textInput,
                isDisabled = state.isListening || state.isThinking,
                onTextChanged = onTextChanged,
                onSendText    = onSendText,
                onMicClick    = onMicClick
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Drag handle
// ---------------------------------------------------------------------------
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color(0xFF3F3F46))
        )
    }
}

// ---------------------------------------------------------------------------
// Header: "IRIS" label + animated state pill
// ---------------------------------------------------------------------------
@Composable
private fun SheetHeader(state: AssistantUiState) {
    val primary = IrisTheme.colors.primary
    val gradient = IrisTheme.colors.gradientEnd

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // IRIS brand dot + label
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(primary, gradient)
                        )
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text       = "IRIS",
                color      = Color(0xFFFAFAFA),
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
        }

        // Animated state pill
        AnimatedContent(
            targetState   = derivedStatePill(state),
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
            },
            label = "statePill"
        ) { (label, color) ->
            if (label.isNotEmpty()) {
                StatePill(label = label, color = color)
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }
        }
    }
}

// Returns Pair<label, color> for the current state
private fun derivedStatePill(state: AssistantUiState): Pair<String, Color> = when {
    state.isListening -> "Dinleniyor" to Color(0xFFF87171)   // red accent
    state.isThinking  -> "Düşünüyor"  to Color(0xFFFCD34D)   // yellow
    state.isSpeaking  -> "Konuşuyor"  to Color(0xFF34D399)   // green
    else              -> ""            to Color.Transparent
}

@Composable
private fun StatePill(label: String, color: Color) {
    val infinite  = rememberInfiniteTransition(label = "pill")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
        Text(
            text       = label,
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Idle prompt
// ---------------------------------------------------------------------------
@Composable
private fun IdlePrompt() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text       = "Nasıl yardımcı olabilirim?",
            color      = Color(0xCCFFFFFF),           // 80% opacity — much more visible
            fontSize   = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text      = "Sesle veya yazarak konuşabilirsin",
            color     = Color(0xFF71717A),
            fontSize  = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Listening waveform — full width, larger, better looking
// ---------------------------------------------------------------------------
@Composable
private fun ListeningWave(amplitude: Float) {
    val primary  = IrisTheme.colors.primary
    val gradient = IrisTheme.colors.gradientEnd
    val barCount = 7

    // Per-bar animated heights using staggered infinite loops
    val infinite = rememberInfiniteTransition(label = "wave")
    val animValues = (0 until barCount).map { i ->
        infinite.animateFloat(
            initialValue = 0.15f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(
                    durationMillis = 420 + i * 60,
                    easing         = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.65f)   // 65% of sheet width
                .height(72.dp)
        ) {
            val totalWidth   = size.width
            val barWidth     = totalWidth / (barCount * 2f - 1f) * 0.55f
            val spacingTotal = totalWidth - barWidth * barCount
            val gap          = spacingTotal / (barCount - 1)

            for (i in 0 until barCount) {
                // Blend idle animation with live amplitude
                val idleFraction = animValues[i].value
                val combined     = (idleFraction * 0.4f + amplitude * 0.6f).coerceIn(0.1f, 1f)
                val barHeight    = size.height * combined
                val x            = i * (barWidth + gap)
                val topY         = (size.height - barHeight) / 2f

                val colorFraction = i.toFloat() / (barCount - 1)
                val barColor = androidx.compose.ui.graphics.lerp(primary, gradient, colorFraction)

                drawRoundRect(
                    color        = barColor.copy(alpha = 0.85f + 0.15f * combined),
                    topLeft      = Offset(x, topY),
                    size         = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text      = "Seni dinliyorum...",
            color     = Color(0xFF71717A),
            fontSize  = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Thinking dots animation
// ---------------------------------------------------------------------------
@Composable
private fun ThinkingDots() {
    val primary  = IrisTheme.colors.primary
    val dotCount = 3
    val infinite = rememberInfiniteTransition(label = "think")

    val dotAlphas = (0 until dotCount).map { i ->
        infinite.animateFloat(
            initialValue = 0.25f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(
                    durationMillis = 500,
                    delayMillis    = i * 160,
                    easing         = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$i"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            dotAlphas.forEachIndexed { i, alpha ->
                val scale by animateFloatAsState(
                    targetValue   = if (alpha.value > 0.6f) 1f else 0.75f,
                    animationSpec = tween(160),
                    label         = "dotScale$i"
                )
                Box(
                    modifier = Modifier
                        .size((10 * scale).dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = alpha.value))
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text      = "Düşünüyorum...",
            color     = Color(0xFF71717A),
            fontSize  = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Message bubble
// ---------------------------------------------------------------------------
@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val primary = IrisTheme.colors.primary

    val bubbleBg = if (bubble.isUser)
        primary.copy(alpha = 0.22f)
    else
        Color(0xFF27272A)

    val shape = RoundedCornerShape(
        topStart    = 18.dp,
        topEnd      = 18.dp,
        bottomStart = if (bubble.isUser) 18.dp else 4.dp,
        bottomEnd   = if (bubble.isUser) 4.dp else 18.dp
    )

    val alignment = if (bubble.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(bubbleBg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text      = bubble.text,
                color     = Color(0xFFFAFAFA),
                fontSize  = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Input row
// ---------------------------------------------------------------------------
@Composable
private fun InputRow(
    textInput  : String,
    isDisabled : Boolean,
    onTextChanged: (String) -> Unit,
    onSendText   : () -> Unit,
    onMicClick   : () -> Unit
) {
    val primary = IrisTheme.colors.primary
    val showSend = textInput.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value         = textInput,
            onValueChange = onTextChanged,
            enabled       = !isDisabled,
            placeholder   = {
                Text(
                    text  = if (isDisabled) "IRIS düşünüyor..." else "Mesaj yaz...",
                    color = Color(0xFF52525B),
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(22.dp),
            colors   = OutlinedTextFieldDefaults.colors(
                focusedTextColor      = Color(0xFFFAFAFA),
                unfocusedTextColor    = Color(0xFFFAFAFA),
                disabledTextColor     = Color(0xFF71717A),
                cursorColor           = primary,
                focusedBorderColor    = primary.copy(alpha = 0.5f),
                unfocusedBorderColor  = Color(0xFF3F3F46),
                disabledBorderColor   = Color(0xFF27272A),
                focusedContainerColor = Color(0xFF27272A),
                unfocusedContainerColor = Color(0xFF27272A),
                disabledContainerColor  = Color(0xFF1C1C1E)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color    = Color(0xFFFAFAFA)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendText() }),
            singleLine      = true,
            minLines        = 1
        )

        Spacer(modifier = Modifier.width(10.dp))

        // FAB: mic ↔ send toggle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isDisabled) Color(0xFF27272A) else primary
                )
                .clickable(enabled = !isDisabled) {
                    if (showSend) onSendText() else onMicClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector  = if (showSend)
                    PhIcons.Filled.PaperPlaneRightFill
                else
                    PhIcons.Filled.MicrophoneFill,
                contentDescription = if (showSend) "Gönder" else "Sesli",
                tint     = if (isDisabled) Color(0xFF52525B) else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}