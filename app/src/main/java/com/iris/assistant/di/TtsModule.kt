package com.iris.assistant.di

import com.iris.assistant.data.remote.tts.AndroidTtsClient
import com.iris.assistant.data.remote.tts.GeminiTtsClient
import com.iris.assistant.data.remote.tts.MmsTtsClient
import com.iris.assistant.data.remote.tts.TtsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    @Binds
    @Singleton
    abstract fun bindDefaultTts(impl: GeminiTtsClient): TtsProvider

    @Binds
    @Named("gemini")
    @Singleton
    abstract fun bindGeminiTts(impl: GeminiTtsClient): TtsProvider

    @Binds
    @Named("mms")
    @Singleton
    abstract fun bindMmsTts(impl: MmsTtsClient): TtsProvider

    @Binds
    @Named("android")
    @Singleton
    abstract fun bindAndroidTts(impl: AndroidTtsClient): TtsProvider
}
