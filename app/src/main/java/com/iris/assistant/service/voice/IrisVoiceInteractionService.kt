package com.iris.assistant.service.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class IrisVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IrisVoiceInteractionService"
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        showSession(Bundle.EMPTY, 0)
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "onReady")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "onShutdown")
    }
}
