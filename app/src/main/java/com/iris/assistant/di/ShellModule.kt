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
 * BootstrapDownloader has been removed. Bootstrap is now embedded in the APK
 * as libiris-bootstrap.so via NDK and extracted by BootstrapInstaller.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule