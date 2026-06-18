package com.iris.assistant.service.voice

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log
import com.iris.assistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * VoiceInteractionService for IRIS default assistant integration.
 * Activated when the user long-presses the power button or home button
 * and IRIS is set as the default assistant app in system settings.
 *
 * onLaunchVoiceAssistFromKeyguard opens MainActivity on lock screen activation.
 * Normal activation (unlocked) triggers the session's onShow, which also
 * opens MainActivity.
 */
@AndroidEntryPoint
class IrisVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IrisVoiceInteractionService"
        const val EXTRA_VOICE_INTERACTION = "voice_interaction"
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        launchIrisActivity()
    }

    private fun launchIrisActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_VOICE_INTERACTION, true)
        }
        startActivity(intent)
    }
}
