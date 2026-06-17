package com.iris.assistant.domain.model

enum class AutonomyLevel(
    val displayName: String,
    val previewSeconds: Int,
    val requireConfirmDestructive: Boolean,
) {
    SAFE(displayName = "Güvenli", previewSeconds = 3, requireConfirmDestructive = true),
    BALANCED(displayName = "Dengeli", previewSeconds = 1, requireConfirmDestructive = true),
    FULL_AUTO(displayName = "Tam Otomatik", previewSeconds = 0, requireConfirmDestructive = false);
}
