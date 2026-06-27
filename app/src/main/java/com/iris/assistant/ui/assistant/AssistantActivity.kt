package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
import android.view.Gravity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.iris.assistant.service.voice.VoiceInteractionEntryPoint
import com.iris.assistant.ui.theme.IrisThemeTransparent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private const val TAG = "AssistantActivity"

// ---------------------------------------------------------------------------
// Floating assistant overlay
//
// Key design:
// - Theme.IRIS.Translucent uses windowIsFloating=true so the window is
//   sized to content (WRAP_CONTENT) and positioned at bottom-center.
// - Touches outside the window naturally pass through to the app behind,
//   because the window is NOT full-screen.
// - In INPUT mode the window expands to full width so the text field
//   has room; in all other modes it wraps the capsule width.
// - Back button or capsule X dismisses.
// ---------------------------------------------------------------------------

class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window.attributes = window.attributes.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

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

    fun updateWindowWidth(mode: CapsuleMode) {
        val newWidth = if (mode == CapsuleMode.INPUT) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        window.setLayout(newWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}

// ---------------------------------------------------------------------------
// Root screen — no full-screen wrapper, just the capsule
// ---------------------------------------------------------------------------

@Composable
private fun AssistantScreen(
    viewModel: AssistantViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val activity = LocalContext.current as AssistantActivity
    LaunchedEffect(state.capsuleMode) {
        activity.updateWindowWidth(state.capsuleMode)
    }

    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) {
            delay(250)
            onClose()
        }
    }

    BackHandler(enabled = !state.isDismissed) {
        viewModel.dismiss()
    }

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
