package com.iris.assistant.data.shell

data class ShellLine(
    val text: String,
    val stream: Stream,
) {
    enum class Stream { STDOUT, STDERR }
}

data class ShellCommandResult(
    val command: String,
    val lines: List<ShellLine>,
    val exitCode: Int,
    val timedOut: Boolean,
) {
    fun outputText(): String = lines.joinToString("\n") { it.text }
    val succeeded: Boolean get() = exitCode == 0 && !timedOut
}
