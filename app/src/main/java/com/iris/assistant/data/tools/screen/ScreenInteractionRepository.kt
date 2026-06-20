package com.iris.assistant.data.tools.screen

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenInteractionRepository @Inject constructor() {

    companion object {
        private const val MAX_NODES = 80 // Max interactive nodes to expose to LLM
    }

    private val _screenDump = MutableStateFlow("")
    val screenDump: StateFlow<String> = _screenDump.asStateFlow()

    var rootNode: AccessibilityNodeInfo? = null
        private set

    // nodeId → AccessibilityNodeInfo map, rebuilt on every screen update
    // Key is the integer id assigned during flattenNode traversal
    private val nodeMap = HashMap<Int, AccessibilityNodeInfo>()

    fun updateRootNode(node: AccessibilityNodeInfo?) {
        rootNode?.let { if (it != node) it.recycle() }
        rootNode = node
        nodeMap.clear()
        _screenDump.value = node?.let { buildDump(it) } ?: ""
    }

    // ------------------------------------------------------------------
    // Node lookup — primary path for ClickTool / TypeTool
    // ------------------------------------------------------------------

    /** Look up a node by the integer id assigned in the last read_screen dump. */
    fun findNodeBySemanticId(id: Int): AccessibilityNodeInfo? = nodeMap[id]

    /** Fallback: find by visible text or contentDescription (case-insensitive, contains). */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findNodeByTextRecursive(root, text)
    }

    /** Fallback: find by viewIdResourceName fragment. */
    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findNodeByIdRecursive(root, viewId)
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    fun performClick(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun performLongClick(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

    fun typeText(chars: CharSequence): Boolean {
        val root = rootNode ?: return false
        val focused = findFocusedNode(root)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                chars
            )
        }
        return focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ?: false
    }

    fun findFocusedNode(): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findFocusedNode(root)
    }

    // ------------------------------------------------------------------
    // Dump builder — produces LLM-facing JSON and populates nodeMap
    // ------------------------------------------------------------------

    private fun buildDump(root: AccessibilityNodeInfo): String {
        val arr = JSONArray()
        var nextId = 0
        flattenNode(root, arr, nextId) { nextId = it }
        return arr.toString()
    }

    /**
     * DFS traversal. Emits one JSON entry per actionable/readable node.
     * Each entry gets a stable integer `id` stored in nodeMap so ClickTool
     * can resolve it without any text matching.
     *
     * Included nodes:
     *   - Clickable / long-clickable
     *   - Focusable input fields
     *   - Checkable items (checkboxes, toggles)
     *   - Any node with non-empty text or contentDescription that is visible
     *
     * UNTESTED — verify before use
     */
    private fun flattenNode(
        node: AccessibilityNodeInfo,
        arr: JSONArray,
        currentId: Int,
        updateId: (Int) -> Unit
    ) {
        if (arr.length() >= MAX_NODES) return
        if (!node.isVisibleToUser) {
            // Still traverse children — parent may be invisible container
            for (i in 0 until node.childCount) {
                if (arr.length() >= MAX_NODES) return
                node.getChild(i)?.let { child ->
                    flattenNode(child, arr, currentId, updateId)
                }
            }
            return
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = node.hintText?.toString()?.trim() // API 26+
        val className = node.className?.toString() ?: ""

        val isActionable = node.isClickable
                || node.isLongClickable
                || node.isFocusable
                || node.isCheckable

        val hasLabel = !text.isNullOrEmpty() || !desc.isNullOrEmpty() || !hint.isNullOrEmpty()

        // Include if actionable OR has readable label
        if (isActionable || hasLabel) {
            val id = currentId
            updateId(currentId + 1)

            // Store in map for guaranteed lookup later
            nodeMap[id] = node

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val entry = JSONObject().apply {
                put("id", id)
                if (!text.isNullOrEmpty()) put("text", text)
                if (!desc.isNullOrEmpty()) put("desc", desc)
                if (!hint.isNullOrEmpty()) put("hint", hint)
                put("type", resolveType(className, node))
                put("clickable", node.isClickable)
                put("focusable", node.isFocusable)
                put("checked", node.isChecked)
                put("enabled", node.isEnabled)
                put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
            }
            arr.put(entry)
        }

        for (i in 0 until node.childCount) {
            if (arr.length() >= MAX_NODES) return
            node.getChild(i)?.let { child ->
                flattenNode(child, arr, currentId, updateId)
            }
        }
    }

    /**
     * Maps Android className to a human-readable type string for LLM context.
     * Keeps the LLM prompt shorter and more semantic.
     */
    private fun resolveType(className: String, node: AccessibilityNodeInfo): String = when {
        node.isCheckable -> "checkbox"
        className.contains("EditText", ignoreCase = true) -> "input"
        className.contains("Button", ignoreCase = true) -> "button"
        className.contains("ImageView", ignoreCase = true) && node.isClickable -> "icon_button"
        className.contains("Switch", ignoreCase = true) -> "toggle"
        className.contains("TextView", ignoreCase = true) -> "text"
        className.contains("RecyclerView", ignoreCase = true) -> "list"
        className.contains("ScrollView", ignoreCase = true) -> "scroll_container"
        else -> "view"
    }

    // ------------------------------------------------------------------
    // Private recursive helpers
    // ------------------------------------------------------------------

    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()
        val nodeDesc = node.contentDescription?.toString()
        val matches = listOfNotNull(nodeText, nodeDesc)
            .any { it.contains(text, ignoreCase = true) }
        if (matches && node.isVisibleToUser && isActionable(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextRecursive(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByIdRecursive(
        node: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(viewId, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByIdRecursive(child, viewId)
            if (result != null) return result
        }
        return null
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun isActionable(node: AccessibilityNodeInfo): Boolean =
        node.isClickable || node.isLongClickable || node.isFocusable || node.isCheckable

    fun clear() {
        rootNode = null
        nodeMap.clear()
        _screenDump.value = ""
    }
}