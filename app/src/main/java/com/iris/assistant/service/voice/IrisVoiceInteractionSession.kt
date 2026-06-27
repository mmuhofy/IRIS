package com.iris.assistant.service.voice

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.iris.assistant.ui.assistant.AssistantCapsule
import com.iris.assistant.ui.assistant.AssistantViewModel
import com.iris.assistant.ui.theme.IrisThemeTransparent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private const val TAG = "IrisVoiceInteractionSession"

/**
 * Session created when the user activates IRIS via the default assistant.
 *
 * Renders the floating capsule directly in a TYPE_VOICE_INTERACTION window
 * and uses onComputeInsets to restrict touchable area to the capsule bounds —
 * touches outside pass through naturally to the underlying app.
 */
class IrisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private var viewModel: AssistantViewModel? = null

    override fun onCreateContentView(): View {
        Log.d(TAG, "onCreateContentView")

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, VoiceInteractionEntryPoint::class.java
        )

        val vm = AssistantViewModel(
            context = context,
            audioRecorder = ep.audioRecorder(),
            transcribeAudioUseCase = ep.transcribeAudioUseCase(),
            sendMessageUseCase = ep.sendMessageUseCase(),
            ttsProvider = ep.ttsProvider(),
        ).also { viewModel = it }

        val composeView = ComposeView(context).apply {
            setContent {
                IrisThemeTransparent {
                    SessionCapsuleContent(viewModel = vm, onFinish = { finish() })
                }
            }
        }

        // Recompute insets after layout so the touchable region matches the
        // actual capsule position (onComputeInsets uses a fixed estimate on
        // first call, but we re-compute once laid out).
        composeView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    computeInsets()
                }
            }
        )

        return composeView
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        outInsets ?: return

        val metrics = context.resources.displayMetrics
        val capsuleHeightPx = (72 * metrics.density).toInt()

        // Only the bottom strip containing the capsule is touchable.
        // Everything above passes through to the app behind.
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        outInsets.contentInsets.set(
            0,
            metrics.heightPx - capsuleHeightPx,
            metrics.widthPx,
            metrics.heightPx,
        )
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow")
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "onHide")
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel?.onCleared()
        viewModel = null
    }
}

// ---------------------------------------------------------------------------
// Session capsule composable — wraps the shared AssistantCapsule in a
// bottom-aligned full-screen transparent Box.
// ---------------------------------------------------------------------------

@Composable
private fun SessionCapsuleContent(
    viewModel: AssistantViewModel,
    onFinish: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) {
            delay(250)
            onFinish()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
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
