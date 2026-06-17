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
        val approval = actionGate.awaitApproval("Kaydırma işlemi")
        if (approval is ToolResult.Cancelled) return approval

        val direction = args.optString("direction", "")
        val action = when (direction) {
            "down" -> android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED to true
            "up" -> android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED to false
            else -> return ToolResult.Error("Geçersiz yön: '$direction'. 'down' veya 'up' kullan.")
        }

        val service = IrisAccessibilityService.instance
        if (service == null) {
            return ToolResult.Error("Erişilebilirlik servisine erişilemiyor.")
        }

        val path = android.graphics.Path().apply {
            if (direction == "down") {
                moveTo(500f, 800f)
                lineTo(500f, 200f)
            } else {
                moveTo(500f, 200f)
                lineTo(500f, 800f)
            }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200))
            .build()

        service.dispatchGesture(gesture, null, null)
        return ToolResult.Success("Ekran $direction kaydırıldı.")
    }
}
