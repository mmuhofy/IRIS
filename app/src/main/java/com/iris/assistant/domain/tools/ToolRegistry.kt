package com.iris.assistant.domain.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for all JarvisTool implementations.
 *
 * Responsibilities:
 *   1. Holds all registered tools (injected via Hilt Set multibinding)
 *   2. Generates the Gemini function declarations array for API requests
 *   3. Dispatches Gemini function_call responses to the correct tool
 *   4. Returns ToolResult back to the caller (SendMessageUseCase)
 *
 * Hilt multibinding:
 *   Each JarvisTool implementation is bound into a Set<JarvisTool> via
 *   @IntoSet in its respective Hilt module (see di/ToolsModule.kt).
 *   ToolRegistry receives the full set via constructor injection.
 *
 * Usage in SendMessageUseCase:
 *   1. Call toolRegistry.geminiToolDeclarations() → include in Gemini request
 *   2. If Gemini returns function_call → call toolRegistry.execute(name, args)
 *   3. Send ToolResult.Success.data back to Gemini as function_result
 *   4. Get final text response from Gemini
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards JarvisTool>
) {

    companion object {
        private const val TAG = "ToolRegistry"
    }

    // Indexed by tool name for O(1) lookup on dispatch
    private val toolMap: Map<String, JarvisTool> by lazy {
        tools.associateBy { it.name }.also {
            Log.d(TAG, "Registered tools: ${it.keys}")
        }
    }

    /**
     * Returns the Gemini `tools` array containing all registered function declarations.
     *
     * Shape sent to Gemini API:
     * {
     *   "tools": [{
     *     "functionDeclarations": [
     *       { "name": "...", "description": "...", "parameters": { ... } },
     *       ...
     *     ]
     *   }]
     * }
     *
     * Called by GeminiRepository before each request.
     * Returns null if no tools are registered (avoids sending empty tools array).
     */
    fun geminiToolsPayload(): JSONArray? {
        if (toolMap.isEmpty()) return null

        val declarations = JSONArray()
        toolMap.values.forEach { tool ->
            declarations.put(
                JSONObject()
                    .put("name",        tool.name)
                    .put("description", tool.description)
                    .put("parameters",  tool.parameters)
            )
        }

        val functionDeclarationsWrapper = JSONObject()
            .put("functionDeclarations", declarations)

        return JSONArray().put(functionDeclarationsWrapper)
    }

    /**
     * Returns the OpenAI-compatible `tools` array for Groq (and other OpenAI-compatible APIs).
     *
     * Shape sent to Groq API:
     * [{
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": { ... }
     *   }
     * }]
     *
     * Called by GroqLlmRepository before each request.
     * Returns null if no tools are registered.
     */
    fun openAiToolsPayload(): JSONArray? {
        if (toolMap.isEmpty()) return null

        val tools = JSONArray()
        toolMap.values.forEach { tool ->
            tools.put(
                JSONObject()
                    .put("type", "function")
                    .put("function", JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", tool.parameters))
            )
        }
        return tools
    }

    /**
     * Dispatches a Gemini function_call to the matching tool.
     *
     * @param name Tool name from Gemini's function_call.name field
     * @param args Tool arguments from Gemini's function_call.args field (may be empty)
     * @return [ToolResult] from the tool, or [ToolResult.Error] if tool not found
     */
    suspend fun execute(name: String, args: JSONObject): ToolResult {
        val tool = toolMap[name] ?: run {
            Log.e(TAG, "execute: unknown tool '$name'")
            return ToolResult.Error("Unknown tool: $name")
        }

        Log.d(TAG, "execute: dispatching '$name' with args=$args")

        return runCatching {
            tool.execute(args)
        }.getOrElse { e ->
            Log.e(TAG, "execute: tool '$name' threw unexpectedly", e)
            ToolResult.Error("Tool '$name' failed: ${e.message}", e)
        }
    }

    /** Returns all registered tool names — useful for logging/debug. */
    fun registeredToolNames(): Set<String> = toolMap.keys
}