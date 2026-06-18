package com.iris.assistant.service.voice

import com.iris.assistant.data.audio.AudioRecorder
import com.iris.assistant.data.remote.tts.TtsProvider
import com.iris.assistant.domain.usecase.SendMessageUseCase
import com.iris.assistant.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceInteractionEntryPoint {
    fun audioRecorder(): AudioRecorder
    fun okHttpClient(): OkHttpClient
    fun transcribeAudioUseCase(): TranscribeAudioUseCase
    fun sendMessageUseCase(): SendMessageUseCase
    fun ttsProvider(): TtsProvider
}
