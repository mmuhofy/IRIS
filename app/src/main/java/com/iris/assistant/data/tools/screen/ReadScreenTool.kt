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

    override val description = """
        Returns all visible and interactive elements on the current screen as a JSON array.
        ALWAYS call this before click, type, or scroll.
        Each element has:
          - id (integer): use this in click(nodeId=...) — guaranteed, never guess text
          - text: visible label if present
          - desc: contentDescription if present (used for icon-only buttons)
          - hint: input field placeholder if present
          - type: button | input | icon_button | checkbox | toggle | text | list | view
          - clickable / focusable / checked / enabled: boolean flags
          - bounds: "left,top,right,bottom" in screen pixels
        Strategy: find the element by text/desc/hint, then pass its id to click(nodeId=...).
        If no element matches by text, try desc or hint fields.
    """.trimIndent()

    override val parameters = JSONObject("""{"type":"object","properties":{},"required":[]}""")
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val dump = screenRepository.screenDump.value
        if (dump.isBlank()) {
            return ToolResult.Error(
                "Screen data unavailable. Make sure the Accessibility Service is enabled."
            )
        }
        return ToolResult.Success(
            displayText = "Screen elements retrieved. Use the 'id' field in click(nodeId=...) — do not guess by text alone.\n$dump",
            data = mapOf("screen" to dump)
        )
    }
}