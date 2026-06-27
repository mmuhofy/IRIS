package com.iris.assistant.ui.assistant

import android.os.Bundle
import android.util.Log
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

/**
 * Fallback translucent activity for manual assistant launches (e.g. from
 * notification, WakeWordService when not yet default assistant).
 *
 * The primary voice interaction path uses [IrisVoiceInteractionSession]
 * which renders the capsule in a TYPE_VOICE_INTERACTION window with native
 * touch passthrough. This Activity is kept as a secondary entry point for
 * when the system cannot bind a VoiceInteractionService.
 */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
