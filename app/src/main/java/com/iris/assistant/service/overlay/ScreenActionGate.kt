package com.iris.assistant.service.overlay

import android.content.Context
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenActionGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun awaitApproval(
        actionLabel: String,
        x: Int? = null,
        y: Int? = null
    ): ToolResult {
        val prefs = preferencesRepository.preferences.first()
        val level = prefs.autonomyLevel

        if (level == AutonomyLevel.FULL_AUTO) {
            return ToolResult.Success(actionLabel)
        }

        val previewMs = level.previewSeconds * 1000L
        if (previewMs <= 0) return ToolResult.Success(actionLabel)

        return ActionPreviewOverlay(context).show(
            actionLabel = actionLabel,
            x = x,
            y = y,
            previewMs = previewMs
        )
    }
}
