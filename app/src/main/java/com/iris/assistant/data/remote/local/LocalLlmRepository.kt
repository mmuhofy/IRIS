package com.iris.assistant.data.remote.local

import android.util.Log
import com.iris.assistant.data.local.datastore.PreferencesRepository
import com.iris.assistant.domain.model.ChatMessage
import com.iris.assistant.domain.repository.LlmRepository
import com.iris.assistant.domain.tools.ToolRegistry
import com.iris.assistant.domain.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmRepository @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val preferencesRepository: PreferencesRepository,
    private val modelDownloader: ModelDownloader
) : LlmRepository {

    companion object {
        private const val TAG = "LocalLlmRepository"
        private const val MAX_TOOL_ROUNDS = 5

        // Regex to detect tool call JSON in model output
        private val TOOL_CALL_PATTERN = Regex(
            """(\{[^}]*"tool"\s*:\s*"[^"]*"[^}]*\})""",
            RegexOption.DOT_MATCHES_ALL
        )
    }

    private var loadedModelPath: String? = null
    private var llamaModel: Any? = null // LlamaModel instance

    override suspend fun chat(
        history: List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val modelName = preferencesRepository.preferences
            .map { it.localModelName }
            .first()
            .takeIf { it.isNotBlank() }
            ?: return@withContext "Lütfen Ayarlar > Yerel Model'den bir model indirin ve seçin."

        val modelPath = preferencesRepository.preferences
            .map { it.localModelPath }
            .first()
            .takeIf { it.isNotBlank() }
            ?: return@withContext "Seçili model dosyası bulunamadı."

        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            return@withContext "Model dosyası bulunamadı: $modelPath"
        }

        // Find model info from manifest for chat template
        val modelInfo = LocalModelManifest.models.find { it.id == modelName }

        // Build tool descriptions for prompt injection
        val toolDescriptions = buildToolDescriptions()

        // Inject tool instructions into system prompt
        val fullSystemPrompt = buildLocalSystemPrompt(systemPrompt, toolDescriptions)

        // Build formatted prompt with chat template
        val formattedPrompt = formatConversation(
            systemPrompt = fullSystemPrompt,
            history = history,
            chatTemplate = modelInfo?.chatTemplate ?: "qwen"
        )

        // Load model if not loaded or path changed
        ensureModelLoaded(modelPath)

        // Multi-turn tool calling loop
        var currentPrompt = formattedPrompt
        for (round in 0 until MAX_TOOL_ROUNDS) {

            val rawOutput = generateText(currentPrompt)
            val output = rawOutput.trim()

            Log.d(TAG, "round=$round output=${output.take(200)}")

            // Check for tool call JSON
            val toolCall = parseToolCall(output)

            if (toolCall == null) {
                // No tool call — this is the final answer
                return@withContext cleanOutput(output)
            }

            Log.d(TAG, "round=$round: toolCall=${toolCall.first} args=${toolCall.second}")

            // Execute tool
            val toolResult = toolRegistry.execute(toolCall.first, toolCall.second)

            if (toolResult is ToolResult.PermissionRequired) {
                throw com.iris.assistant.domain.model.IrisException.PermissionRequiredException(
                    permission = toolResult.permission,
                    rationale = toolResult.rationale,
                    toolName = toolCall.first
                )
            }
            if (toolResult is ToolResult.Cancelled) {
                return@withContext "İşlem iptal edildi."
            }

            val resultText = when (toolResult) {
                is ToolResult.Success -> toolResult.displayText
                is ToolResult.Error -> "Error: ${toolResult.message}"
                else -> "Unknown result"
            }

            // Append result and continue
            currentPrompt = buildContinuationPrompt(currentPrompt, output, resultText)
        }

        throw com.iris.assistant.domain.model.IrisException.LlmException(
            "Local model exceeded max tool call rounds ($MAX_TOOL_ROUNDS)"
        )
    }

    private suspend fun ensureModelLoaded(modelPath: String) {
        if (loadedModelPath == modelPath && llamaModel != null) return

        closeModel()
        Log.d(TAG, "Loading model: $modelPath")

        // Try multiple known API shapes in order of likelihood
        val model = loadModelFirstAvailable(modelPath)
        if (model != null) {
            llamaModel = model
            loadedModelPath = modelPath
            Log.d(TAG, "Model loaded successfully")
        } else {
            loadedModelPath = null
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Model yüklenemedi. Kütüphane API'si uyumsuz olabilir."
            )
        }
    }

    private suspend fun loadModelFirstAvailable(modelPath: String): Any? {
        try { return loadModelDirect(modelPath) } catch (_: Exception) {}
        try { return loadModelReflective("org.codeshipping.llama.LlamaModel", modelPath) } catch (_: Exception) {}
        try { return loadModelReflective("com.suhel.llamabro.sdk.LlamaEngine", modelPath) } catch (_: Exception) {}
        try { return loadModelReflective("com.dark.gguf_lib.GGMLEngine", modelPath) } catch (_: Exception) {}
        return null
    }

    private suspend fun loadModelDirect(modelPath: String): Any? {
        // If the library exposes a known static factory, we use it here.
        // org.codeshipping.llama.LlamaModel.load(path) returns LlamaModel
        try {
            val llamaClass = Class.forName("org.codeshipping.llama.LlamaModel$Companion")
            val loadMethod = llamaClass.getMethod("load", String::class.java)
            return loadMethod.invoke(null, modelPath)
        } catch (_: NoClassDefFoundError) {}
        catch (_: ClassNotFoundException) {}

        try {
            val llamaClass = Class.forName("org.codeshipping.llama.LlamaModel")
            val loadMethod = llamaClass.getMethod("load", String::class.java)
            return loadMethod.invoke(null, modelPath)
        } catch (_: Exception) {}
        return null
    }

    private suspend fun loadModelReflective(className: String, modelPath: String): Any? {
        return try {
            val clazz = Class.forName(className)
            val methods = clazz.methods
            val loadMethod = methods.find { it.name == "load" && it.parameterCount == 1 }
            if (loadMethod != null) {
                loadMethod.invoke(null, modelPath)
            } else null
        } catch (_: Exception) { null }
    }

    private fun closeModel() {
        if (llamaModel != null) {
            try {
                val closeMethod = llamaModel!!.javaClass.methods
                    .find { it.name == "close" || it.name == "closeModel" || it.name == "release" }
                if (closeMethod != null) {
                    closeMethod.invoke(llamaModel)
                }
            } catch (_: Exception) {}
            llamaModel = null
            loadedModelPath = null
        }
    }

    private suspend fun generateText(prompt: String): String {
        if (llamaModel == null) return ""

        return try {
            val obj = llamaModel!!
            val methods = obj.javaClass.methods
            val genMethod = methods.find { it.name == "generate" && it.parameterTypes.any { it == String::class.java } }
            if (genMethod != null) {
                genMethod.invoke(obj, prompt)?.toString() ?: ""
            } else {
                Log.e(TAG, "No generate(String) method found on ${obj.javaClass.name}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generate error", e)
            ""
        }
    }

    private fun buildToolDescriptions(): String {
        val toolsJson = toolRegistry.openAiToolsPayload() ?: return "[]"
        val descriptions = JSONArray()

        for (i in 0 until toolsJson.length()) {
            val tool = toolsJson.getJSONObject(i)
            val fn = tool.optJSONObject("function") ?: continue
            descriptions.put(
                JSONObject()
                    .put("name", fn.optString("name", ""))
                    .put("description", fn.optString("description", ""))
                    .put("parameters", fn.optJSONObject("parameters") ?: JSONObject())
            )
        }

        return descriptions.toString(2)
    }

    private fun buildLocalSystemPrompt(
        baseSystemPrompt: String,
        toolDescriptions: String
    ): String {
        return """
$baseSystemPrompt

AVAILABLE FUNCTIONS:
You have access to the following functions. When the user asks you to do something that matches one of these functions, respond with a JSON object exactly in this format (and nothing else):

{"tool": "function_name", "args": {"key": "value"}}

Available functions:
$toolDescriptions

If no function is needed, respond normally. Never say you "cannot" do something when a function is available.

RULES:
- When calling a function, output ONLY the JSON object, no other text.
- After the function result is returned, explain the result to the user naturally.
- Always respond in Turkish.
        """.trimIndent()
    }

    private fun formatConversation(
        systemPrompt: String,
        history: List<ChatMessage>,
        chatTemplate: String
    ): String {
        val sb = StringBuilder()

        when (chatTemplate) {
            "qwen" -> {
                sb.appendLine("<|im_start|>system")
                sb.appendLine(systemPrompt)
                sb.appendLine("<|im_end|>")

                history.forEach { msg ->
                    val role = when (msg.role) {
                        ChatMessage.Role.USER -> "user"
                        ChatMessage.Role.ASSISTANT -> "assistant"
                    }
                    sb.appendLine("<|im_start|>$role")
                    sb.appendLine(msg.content)
                    sb.appendLine("<|im_end|>")
                }

                sb.append("<|im_start|>assistant")
            }
            "llama" -> {
                sb.appendLine("<|begin_of_text|><|start_header_id|>system<|end_header_id|>")
                sb.appendLine(systemPrompt)
                sb.appendLine("<|eot_id|>")

                history.forEach { msg ->
                    val role = when (msg.role) {
                        ChatMessage.Role.USER -> "user"
                        ChatMessage.Role.ASSISTANT -> "assistant"
                    }
                    sb.appendLine("<|start_header_id|>$role<|end_header_id|>")
                    sb.appendLine(msg.content)
                    sb.appendLine("<|eot_id|>")
                }

                sb.append("<|start_header_id|>assistant<|end_header_id|>")
            }
        }

        return sb.toString()
    }

    private fun buildContinuationPrompt(
        previousPrompt: String,
        modelOutput: String,
        toolResult: String
    ): String {
        // Append the model's tool call and the function result
        val sb = StringBuilder(previousPrompt)
        sb.appendLine()
        sb.appendLine(modelOutput)

        // Add function result as a system/user message
        // Qwen format:
        sb.appendLine("<|im_end|>")
        sb.appendLine("<|im_start|>user")
        sb.appendLine("Function result: $toolResult")
        sb.appendLine("Based on this result, respond to the user naturally.")
        sb.appendLine("<|im_end|>")
        sb.append("<|im_start|>assistant")

        return sb.toString()
    }

    private fun parseToolCall(output: String): Pair<String, JSONObject>? {
        val match = TOOL_CALL_PATTERN.find(output) ?: return null
        val jsonStr = match.value

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

    private fun cleanOutput(output: String): String {
        // Remove any tool call JSON remnants
        return output.replace(TOOL_CALL_PATTERN, "").trim()
    }
}
