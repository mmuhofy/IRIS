package com.iris.assistant.data.tools.screen

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.accessibility.IrisAccessibilityService
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

    override val description = """
        Click a UI element on screen.
        PREFERRED: use nodeId from read_screen output — this is always reliable.
        FALLBACK: use text or desc if nodeId is unavailable (less reliable, may fail on icon-only buttons).
        DO NOT guess coordinates without read_screen first.
        Resolution order: nodeId → text → desc → (x,y) gesture.
    """.trimIndent()

    override val parameters = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "nodeId": {
              "type": "integer",
              "description": "Preferred: the 'id' field from read_screen output. Guaranteed match."
            },
            "text": {
              "type": "string",
              "description": "Fallback: visible text of the element (case-insensitive contains match)."
            },
            "desc": {
              "type": "string",
              "description": "Fallback: contentDescription of the element (for icon-only buttons)."
            },
            "x": {
              "type": "integer",
              "description": "Last resort: x coordinate for gesture tap."
            },
            "y": {
              "type": "integer",
              "description": "Last resort: y coordinate for gesture tap."
            }
          },
          "required": []
        }
        """.trimIndent()
    )

    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val nodeId = if (args.has("nodeId")) args.optInt("nodeId", -1) else -1
        val text = args.optString("text", "").trim()
        val desc = args.optString("desc", "").trim()
        val x = args.optInt("x", -1)
        val y = args.optInt("y", -1)

        // Resolve target node and human-readable label for overlay
        val (targetRect, label) = resolveTarget(nodeId, text, desc, x, y)

        // Show ActionPreviewOverlay and wait for approval per Autonomy Level
        val highlightX = targetRect?.centerX()
        val highlightY = targetRect?.centerY()
        val approval = actionGate.awaitApproval(label, x = highlightX, y = highlightY)
        if (approval is ToolResult.Cancelled) return approval

        // Execute click using resolved strategy
        return performClick(nodeId, text, desc, x, y)
    }

    // ------------------------------------------------------------------
    // Resolution: find best rect + label for overlay highlight
    // ------------------------------------------------------------------

    private fun resolveTarget(
        nodeId: Int,
        text: String,
        desc: String,
        x: Int,
        y: Int
    ): Pair<Rect?, String> {
        if (nodeId >= 0) {
            val node = screenRepository.findNodeBySemanticId(nodeId)
            if (node != null) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                val label = buildLabel(node.text?.toString(), node.contentDescription?.toString(), nodeId)
                return rect to label
            }
        }
        if (text.isNotBlank()) {
            val node = screenRepository.findNodeByText(text)
            if (node != null) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                return rect to "Tap: '$text'"
            }
        }
        if (desc.isNotBlank()) {
            val node = screenRepository.findNodeByText(desc)
            if (node != null) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                return rect to "Tap: '$desc'"
            }
        }
        if (x >= 0 && y >= 0) {
            return Rect(x - 50, y - 50, x + 50, y + 50) to "Tap: ($x, $y)"
        }
        return null to "Tap"
    }

    private fun buildLabel(text: String?, desc: String?, nodeId: Int): String = when {
        !text.isNullOrBlank() -> "Tap: '$text' (id=$nodeId)"
        !desc.isNullOrBlank() -> "Tap: '$desc' (id=$nodeId)"
        else -> "Tap: element id=$nodeId"
    }

    // ------------------------------------------------------------------
    // Execution: same resolution order, this time actually performing click
    // UNTESTED — verify before use
    // ------------------------------------------------------------------

    private suspend fun performClick(
        nodeId: Int,
        text: String,
        desc: String,
        x: Int,
        y: Int
    ): ToolResult {
        // 1. Preferred: semantic node id
        if (nodeId >= 0) {
            val node = screenRepository.findNodeBySemanticId(nodeId)
            if (node != null) {
                return if (screenRepository.performClick(node))
                    ToolResult.Success(displayText = "Clicked element id=$nodeId.")
                else
                    ToolResult.Error("performAction(ACTION_CLICK) failed for id=$nodeId. Node may no longer be on screen.")
            }
            // nodeId was provided but node is gone — screen changed between read_screen and click
            return ToolResult.Error(
                "Element id=$nodeId not found. Screen may have changed. Call read_screen again."
            )
        }

        // 2. Text fallback
        if (text.isNotBlank()) {
            val node = screenRepository.findNodeByText(text)
            if (node != null) {
                return if (screenRepository.performClick(node))
                    ToolResult.Success(displayText = "Clicked '$text'.")
                else
                    ToolResult.Error("performAction(ACTION_CLICK) failed for text='$text'.")
            }
        }

        // 3. Description fallback
        if (desc.isNotBlank()) {
            val node = screenRepository.findNodeByText(desc)
            if (node != null) {
                return if (screenRepository.performClick(node))
                    ToolResult.Success(displayText = "Clicked '$desc'.")
                else
                    ToolResult.Error("performAction(ACTION_CLICK) failed for desc='$desc'.")
            }
        }

        // 4. Coordinate gesture — last resort
        if (x >= 0 && y >= 0) {
            val service = IrisAccessibilityService.instance
                ?: return ToolResult.Error("AccessibilityService not running. Cannot dispatch gesture.")
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            return ToolResult.Success(displayText = "Dispatched tap gesture at ($x, $y).")
        }

        return ToolResult.Error(
            "No matching element found. Provide nodeId (from read_screen), text, desc, or (x,y)."
        )
    }
}