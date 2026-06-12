package com.iris.assistant.di

import com.iris.assistant.data.remote.tts.AndroidTtsClient
import com.iris.assistant.data.remote.tts.TtsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    @Binds
    @Singleton
    abstract fun bindTtsProvider(impl: AndroidTtsClient): TtsProvider
}