package com.iris.assistant.domain.tools

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Built-in tools that require no Android permissions and no external APIs.
 * These are always available regardless of user permission state.
 *
 * Tools in this file:
 *   - GetCurrentTimeTool  → get_current_time
 *   - CalculateTool       → calculate
 */

// ---------------------------------------------------------------------------
// get_current_time
// ---------------------------------------------------------------------------

/**
 * Returns the current date and time in Turkish locale format.
 * No parameters required.
 *
 * Example Gemini interaction:
 *   User: "Saat kaç?"
 *   Gemini: function_call { name: "get_current_time", args: {} }
 *   Tool:   ToolResult.Success("Şu an saat 14:35, 15 Haziran 2026 Pazartesi.")
 *   Gemini: "Şu an saat 14:35, 15 Haziran 2026 Pazartesi."
 */
class GetCurrentTimeTool @Inject constructor() : JarvisTool {

    override val name        = "get_current_time"
    override val description = "Returns the current date and time. Call this when the user asks what time or date it is."
    override val parameters  = JSONObject("""{"type": "object", "properties": {}}""")
    override val requiredPermission: String? = null

    // UNTESTED — verify date format output before use
    override suspend fun execute(args: JSONObject): ToolResult {
        return runCatching {
            val now        = Date()
            val timeFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
            val dateFormat = SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr", "TR"))
            val time       = timeFormat.format(now)
            val date       = dateFormat.format(now)

            ToolResult.Success(
                displayText = "Şu an saat $time, $date.",
                data        = mapOf("time" to time, "date" to date)
            )
        }.getOrElse { e ->
            ToolResult.Error("Saat alınamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// calculate
// ---------------------------------------------------------------------------

/**
 * Evaluates a basic mathematical expression.
 *
 * Parameters:
 *   expression (string, required): A math expression — e.g. "12 * 4 + 8 / 2"
 *
 * Supported: +, -, *, /, parentheses, integer and decimal numbers.
 * NOT supported: trig functions, variables, exponents — Gemini should handle those natively.
 *
 * Security note: Uses a hand-rolled recursive descent parser — no eval() or script engine.
 * This avoids arbitrary code execution risk from user-supplied expressions.
 *
 * Example Gemini interaction:
 *   User: "245 çarpı 18 kaç eder?"
 *   Gemini: function_call { name: "calculate", args: { "expression": "245 * 18" } }
 *   Tool:   ToolResult.Success("245 × 18 = 4410")
 */
class CalculateTool @Inject constructor() : JarvisTool {

    override val name        = "calculate"
    override val description = "Evaluates a mathematical expression and returns the result. Use for arithmetic the user asks you to compute."
    override val parameters  = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "expression": {
              "type": "string",
              "description": "A mathematical expression to evaluate, e.g. '245 * 18' or '(100 + 50) / 3'"
            }
          },
          "required": ["expression"]
        }
        """.trimIndent()
    )
    override val requiredPermission: String? = null

    // UNTESTED — verify parser with edge cases before use
    override suspend fun execute(args: JSONObject): ToolResult {
        val expression = args.optString("expression").trim()

        if (expression.isBlank()) {
            return ToolResult.Error("İfade boş — hesaplanamadı.")
        }

        return runCatching {
            val result = MathParser(expression).parse()

            // Format: remove trailing .0 for whole numbers
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                "%.4f".format(result).trimEnd('0').trimEnd('.')
            }

            val display = "$expression = $formatted"

            ToolResult.Success(
                displayText = display,
                data        = mapOf("result" to result, "expression" to expression)
            )
        }.getOrElse { e ->
            ToolResult.Error("Hesaplama hatası: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// MathParser — recursive descent parser for basic arithmetic
// Supports: +, -, *, /, (), decimal numbers, unary minus
// ---------------------------------------------------------------------------

/**
 * Hand-rolled recursive descent parser for arithmetic expressions.
 * Grammar:
 *   expr    → term (('+' | '-') term)*
 *   term    → factor (('*' | '/') factor)*
 *   factor  → '-' factor | '(' expr ')' | number
 *   number  → [0-9]+ ('.' [0-9]+)?
 *
 * UNTESTED — verify with edge cases before use
 */
private class MathParser(private val input: String) {

    private var pos = 0

    fun parse(): Double {
        val result = parseExpr()
        skipWhitespace()
        if (pos < input.length) {
            throw IllegalArgumentException("Unexpected character '${input[pos]}' at position $pos")
        }
        return result
    }

    private fun parseExpr(): Double {
        var result = parseTerm()
        while (pos < input.length) {
            skipWhitespace()
            when {
                peek() == '+' -> { pos++; result += parseTerm() }
                peek() == '-' -> { pos++; result -= parseTerm() }
                else          -> break
            }
        }
        return result
    }

    private fun parseTerm(): Double {
        var result = parseFactor()
        while (pos < input.length) {
            skipWhitespace()
            when {
                peek() == '*' -> { pos++; result *= parseFactor() }
                peek() == '/' -> {
                    pos++
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("Sıfıra bölme hatası")
                    result /= divisor
                }
                else -> break
            }
        }
        return result
    }

    private fun parseFactor(): Double {
        skipWhitespace()
        if (pos >= input.length) throw IllegalArgumentException("Unexpected end of expression")

        // Unary minus
        if (peek() == '-') { pos++; return -parseFactor() }

        // Parenthesized expression
        if (peek() == '(') {
            pos++
            val result = parseExpr()
            skipWhitespace()
            if (pos >= input.length || peek() != ')') {
                throw IllegalArgumentException("Missing closing parenthesis")
            }
            pos++
            return result
        }

        // Number
        return parseNumber()
    }

    private fun parseNumber(): Double {
        skipWhitespace()
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (pos == start) throw IllegalArgumentException("Expected number at position $pos, got '${input.getOrNull(pos)}'")
        return input.substring(start, pos).toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number: '${input.substring(start, pos)}'")
    }

    private fun peek(): Char = input[pos]

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }
}