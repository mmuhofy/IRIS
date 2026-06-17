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
import org.codeshipping.llamakotlin.LlamaModel
import org.codeshipping.llamakotlin.LlamaNative
import org.codeshipping.llamakotlin.exception.LlamaException
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

        private val TOOL_CALL_PATTERN = Regex(
            """(\{[^}]*"tool"\s*:\s*"[^"]*"[^}]*\})""",
            RegexOption.DOT_MATCHES_ALL
        )
    }

    private var loadedModelPath: String? = null
    private var llamaModel: LlamaModel? = null

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

        val modelInfo = LocalModelManifest.models.find { it.id == modelName }

        val toolDescriptions = buildToolDescriptions()
        val fullSystemPrompt = buildLocalSystemPrompt(systemPrompt, toolDescriptions)

        val formattedPrompt = formatConversation(
            systemPrompt = fullSystemPrompt,
            history = history,
            chatTemplate = modelInfo?.chatTemplate ?: "qwen"
        )

        ensureModelLoaded(modelPath)

        var currentPrompt = formattedPrompt
        for (round in 0 until MAX_TOOL_ROUNDS) {
            val rawOutput = generateText(currentPrompt)
            val output = rawOutput.trim()

            Log.d(TAG, "round=$round output=${output.take(200)}")

            val toolCall = parseToolCall(output)

            if (toolCall == null) {
                return@withContext cleanOutput(output)
            }

            Log.d(TAG, "round=$round: toolCall=${toolCall.first} args=${toolCall.second}")

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

        preloadNativeLibrary()

        try {
            llamaModel = LlamaModel.load(modelPath) {
                contextSize = 2048
                threads = 4
                temperature = 0.7f
                topP = 0.9f
                topK = 40
                repeatPenalty = 1.1f
                maxTokens = 512
                useMmap = true
                useMlock = false
                gpuLayers = 0
            }
            loadedModelPath = modelPath
            Log.d(TAG, "Model loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not found", e)
            loadedModelPath = null
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Yerel kütüphane (libllama-android.so) yüklenemedi. APK'da arm64-v8a .so dosyası eksik olabilir."
            )
        } catch (e: LlamaException.ModelLoadError) {
            Log.e(TAG, "Model load error", e)
            loadedModelPath = null
            val nativeError = getNativeError()
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Model yüklenemedi: ${e.message ?: "GGUF model uyumsuz olabilir"}$nativeError"
            )
        } catch (e: LlamaException.NativeError) {
            Log.e(TAG, "Native library error", e)
            loadedModelPath = null
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Yerel kütüphane hatası: ${e.message ?: "llama.cpp sürüm uyumsuzluğu"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            loadedModelPath = null
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Model yüklenemedi: ${e.message ?: "Bilinmeyen hata"}"
            )
        }
    }

    private fun preloadNativeLibrary() {
        try {
            if (!LlamaNative.isLoaded) {
                Log.d(TAG, "Preloading native library...")
                LlamaNative.ensureLoaded()
                Log.d(TAG, "Native library version: ${LlamaNative.nativeGetVersion()}")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to preload native library", e)
            throw e
        }
    }

    private fun getNativeError(): String {
        return try {
            val err = LlamaNative.nativeGetLastError()
            if (err.isNullOrBlank()) "" else " | Native: $err"
        } catch (_: Exception) {
            ""
        }
    }

    private fun closeModel() {
        try {
            llamaModel?.close()
        } catch (_: Exception) {}
        llamaModel = null
        loadedModelPath = null
    }

    private suspend fun generateText(prompt: String): String {
        val model = llamaModel ?: return ""
        return try {
            model.generate(prompt)
        } catch (e: LlamaException.GenerationError) {
            Log.e(TAG, "Generation error", e)
            val nativeError = getNativeError()
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Metin oluşturma hatası: ${e.message ?: "Bilinmeyen"}$nativeError"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Generate error", e)
            throw com.iris.assistant.domain.model.IrisException.LlmException(
                "Metin oluşturma hatası: ${e.message}"
            )
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
        val sb = StringBuilder(previousPrompt)
        sb.appendLine()
        sb.appendLine(modelOutput)
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
        return output.replace(TOOL_CALL_PATTERN, "").trim()
    }
}
