package com.iris.assistant.ui.assistant

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.PaperPlaneRight
import com.phosphor.icons.regular.StopCircle
import com.phosphor.icons.regular.X

// ---------------------------------------------------------------------------
// Capsule state indicator colors
// ---------------------------------------------------------------------------

private val CapsuleListeningColor  = Color(0xFFF87171)
private val CapsuleReplyColor     = Color(0xFF34D399)

// ---------------------------------------------------------------------------
// Capsule container — wraps all states with shared styling
// ---------------------------------------------------------------------------

@Composable
fun AssistantCapsule(
    state       : AssistantUiState,
    amplitude   : Float,
    onInputChanged: (String) -> Unit,
    onSendText  : () -> Unit,
    onMicClick  : () -> Unit,
    onCapsuleTap: () -> Unit,
    onClose     : () -> Unit,
    modifier    : Modifier = Modifier,
) {
    val primary     = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd

    // Capsule width changes by mode
    val capsuleWidth by animateFloatAsState(
        targetValue = when (state.capsuleMode) {
            CapsuleMode.INPUT -> 1f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.7f),
        label = "capsuleWidth",
    )

    val isInput = state.capsuleMode == CapsuleMode.INPUT

    Surface(
        shape = RoundedCornerShape(if (isInput) 20.dp else 26.dp),
        color = Color(0xFF1C1C1E),
        shadowElevation = 24.dp,
        modifier = modifier
            .then(
                if (isInput) Modifier.fillMaxWidth()
                          else Modifier.widthIn(max = 340.dp)
            )
            .padding(horizontal = if (isInput) 0.dp else 16.dp),
    ) {
        CapsuleBorderLine(primary = primary, gradientEnd = gradientEnd)

        AnimatedContent(
            targetState = state.capsuleMode,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 })
                    togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 })
            },
            label = "capsuleContent",
        ) { mode ->
            when (mode) {
                CapsuleMode.LISTENING -> ListeningContent(
                    amplitude = amplitude,
                    onCapsuleTap = onCapsuleTap,
                )
                CapsuleMode.THINKING -> ThinkingContent(
                    onStop = onClose,
                    onCapsuleTap = onCapsuleTap,
                )
                CapsuleMode.REPLY -> ReplyContent(
                    text = state.replyText,
                    onClose = onClose,
                    onCapsuleTap = onCapsuleTap,
                )
                CapsuleMode.INPUT -> InputContent(
                    text = state.textInput,
                    onTextChanged = onInputChanged,
                    onSend = onSendText,
                    onMicClick = onMicClick,
                    onClose = onClose,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Capsule border line — subtle gradient accent at top edge
// ---------------------------------------------------------------------------

@Composable
private fun CapsuleBorderLine(primary: Color, gradientEnd: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        primary.copy(alpha = 0f),
                        primary.copy(alpha = 0.5f),
                        gradientEnd.copy(alpha = 0.5f),
                        primary.copy(alpha = 0f),
                    )
                )
            )
    )
}

// ---------------------------------------------------------------------------
// Shared status dot
// ---------------------------------------------------------------------------

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val inf = rememberInfiniteTransition(label = "statusDot")
    val dotAlpha by inf.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(800),
            RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color.copy(alpha = if (pulsing) dotAlpha else 0.8f),
                CircleShape,
            )
    )
}

// ---------------------------------------------------------------------------
// ListeningContent — red dot + text + amplitude waveform
// ---------------------------------------------------------------------------

@Composable
private fun ListeningContent(
    amplitude: Float,
    onCapsuleTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCapsuleTap,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = CapsuleListeningColor, pulsing = true)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Dinliyorum...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                ),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = PhIcons.Regular.Microphone,
                contentDescription = "Mikrofon",
                tint = CapsuleListeningColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        WaveformBars(amplitude = amplitude)
    }
}

@Composable
private fun WaveformBars(amplitude: Float) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val barCount = 32
    val inf = rememberInfiniteTransition(label = "waveform")

    val seeds = (0 until barCount).map { i ->
        val delay = i * 60
        inf.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                tween(400 + delay % 300, delayMillis = delay % 400),
                RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val barW = 2.dp.toPx()
        val gap = (size.width - barW * barCount) / (barCount + 1)
        val baseAmp = amplitude.coerceIn(0.15f, 1f)

        seeds.forEachIndexed { i, seed ->
            val h = size.height * seed.value * baseAmp * 0.45f
            val x = gap + i * (barW + gap)
            val t = i.toFloat() / barCount
            val c = lerp(gradientEnd, primary, t)
            drawRoundRect(
                color = c.copy(alpha = (0.5f + seed.value * 0.4f).coerceIn(0f, 0.9f)),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ThinkingContent — animated dots + stop button
// ---------------------------------------------------------------------------

@Composable
private fun ThinkingContent(
    onStop: () -> Unit,
    onCapsuleTap: () -> Unit,
) {
    val primary = IrisTheme.colors.primary
    val inf = rememberInfiniteTransition(label = "thinkingDots")

    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCapsuleTap,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = primary, pulsing = true)
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { idx ->
                val dotAlpha by inf.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(500, delayMillis = idx * 160),
                        RepeatMode.Reverse,
                    ),
                    label = "dot$idx",
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = dotAlpha)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Düşünüyorum...",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
            ),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStop,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.StopCircle,
                contentDescription = "Durdur",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ReplyContent — green dot + reply text + close
// ---------------------------------------------------------------------------

@Composable
private fun ReplyContent(
    text: String,
    onClose: () -> Unit,
    onCapsuleTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCapsuleTap,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = CapsuleReplyColor, pulsing = false)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp,
            ),
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.X,
                contentDescription = "Kapat",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// InputContent — text field + mic + send
// ---------------------------------------------------------------------------

@Composable
private fun InputContent(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onClose: () -> Unit,
) {
    val primary = IrisTheme.colors.primary
    val gradientEnd = IrisTheme.colors.gradientEnd
    val fr = remember { FocusRequester() }
    val view = LocalView.current

    // IME show for translucent window — same workaround as before
    LaunchedEffect(Unit) {
        view.post {
            fr.requestFocus()
            fun showImm() {
                view.post {
                    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            if (view.hasWindowFocus()) {
                showImm()
            } else {
                view.viewTreeObserver.addOnWindowFocusChangeListener(
                    object : ViewTreeObserver.OnWindowFocusChangeListener {
                        override fun onWindowFocusChanged(hasFocus: Boolean) {
                            if (hasFocus) {
                                showImm()
                                view.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                            }
                        }
                    }
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Close
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.X,
                contentDescription = "Kapat",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Text field
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = primary.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                )
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .focusRequester(fr),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                ),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = true,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Mesaj yaz...",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        // Mic
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMicClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.Microphone,
                contentDescription = "Sesli giriş",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(6.dp))

        // Send
        val canSend = text.isNotBlank()
        val sendMod = if (canSend) {
            Modifier.background(
                Brush.linearGradient(listOf(primary, gradientEnd)),
                CircleShape,
            )
        } else {
            Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(sendMod)
                .clickable(
                    enabled = canSend,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PhIcons.Regular.PaperPlaneRight,
                contentDescription = "Gönder",
                tint = if (canSend) Color.White else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
