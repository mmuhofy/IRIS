package com.iris.assistant.service.voice

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
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
class IrisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry = SavedStateRegistry()

    private var viewModel: AssistantViewModel? = null

    override fun onCreateContentView(): View {
        Log.d(TAG, "onCreateContentView")
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

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

        return ComposeView(context).apply {
            attachViewTreeLifecycleOwner(this@IrisVoiceInteractionSession)
            setContent {
                IrisThemeTransparent {
                    SessionCapsuleContent(viewModel = vm, onFinish = { finish() })
                }
            }
        }
    }

    private fun attachViewTreeLifecycleOwner(owner: LifecycleOwner) {
        try {
            val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
                .invoke(null, this, owner)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set ViewTreeLifecycleOwner", e)
        }
        try {
            val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                .invoke(null, this, owner as SavedStateRegistryOwner)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set ViewTreeSavedStateRegistryOwner", e)
        }
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        outInsets ?: return

        val dm: DisplayMetrics = context.resources.displayMetrics
        val capsuleHeightPx = (72 * dm.density).toInt()

        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        outInsets.contentInsets.set(
            0,
            dm.heightPixels - capsuleHeightPx,
            dm.widthPixels,
            dm.heightPixels,
        )
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow")
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "onHide")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModel?.release()
        viewModel = null
        super.onDestroy()
    }
}

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
