package com.iris.assistant.data.tools.screen

import com.iris.assistant.data.tools.screen.ScreenInteractionRepository
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.overlay.ScreenActionGate
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypeTool @Inject constructor(
    private val screenRepository: ScreenInteractionRepository,
    private val actionGate: ScreenActionGate
) : JarvisTool {

    override val name = "type"
    override val description = "Odaklanmış metin kutusuna yazı yaz. Önce odaklanılacak alanı click ile seç, sonra type ile yaz."
    override val parameters = JSONObject("""
        {"type":"object","properties":{
            "text":{"type":"string","description":"Yazılacak metin"}
        },"required":["text"]}
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val approval = actionGate.awaitApproval("Yazma işlemi")
        if (approval is ToolResult.Cancelled) return approval

        val text = args.optString("text", "")
        if (text.isBlank()) return ToolResult.Error("Yazılacak metin boş.")

        val success = screenRepository.typeText(text)
        return if (success) ToolResult.Success("Metin kutusuna '$text' yazıldı.")
        else ToolResult.Error("Yazı yazılamadı. Lütfen önce bir metin kutusuna odaklan.")
    }
}
