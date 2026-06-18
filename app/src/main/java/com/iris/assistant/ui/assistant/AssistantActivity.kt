package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PaperPlaneRightFill
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sin

@AndroidEntryPoint
class AssistantActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AssistantActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        overridePendingTransition(0, 0)

        Log.d(TAG, "onCreate")
        setContent {
            IrisTheme {
                AssistantScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    onClose: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.isDone) {
        if (state.isDone) {
            kotlinx.coroutines.delay(1200)
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startVoicePipeline()
    }

    val dimAlpha by animateFloatAsState(
        targetValue = if (state.isDone) 0f else 0.65f,
        animationSpec = tween(350, easing = LinearEasing),
        label = "dim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0f, 0f, 0f, dimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                viewModel.stop()
                onClose()
            }
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400, easing = LinearEasing)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 160.dp, max = 480.dp)
                    .navigationBarsPadding()
                    .background(Color(0xFF1C1C1E), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 20.dp, bottom = 14.dp, start = 16.dp, end = 16.dp)
                ) {
                    if (state.isListening) {
                        SoundWave(amplitude = state.amplitude)
                        Spacer(Modifier.height(16.dp))
                    }

                    if (state.messages.isEmpty() && !state.isListening) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Nasıl yardımcı olabilirim?",
                            color = Color(0x66FFFFFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.weight(1f))
                    } else if (state.messages.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.messages) { bubble ->
                                MessageBubble(bubble)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.textInput,
                            onValueChange = viewModel::onTextInputChanged,
                            placeholder = {
                                Text("Mesaj yaz...", color = Color(0x66FFFFFF), fontSize = 14.sp)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = IrisTheme.colors.primary,
                                focusedBorderColor = IrisTheme.colors.primary.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedContainerColor = Color(0x1AFFFFFF),
                                unfocusedContainerColor = Color(0x1AFFFFFF)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    viewModel.sendText()
                                    keyboardController?.hide()
                                }
                            ),
                            singleLine = true
                        )

                        Spacer(Modifier.width(8.dp))

                        val buttonIcon = if (state.textInput.isEmpty())
                            PhIcons.Filled.MicrophoneFill
                        else
                            PhIcons.Filled.PaperPlaneRightFill

                        val buttonDesc = if (state.textInput.isEmpty()) "Sesli" else "Gönder"

                        IconButton(
                            onClick = {
                                if (state.textInput.isEmpty()) {
                                    viewModel.startVoicePipeline()
                                } else {
                                    viewModel.sendText()
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IrisTheme.colors.primary)
                        ) {
                            Icon(
                                imageVector = buttonIcon,
                                contentDescription = buttonDesc,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundWave(amplitude: Float) {
    val barCount = 5
    val primary = IrisTheme.colors.primary

    val amplitudes = remember(amplitude) {
        List(barCount) { i ->
            val phase = i.toFloat() / barCount * kotlin.math.PI.toFloat()
            (amplitude * 0.5f + 0.5f) * (0.4f + 0.6f * (sin(phase.toDouble()) + 1f) / 2f).toFloat()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 80.dp, height = 40.dp)) {
            val barWidth = size.width / (barCount * 2f)
            val spacing = barWidth

            for (i in 0 until barCount) {
                val barHeight = size.height * (0.15f + 0.85f * amplitudes[i])
                val x = i * (barWidth * 2f) + spacing / 2f
                drawRoundRect(
                    color = primary.copy(alpha = (0.4f + 0.6f * amplitudes[i]).coerceIn(0f, 1f)),
                    topLeft = Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val bg = if (bubble.isUser)
        IrisTheme.colors.primary.copy(alpha = 0.25f)
    else Color(0x1AFFFFFF)

    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (bubble.isUser) 16.dp else 4.dp,
        bottomEnd = if (bubble.isUser) 4.dp else 16.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (bubble.isUser) 48.dp else 0.dp, end = if (bubble.isUser) 0.dp else 48.dp)
    ) {
        Text(
            text = bubble.text,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .background(bg, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
