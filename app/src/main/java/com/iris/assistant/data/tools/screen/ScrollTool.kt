package com.iris.assistant.data.tools.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.service.accessibility.IrisAccessibilityService
import com.iris.assistant.service.overlay.ScreenActionGate
import com.iris.assistant.util.Constants
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrollTool @Inject constructor(
    private val screenRepository: ScreenInteractionRepository,
    private val actionGate: ScreenActionGate
) : JarvisTool {

    override val name = "scroll"

    override val description = """
        Scroll a list, page, or scrollable container on screen.
        PREFERRED: provide nodeId of the scrollable container from read_screen (type: list or scroll_container).
        FALLBACK (no nodeId): scrolls the first scrollable container found on screen.
        direction: 'down' or 'up'.
        Use read_screen first to identify the correct scrollable area when multiple lists are present.
        Strategy: ACTION_SCROLL_FORWARD/BACKWARD is used (reliable, no hardcoded coordinates).
    """.trimIndent()

    override val parameters = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "direction": {
              "type": "string",
              "description": "'down' to scroll forward (towards end), 'up' to scroll backward (towards start)."
            },
            "nodeId": {
              "type": "integer",
              "description": "Preferred: the 'id' field from read_screen for the scrollable container. If omitted, the first scrollable node is used."
            }
          },
          "required": ["direction"]
        }
        """.trimIndent()
    )

    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val direction = args.optString("direction", "").trim().lowercase()
        if (direction != "down" && direction != "up") {
            return ToolResult.Error("Invalid direction: '$direction'. Use 'down' or 'up'.")
        }

        val nodeId = if (args.has("nodeId")) args.optInt("nodeId", -1) else -1

        // Resolve scroll target
        // UNTESTED — verify before use
        val scrollNode: AccessibilityNodeInfo? = when {
            nodeId >= 0 -> {
                val n = screenRepository.findNodeBySemanticId(nodeId)
                if (n == null) {
                    return ToolResult.Error(
                        "Scroll target id=$nodeId not found. Screen may have changed — call read_screen again."
                    )
                }
                if (!n.isScrollable) {
                    return ToolResult.Error(
                        "Element id=$nodeId is not scrollable. Use read_screen to find a list or scroll_container node."
                    )
                }
                n
            }
            else -> findFirstScrollable(screenRepository.rootNode)
        }

        if (scrollNode == null) {
            return ToolResult.Error(
                "No scrollable element found on screen. " +
                "Use read_screen and provide a nodeId with type 'list' or 'scroll_container'."
            )
        }

        // Overlay highlight at center of scroll container
        val bounds = Rect().also { scrollNode.getBoundsInScreen(it) }
        val label = if (direction == "down") "Scroll down" else "Scroll up"
        val approval = actionGate.awaitApproval(label, x = bounds.centerX(), y = bounds.centerY())
        if (approval is ToolResult.Cancelled) return approval

        // Use AccessibilityNodeInfo actions — no hardcoded coordinates
        // UNTESTED — verify before use
        val action = if (direction == "down") {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        val success = scrollNode.performAction(action)
        if (success) {
            return ToolResult.Success("Scrolled $direction successfully.")
        }

        // AccessibilityNodeInfo scroll failed — fall back to gesture with real screen dimensions
        return gestureScroll(direction)
    }

    /**
     * Gesture-based scroll fallback.
     * Uses real display metrics instead of hardcoded pixel values.
     * Only called when ACTION_SCROLL_FORWARD/BACKWARD fails.
     * UNTESTED — verify before use
     */
    private fun gestureScroll(direction: String): ToolResult {
        val service = IrisAccessibilityService.instance
            ?: return ToolResult.Error("AccessibilityService not running. Cannot dispatch scroll gesture.")

        val display = service.resources.displayMetrics
        val screenWidth = display.widthPixels
        val screenHeight = display.heightPixels

        val centerX = screenWidth / 2
        // Scroll down gesture: finger moves UP (start near bottom, end near top)
        // Scroll up gesture:   finger moves DOWN (start near top, end near bottom)
        val startY: Int
        val endY: Int
        if (direction == "down") {
            startY = (screenHeight * Constants.SCROLL_GESTURE_START_RATIO).toInt()
            endY   = (screenHeight * Constants.SCROLL_GESTURE_END_RATIO).toInt()
        } else {
            startY = (screenHeight * Constants.SCROLL_GESTURE_END_RATIO).toInt()
            endY   = (screenHeight * Constants.SCROLL_GESTURE_START_RATIO).toInt()
        }

        val path = android.graphics.Path().apply {
            moveTo(centerX.toFloat(), startY.toFloat())
            lineTo(centerX.toFloat(), endY.toFloat())
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
            path,
            /* startTime= */ 0L,
            /* duration= */ Constants.SCROLL_GESTURE_DURATION_MS
        )
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(gesture, null, null)
        return ToolResult.Success("Scrolled $direction via gesture fallback.")
    }

    /**
     * DFS to find the first scrollable node in the accessibility tree.
     * Used when no nodeId is provided.
     */
    private fun findFirstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val result = findFirstScrollable(node.getChild(i))
            if (result != null) return result
        }
        return null
    }
}