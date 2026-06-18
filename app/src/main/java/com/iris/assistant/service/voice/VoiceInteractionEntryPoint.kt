package com.iris.assistant.service.voice

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for IrisVoiceInteractionSession to access singleton-scoped
 * dependencies (e.g. UseCases, Repositories) without being a Hilt-injected class.
 *
 * Usage from session:
 *   val entryPoint = EntryPointAccessors.fromApplication(
 *       context.applicationContext,
 *       VoiceInteractionEntryPoint::class.java
 *   )
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceInteractionEntryPoint
