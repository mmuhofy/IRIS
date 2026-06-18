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
        private const val MAX_DUMP_CHARS = 5000
    }

    private val _screenDump = MutableStateFlow("")
    val screenDump: StateFlow<String> = _screenDump.asStateFlow()

    var rootNode: AccessibilityNodeInfo? = null
        private set

    fun updateRootNode(node: AccessibilityNodeInfo?) {
        rootNode?.let {
            if (it != node) it.recycle()
        }
        rootNode = node
        _screenDump.value = node?.let { nodeToJson(it).toString() } ?: ""
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findNodeByTextRecursive(root, text)
    }

    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findNodeByIdRecursive(root, viewId)
    }

    fun findNodeByViewTag(tag: String): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findNodeByViewTagRecursive(root, tag)
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()
        val nodeDesc = node.contentDescription?.toString()
        val matchText = listOfNotNull(nodeText, nodeDesc).any { it.contains(text, ignoreCase = true) }

        if (matchText && node.isVisibleToUser && isActionable(node)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextRecursive(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByIdRecursive(node: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(viewId, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByIdRecursive(child, viewId)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByViewTagRecursive(node: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(tag, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(tag, ignoreCase = true) == true
        ) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByViewTagRecursive(child, tag)
            if (result != null) return result
        }
        return null
    }

    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun typeText(chars: CharSequence): Boolean {
        val root = rootNode ?: return false
        val focused = findFocusedNode(root)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, chars) }
        return focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ?: false
    }

    fun findFocusedNode(): AccessibilityNodeInfo? {
        val root = rootNode ?: return null
        return findFocusedNode(root)
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

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isLongClickable ||
                node.isFocusable || node.isCheckable ||
                node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    private var charCount = 0

    private fun nodeToJson(node: AccessibilityNodeInfo): JSONArray {
        charCount = 0
        val arr = JSONArray()
        flattenNode(node, arr)
        return arr
    }

    private fun flattenNode(node: AccessibilityNodeInfo, arr: JSONArray) {
        if (charCount >= MAX_DUMP_CHARS) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        val hasText = !text.isNullOrEmpty() || !desc.isNullOrEmpty() ||
                (node.className?.toString()?.contains("Button", ignoreCase = true) == true &&
                        (text != null || desc != null))

        if (node.isVisibleToUser && hasText) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val entry = JSONObject()
            text?.let { entry.put("text", it) }
            desc?.let { entry.put("description", it) }
            entry.put("className", node.className?.toString() ?: "unknown")
            entry.put("bounds", "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            entry.put("clickable", node.isClickable)
            entry.put("focusable", node.isFocusable)
            entry.put("enabled", node.isEnabled)

            val jsonStr = entry.toString()
            charCount += jsonStr.length
            if (charCount <= MAX_DUMP_CHARS) {
                arr.put(entry)
            }
        }

        for (i in 0 until node.childCount) {
            if (charCount >= MAX_DUMP_CHARS) return
            node.getChild(i)?.let { flattenNode(it, arr) }
        }
    }

    fun clear() {
        rootNode = null
        _screenDump.value = ""
    }
}
