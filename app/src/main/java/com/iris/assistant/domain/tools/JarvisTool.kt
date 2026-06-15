package com.iris.assistant.domain.tools

import org.json.JSONObject

/**
 * Contract for all IRIS tools.
 *
 * Every tool is a self-contained unit that:
 *   1. Declares its name, description, and parameters for Gemini function declarations
 *   2. Declares the Android permission it requires (null = no permission needed)
 *   3. Executes given a JSON args object and returns a ToolResult
 *
 * Architecture note:
 *   - Interface lives in domain/ — no Android imports here (except JSONObject which
 *     is available in domain since it's in the JVM stdlib on Android).
 *   - Implementations live in data/tools/ or service/tools/ depending on
 *     what API they need (network, accessibility, system APIs).
 *   - All implementations are registered in ToolRegistry via Hilt multibinding.
 *
 * Gemini function calling:
 *   [toFunctionDeclaration()] serializes this tool's metadata into the JSON shape
 *   Gemini expects in the `tools[].functionDeclarations[]` array.
 *
 * Tool authoring checklist:
 *   - name: snake_case, matches Gemini function_call name exactly
 *   - description: clear, specific — Gemini uses this to decide when to call the tool
 *   - parameters: valid JSON Schema object (type: "object", properties: {...}, required: [...])
 *   - requiredPermission: exact Android Manifest.permission string, or null
 *   - execute(): never throws — always returns ToolResult
 */
interface JarvisTool {

    /** Unique tool name — snake_case. Must match Gemini function_call name exactly. */
    val name: String

    /** Human-readable description used in Gemini function declarations. */
    val description: String

    /**
     * JSON Schema object describing the tool's parameters.
     * Shape: { "type": "object", "properties": { ... }, "required": [...] }
     * Use JSONObject.NULL for optional fields.
     * Return empty object `{}` if the tool takes no parameters.
     */
    val parameters: JSONObject

    /**
     * Android permission required to execute this tool.
     * Null means no permission is needed.
     * Example: android.Manifest.permission.CALL_PHONE
     *
     * Note: domain/ avoids android.Manifest import — use the raw string constant.
     * The string is only resolved at runtime by the permission-checking layer.
     */
    val requiredPermission: String?

    /**
     * Executes the tool with the given arguments.
     *
     * @param args JSON object from Gemini's function_call.args field.
     *             May be empty if the tool takes no parameters.
     * @return [ToolResult] — never throws. Wrap all errors in [ToolResult.Error].
     */
    suspend fun execute(args: JSONObject): ToolResult
}