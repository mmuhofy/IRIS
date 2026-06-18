package com.iris.assistant.util

import org.json.JSONObject

object ToolCallParser {

    private fun findMatchingBraceEnd(text: CharSequence, startIdx: Int): Int {
        var depth = 0
        for (i in startIdx until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
        }
        return -1
    }

    fun extractFirst(text: String): Pair<String, JSONObject>? {
        val idx = text.indexOf("{\"tool\"")
        if (idx < 0) return null

        val end = findMatchingBraceEnd(text, idx)
        if (end < 0) return null

        val jsonStr = text.substring(idx, end)
        return try {
            val json = JSONObject(jsonStr)
            val name = json.optString("tool", "")
            if (name.isBlank()) return null
            val args = json.optJSONObject("args") ?: JSONObject()
            Pair(name, args)
        } catch (_: Exception) {
            null
        }
    }

    fun stripAll(text: String): String {
        val sb = StringBuilder(text)
        var i = 0
        while (i < sb.length) {
            if (sb[i] == '{') {
                val end = findMatchingBraceEnd(sb, i)
                if (end > 0) {
                    val candidate = sb.substring(i, end)
                    if (candidate.contains("\"tool\"")) {
                        sb.delete(i, end)
                        continue
                    }
                }
            }
            i++
        }
        return sb.toString().trim()
    }
}
