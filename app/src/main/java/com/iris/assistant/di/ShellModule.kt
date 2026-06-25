package com.iris.assistant.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Phase 4 Power Mode / Embedded Shell.
 *
 * [BootstrapInstaller] and [EmbeddedShell] are both @Singleton + @Inject constructor,
 * so Hilt resolves them automatically — no explicit @Provides needed.
 *
 * BootstrapDownloader has been removed. Bootstrap zip is now embedded in
 * app/src/main/assets/ and extracted by BootstrapInstaller at runtime.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule