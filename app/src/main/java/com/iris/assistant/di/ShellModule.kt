package com.iris.assistant.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Phase 4 Power Mode / Shell.
 *
 * [BootstrapInstaller] and [IrisShellSession] are both @Singleton + @Inject constructor,
 * so Hilt resolves them automatically — no explicit @Provides needed.
 *
 * Bootstrap zip is downloaded at runtime from GitHub releases on first install.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule