package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.filled.MicrophoneFill
import com.phosphor.icons.filled.PaperPlaneRightFill
import dagger.hilt.android.AndroidEntryPoint

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
            kotlinx.coroutines.delay(1500)
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startVoicePipeline()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    viewModel.stop()
                    onClose()
                }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .heightIn(max = 540.dp)
                .align(Alignment.Center)
                .background(Color(0xFF1C1C1E), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages) { bubble ->
                        MessageBubble(bubble)
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
                        placeholder = { Text("Mesaj yaz...", color = Color(0x66FFFFFF), fontSize = 14.sp) },
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

                    if (state.textInput.isEmpty()) {
                        IconButton(
                            onClick = { viewModel.startVoicePipeline() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IrisTheme.colors.primary)
                        ) {
                            Icon(
                                imageVector = PhIcons.Filled.MicrophoneFill,
                                contentDescription = "Sesli",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.sendText()
                                keyboardController?.hide()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IrisTheme.colors.primary)
                        ) {
                            Icon(
                                imageVector = PhIcons.Filled.PaperPlaneRightFill,
                                contentDescription = "Gönder",
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
