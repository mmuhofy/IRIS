package com.iris.assistant.service.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IrisVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle): VoiceInteractionSession {
        return IrisVoiceInteractionSession(this)
    }
}
