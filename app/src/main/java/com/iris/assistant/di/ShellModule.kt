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
 * Bootstrap is embedded in libiris-bootstrap.so via NDK (jni/) and extracted
 * by BootstrapInstaller at runtime through JNI.
 */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule