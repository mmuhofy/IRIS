package com.iris.assistant.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrimaryLlm

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FallbackLlm