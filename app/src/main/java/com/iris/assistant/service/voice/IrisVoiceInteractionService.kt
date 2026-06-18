package com.iris.assistant.service.voice

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log
import com.iris.assistant.ui.assistant.AssistantActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * VoiceInteractionService for IRIS default assistant integration.
 * Activated when the user long-presses the power button or home button
 * and IRIS is set as the default assistant app in system settings.
 *
 * Both onLaunchVoiceAssistFromKeyguard and the session's onShow open
 * the lightweight AssistantActivity instead of the full main app.
 */
@AndroidEntryPoint
class IrisVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IrisVoiceInteractionService"
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        launchAssistant()
    }

    private fun launchAssistant() {
        val intent = Intent(this, AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
