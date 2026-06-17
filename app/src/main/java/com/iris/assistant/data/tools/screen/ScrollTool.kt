package com.iris.assistant.data.tools.screen

import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.accessibility.IrisAccessibilityService
import com.iris.assistant.service.overlay.ScreenActionGate
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrollTool @Inject constructor(
    private val actionGate: ScreenActionGate
) : JarvisTool {

    override val name = "scroll"
    override val description = "Ekranda aşağı veya yukarı kaydır. direction: 'down' veya 'up'."
    override val parameters = JSONObject("""
        {"type":"object","properties":{
            "direction":{"type":"string","description":"Kaydırma yönü: 'down' (aşağı) veya 'up' (yukarı)"}
        },"required":["direction"]}
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val direction = args.optString("direction", "")

        val label = when (direction) {
            "down" -> "Aşağı kaydır"
            "up" -> "Yukarı kaydır"
            else -> return ToolResult.Error("Geçersiz yön: '$direction'. 'down' veya 'up' kullan.")
        }

        val startX = 500
        val startY = if (direction == "down") 800 else 200
        val endY = if (direction == "down") 200 else 800

        val approval = actionGate.awaitApproval(label, x = startX, y = startY)
        if (approval is ToolResult.Cancelled) return approval

        val service = IrisAccessibilityService.instance
        if (service == null) {
            return ToolResult.Error("Erişilebilirlik servisine erişilemiyor.")
        }

        val path = android.graphics.Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), endY.toFloat())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200))
            .build()

        service.dispatchGesture(gesture, null, null)
        return ToolResult.Success("Ekran $direction kaydırıldı.")
    }
}
