package com.iris.assistant.di

import com.iris.assistant.domain.tools.CalculateTool
import com.iris.assistant.domain.tools.GetCurrentTimeTool
import com.iris.assistant.domain.tools.JarvisTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module that registers all JarvisTool implementations into a Set<JarvisTool>.
 * ToolRegistry receives this set via constructor injection.
 *
 * To add a new tool:
 *   1. Create the tool class implementing JarvisTool
 *   2. Add a @Binds @IntoSet entry here
 *   3. That's it — ToolRegistry picks it up automatically
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds @IntoSet
    abstract fun bindGetCurrentTimeTool(impl: GetCurrentTimeTool): JarvisTool

    @Binds @IntoSet
    abstract fun bindCalculateTool(impl: CalculateTool): JarvisTool
}