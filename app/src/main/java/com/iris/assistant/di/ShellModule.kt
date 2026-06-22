package com.iris.assistant.di

import com.iris.assistant.data.shell.BootstrapDownloader
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Phase 4 Power Mode / Embedded Shell dependencies.
 *
 * [BootstrapDownloader] is a @Singleton annotated with @Inject constructor,
 * so Hilt resolves it automatically — no explicit @Provides needed here.
 *
 * This module exists as a placeholder for future shell-related bindings
 * (e.g. EmbeddedShell interface → EmbeddedShellImpl) in subsequent steps.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule