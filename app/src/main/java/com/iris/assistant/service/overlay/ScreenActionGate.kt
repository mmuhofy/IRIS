package com.iris.assistant.service.overlay

import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.AutonomyLevel
import com.iris.assistant.domain.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenActionGate @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun awaitApproval(actionLabel: String): ToolResult {
        val prefs = preferencesRepository.preferences.first()
        val level = prefs.autonomyLevel
        val previewMs = level.previewSeconds * 1000L

        // FULL_AUTO — no preview
        if (level == AutonomyLevel.FULL_AUTO) {
            return ToolResult.Success(actionLabel)
        }

        // For now without system overlay, just add the delay
        if (previewMs > 0) {
            delay(previewMs)
        }

        return ToolResult.Success(actionLabel)
    }
}
