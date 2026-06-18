package com.iris.assistant.service.voice

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log
import com.iris.assistant.ui.assistant.AssistantActivity

class IrisVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IrisVoiceInteractionService"
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        val intent = Intent(this, AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
