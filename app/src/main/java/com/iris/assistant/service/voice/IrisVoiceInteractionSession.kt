package com.iris.assistant.service.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.iris.assistant.ui.MainActivity

/**
 * Session created when the user activates IRIS via the default assistant.
 * onShow opens MainActivity so the voice pipeline begins immediately.
 */
class IrisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "IrisVoiceInteractionSession"
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow")
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(IrisVoiceInteractionService.EXTRA_VOICE_INTERACTION, true)
        }
        startAssistantActivity(intent)
    }
}
