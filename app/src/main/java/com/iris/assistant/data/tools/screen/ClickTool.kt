package com.iris.assistant.data.tools.screen

import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.overlay.ScreenActionGate
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClickTool @Inject constructor(
    private val screenRepository: ScreenInteractionRepository,
    private val actionGate: ScreenActionGate
) : JarvisTool {

    override val name = "click"
    override val description = "Ekranda bir buton veya öğeye tıkla. Öğeyi metniyle (text) veya açıklamasıyla (description) belirt. Eşleşen öğe bulunamazsa koordinat (x, y) dene."
    override val parameters = JSONObject("""
        {"type":"object","properties":{
            "text":{"type":"string","description":"Tıklanacak öğenin metni (buton üzerindeki yazı)"},
            "description":{"type":"string","description":"Tıklanacak öğenin contentDescription değeri"},
            "x":{"type":"integer","description":"Tıklanacak x koordinatı (text/description bulunamazsa)"},
            "y":{"type":"integer","description":"Tıklanacak y koordinatı (text/description bulunamazsa)"}
        },"required":[]}
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val text = args.optString("text", "")
        val description = args.optString("description", "")
        val x = args.optInt("x", -1)
        val y = args.optInt("y", -1)

        val targetRect = findTargetRect(text, description, x, y)
        val label = buildLabel(text, description, x, y)
        val highlightX = targetRect?.centerX()
        val highlightY = targetRect?.centerY()

        val approval = actionGate.awaitApproval(label, x = highlightX, y = highlightY)
        if (approval is ToolResult.Cancelled) return approval

        return performClick(text, description, x, y)
    }

    private fun findTargetRect(
        text: String, description: String, x: Int, y: Int
    ): android.graphics.Rect? {
        if (text.isNotBlank()) {
            val node = screenRepository.findNodeByText(text)
            if (node != null) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                return rect
            }
        }
        if (description.isNotBlank()) {
            val node = screenRepository.findNodeByText(description)
            if (node != null) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                return rect
            }
        }
        if (x >= 0 && y >= 0) {
            return android.graphics.Rect(x - 50, y - 50, x + 50, y + 50)
        }
        return null
    }

    private fun buildLabel(
        text: String, description: String, x: Int, y: Int
    ): String {
        return when {
            text.isNotBlank() -> "Tıklama: '$text'"
            description.isNotBlank() -> "Tıklama: '$description'"
            x >= 0 && y >= 0 -> "Tıklama: ($x, $y)"
            else -> "Tıklama"
        }
    }

    private suspend fun performClick(
        text: String, description: String, x: Int, y: Int
    ): ToolResult {
        if (text.isNotBlank()) {
            val node = screenRepository.findNodeByText(text)
            if (node != null) {
                val success = screenRepository.performClick(node)
                return if (success) ToolResult.Success("'$text' butonuna tıklandı.")
                else ToolResult.Error("'$text' butonuna tıklanamadı.")
            }
        }

        if (description.isNotBlank()) {
            val node = screenRepository.findNodeByText(description)
            if (node != null) {
                val success = screenRepository.performClick(node)
                return if (success) ToolResult.Success("'$description' öğesine tıklandı.")
                else ToolResult.Error("'$description' öğesine tıklanamadı.")
            }
        }

        if (x >= 0 && y >= 0) {
            val service = com.iris.assistant.service.accessibility.IrisAccessibilityService.instance
            if (service != null) {
                val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                service.dispatchGesture(gesture, null, null)
                return ToolResult.Success("($x, $y) koordinatına tıklandı.")
            }
            return ToolResult.Error("Erişilebilirlik servisine erişilemiyor.")
        }

        return ToolResult.Error("Tıklanacak öğe bulunamadı. Lütfen text, description veya koordinat (x,y) belirt.")
    }
}
