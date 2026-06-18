package com.iris.assistant.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.iris.assistant.ui.assistant.AssistantActivity

/**
 * Session created when the user activates IRIS via the default assistant.
 * onShow opens the lightweight AssistantActivity so the voice pipeline
 * begins immediately without launching the full main app.
 */
class IrisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "IrisVoiceInteractionSession"
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow")
        val intent = Intent(context, AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startAssistantActivity(intent)
    }
}
