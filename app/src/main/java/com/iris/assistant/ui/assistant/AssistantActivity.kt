package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iris.assistant.service.voice.VoiceInteractionEntryPoint
import com.iris.assistant.ui.theme.IrisThemeTransparent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private const val TAG = "AssistantActivity"

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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
            IrisThemeTransparent {
                AssistantScreen(viewModel = viewModel, onClose = { finish() })
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
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) {
            delay(250)
            onClose()
        }
    }

    BackHandler(enabled = !state.isDismissed) {
        viewModel.dismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        // Capsule
        AnimatedVisibility(
            visible = !state.isDismissed,
            enter = scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(dampingRatio = 0.7f),
            ) + fadeIn(),
            exit = scaleOut(
                targetScale = 0.88f,
                animationSpec = spring(dampingRatio = 0.7f),
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding(),
        ) {
            AssistantCapsule(
                state = state,
                amplitude = state.amplitude,
                onInputChanged = viewModel::onTextInputChanged,
                onSendText = viewModel::sendText,
                onMicClick = viewModel::startVoicePipeline,
                onCapsuleTap = viewModel::onCapsuleTap,
                onClose = viewModel::dismiss,
            )
        }
    }
}
