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
 * onLaunchVoiceAssist is the main entry point — it opens MainActivity
 * which then starts the voice pipeline (listening → LLM → TTS).
 */
@AndroidEntryPoint
class IrisVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IrisVoiceInteractionService"
        const val EXTRA_VOICE_INTERACTION = "voice_interaction"
    }

    override fun onLaunchVoiceAssist(fromKeyguard: Boolean): Boolean {
        Log.d(TAG, "onLaunchVoiceAssist fromKeyguard=$fromKeyguard")
        launchIrisActivity()
        return true
    }

    override fun onLaunchVoiceAssistFromKeyguard(): Boolean {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        launchIrisActivity()
        return true
    }

    private fun launchIrisActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_VOICE_INTERACTION, true)
        }
        startActivity(intent)
    }
}
