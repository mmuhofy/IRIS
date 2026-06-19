package com.iris.assistant.data.tools.screen

import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadScreenTool @Inject constructor(
    private val screenRepository: ScreenInteractionRepository
) : JarvisTool {

    override val name = "read_screen"
    override val description = "Ekrandaki tüm görünür metinleri, butonları ve etkileşimli öğeleri JSON formatında listele. Her öğe için text, description, bounds, className bilgilerini döndürür. Bir ekran işlemi yapmadan ÖNCE mutlaka çağır. Sonuçtaki text/description değerlerini click, type veya scroll tool'larında kullan."
    override val parameters = JSONObject("""{"type":"object","properties":{},"required":[]}""")
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val dump = screenRepository.screenDump.value
        if (dump.isBlank()) {
            return ToolResult.Error("Ekran bilgisi alınamadı. Erişilebilirlik servisinin açık olduğundan emin ol.")
        }
        return ToolResult.Success("Ekran içeriği alındı. İşte ekrandaki öğeler:\n$dump", data = mapOf("screen" to dump))
    }
}
