package com.iris.assistant.service.voice

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IrisVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(intent: Intent?): VoiceInteractionSession {
        return IrisVoiceInteractionSession(this)
    }
}
