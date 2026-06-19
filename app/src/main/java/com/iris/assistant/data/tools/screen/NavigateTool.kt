package com.iris.assistant.data.tools.screen

import android.accessibilityservice.AccessibilityService
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.accessibility.IrisAccessibilityService
import com.iris.assistant.service.overlay.ScreenActionGate
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigateTool @Inject constructor(
    private val actionGate: ScreenActionGate
) : JarvisTool {

    override val name = "navigate"
    override val description = "Sistem gezinme işlemleri: 'back' (geri git), 'home' (ana ekran), 'recents' (son uygulamalar). Kullanıcı 'geri dön' veya 'ana ekrana git' gibi bir şey söylerse doğrudan çağır, önce read_screen gerekmez."
    override val parameters = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","description":"Yapılacak işlem: 'back', 'home', 'recents'"}
        },"required":["action"]}
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val action = args.optString("action", "")
        val label = when (action) {
            "back"    -> "Geri git"
            "home"    -> "Ana ekrana git"
            "recents" -> "Son uygulamalar"
            else -> return ToolResult.Error("Geçersiz işlem: '$action'. 'back', 'home' veya 'recents' kullan.")
        }

        val approval = actionGate.awaitApproval(label)
        if (approval is ToolResult.Cancelled) return approval

        val globalAction = when (action) {
            "back"    -> AccessibilityService.GLOBAL_ACTION_BACK
            "home"    -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return ToolResult.Error("Geçersiz işlem: '$action'. 'back', 'home' veya 'recents' kullan.")
        }

        val service = IrisAccessibilityService.instance
        if (service == null) {
            return ToolResult.Error("Erişilebilirlik servisine erişilemiyor.")
        }

        val success = service.performGlobalActionCompat(globalAction)
        return if (success) ToolResult.Success("$action işlemi gerçekleştirildi.")
        else ToolResult.Error("$action işlemi başarısız oldu.")
    }
}
