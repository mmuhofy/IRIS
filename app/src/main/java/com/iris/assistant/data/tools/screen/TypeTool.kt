package com.iris.assistant.data.tools.screen

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
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

    override val description = """
        Type text into an input field.
        WORKFLOW: read_screen → find input field id → click(nodeId=...) to focus → type(nodeId=..., text=...)
        PREFERRED: provide nodeId from read_screen to target the exact input field.
        FALLBACK: if nodeId is omitted, types into the currently focused field (may fail if nothing is focused).
        Do NOT call type without first calling click to focus the target field.
    """.trimIndent()

    override val parameters = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "text": {
              "type": "string",
              "description": "Text to type into the input field."
            },
            "nodeId": {
              "type": "integer",
              "description": "Preferred: the 'id' field from read_screen for the target input field. If provided, the tool will focus the field before typing."
            },
            "append": {
              "type": "boolean",
              "description": "If true, appends to existing text. If false (default), replaces existing content."
            }
          },
          "required": ["text"]
        }
        """.trimIndent()
    )

    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val text = args.optString("text", "").trim()
        if (text.isBlank()) return ToolResult.Error("Text to type is empty.")

        val nodeId = if (args.has("nodeId")) args.optInt("nodeId", -1) else -1
        val append = args.optBoolean("append", false)

        // Resolve target node for overlay highlight
        val targetNode: AccessibilityNodeInfo? = when {
            nodeId >= 0 -> screenRepository.findNodeBySemanticId(nodeId)
            else -> screenRepository.findFocusedNode()
        }

        val (hx, hy) = targetNode?.let {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.centerX() to r.centerY()
        } ?: (-1 to -1)

        val label = "Type: '$text'"
        val approval = actionGate.awaitApproval(label, x = hx.takeIf { it >= 0 }, y = hy.takeIf { it >= 0 })
        if (approval is ToolResult.Cancelled) return approval

        // If nodeId was given, focus the field first via ACTION_CLICK, then type
        // UNTESTED — verify before use
        if (nodeId >= 0 && targetNode != null) {
            val focused = targetNode.isFocused
            if (!focused) {
                // Attempt to focus by clicking — most reliable way to focus an EditText
                val clicked = screenRepository.performClick(targetNode)
                if (!clicked) {
                    return ToolResult.Error(
                        "Could not focus input field id=$nodeId. " +
                        "Try calling click(nodeId=$nodeId) first, then type again."
                    )
                }
            }

            val finalText = if (append) {
                val existing = targetNode.text?.toString() ?: ""
                existing + text
            } else {
                text
            }

            val args2 = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    finalText
                )
            }
            val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args2)
            return if (success) {
                ToolResult.Success("Typed '$finalText' into field id=$nodeId.")
            } else {
                ToolResult.Error(
                    "ACTION_SET_TEXT failed for id=$nodeId. " +
                    "Field may not be editable or may no longer be on screen."
                )
            }
        }

        // Fallback: type into whatever is currently focused
        val success = screenRepository.typeText(text)
        return if (success) {
            ToolResult.Success("Typed '$text' into focused field.")
        } else {
            ToolResult.Error(
                "No focused input field found. " +
                "Call read_screen to find the input field id, then click(nodeId=...) to focus it first."
            )
        }
    }
}