package com.iris.assistant.data.remote.local

data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val hfRepoId: String,
    val hfFilename: String,
    val sizeMb: Int,
    val description: String,
    val recommended: Boolean = false,
    val minRamGb: Int = 3,
    val contextSize: Int = 4096,
    val chatTemplate: String = "qwen"
)

object LocalModelManifest {

    val models: List<LocalModelInfo> = listOf(
        LocalModelInfo(
            id = "qwen-2.5-0.5b",
            displayName = "Qwen 2.5 0.5B",
            hfRepoId = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            hfFilename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeMb = 400,
            description = "En hızlı seçenek. Düşük RAM, temel sohbet için ideal.",
            recommended = false,
            minRamGb = 2,
            chatTemplate = "qwen"
        ),
        LocalModelInfo(
            id = "qwen-2.5-1.5b",
            displayName = "Qwen 2.5 1.5B",
            hfRepoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            hfFilename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeMb = 1000,
            description = "Önerilen. Türkçede iyi performans, hız ve kalite dengesi.",
            recommended = true,
            minRamGb = 3,
            chatTemplate = "qwen"
        ),
        LocalModelInfo(
            id = "llama-3.2-1b",
            displayName = "Llama 3.2 1B",
            hfRepoId = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            hfFilename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeMb = 600,
            description = "İyi genel performans, orta boy.",
            recommended = false,
            minRamGb = 3,
            contextSize = 8192,
            chatTemplate = "llama"
        ),
        LocalModelInfo(
            id = "llama-3.2-3b",
            displayName = "Llama 3.2 3B",
            hfRepoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            hfFilename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeMb = 2000,
            description = "En kaliteli çıktı, yüksek RAM gerektirir.",
            recommended = false,
            minRamGb = 4,
            contextSize = 8192,
            chatTemplate = "llama"
        ),
    )
}
