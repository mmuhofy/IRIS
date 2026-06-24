package com.iris.assistant.data.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent bash session running inside the embedded Termux bootstrap environment.
 *
 * Lifecycle:
 *   start() → [running] = true → commands flow in via [send] → stop() cleans up
 *
 * The session is a single long-running Process. stdin is written per command;
 * stdout/stderr are streamed continuously and emitted as [ShellLine] events on
 * [output]. Callers collect [output] to display live terminal output.
 *
 * Thread safety: [send] and [stop] are coroutine-safe. The stdout/stderr reader
 * threads are internal and never exposed.
 *
 * UNTESTED — verify before use
 */
@Singleton
class EmbeddedShell @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bootstrapInstaller: BootstrapInstaller,
) {
    companion object {
        private const val TAG = "EmbeddedShell"

        // Sentinel written to stdout after each command so we know the command finished.
        // Must be a string that cannot appear in normal command output.
        private const val CMD_DONE_SENTINEL = "__IRIS_CMD_DONE_\$?__"

        // Shell binary inside the Termux prefix
        private const val SHELL_BIN = "usr/bin/bash"
    }

    // ── Output stream ─────────────────────────────────────────────────────────

    /**
     * Emits every line produced by the shell (stdout + stderr).
     * Replay = 200 so a reconnecting UI gets recent history.
     */
    private val _output = MutableSharedFlow<ShellLine>(replay = 200, extraBufferCapacity = 500)
    val output: Flow<ShellLine> = _output.asSharedFlow()

    // ── Process state ─────────────────────────────────────────────────────────

    @Volatile private var process: Process? = null
    @Volatile private var stdinWriter: BufferedWriter? = null

    val isRunning: Boolean get() = process?.isAlive == true

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the bash session.
     * No-op if already running.
     * Must be called from a coroutine (switches to [Dispatchers.IO] internally).
     *
     * @throws IllegalStateException if the bootstrap is not installed
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val termuxDir = File(context.filesDir, "termux")
        val shellBin  = File(termuxDir, SHELL_BIN)

        if (!shellBin.exists()) {
            throw IllegalStateException(
                "Termux bootstrap not installed. Shell binary not found: ${shellBin.absolutePath}"
            )
        }
        if (!shellBin.canExecute()) {
            shellBin.setExecutable(true)
        }

        val prefixPath = bootstrapInstaller.prefixDir.absolutePath
        val homePath   = bootstrapInstaller.homeDir.absolutePath

        val pb = ProcessBuilder(shellBin.absolutePath, "--login")
            .redirectErrorStream(false) // keep stderr separate so we can tag lines
            .apply {
                environment().apply {
                    put("HOME",          homePath)
                    put("PREFIX",        prefixPath)
                    put("PATH",          "$prefixPath/bin:$prefixPath/sbin:/system/bin:/system/xbin")
                    put("TMPDIR",        "$prefixPath/tmp")
                    put("TERM",          "xterm-256color")
                    put("LANG",          "en_US.UTF-8")
                    put("LD_LIBRARY_PATH", "$prefixPath/lib")
                    // Tell bash where it lives so scripts using $PREFIX work
                    put("TERMUX_PREFIX", prefixPath)
                }
                directory(File(homePath))
            }

        val proc = pb.start()
        process     = proc
        stdinWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))

        // Stdout reader thread
        Thread({
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        _output.tryEmit(ShellLine(line, ShellLine.Stream.STDOUT))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "stdout reader ended: ${e.message}")
            }
        }, "iris-shell-stdout").also { it.isDaemon = true; it.start() }

        // Stderr reader thread
        Thread({
            try {
                BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                    reader.forEachLine { line ->
                        _output.tryEmit(ShellLine(line, ShellLine.Stream.STDERR))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "stderr reader ended: ${e.message}")
            }
        }, "iris-shell-stderr").also { it.isDaemon = true; it.start() }

        Log.d(TAG, "Shell session started")
    }

    /**
     * Sends a command to the running bash session.
     * The command is followed by a sentinel echo so callers can detect completion.
     *
     * @throws IllegalStateException if the shell is not running
     */
    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        val writer = stdinWriter
            ?: throw IllegalStateException("Shell is not running. Call start() first.")
        // Write the command, then echo the sentinel with the exit code embedded
        writer.write("$command\n")
        writer.write("echo \"$CMD_DONE_SENTINEL\"\n")
        writer.flush()
        Log.d(TAG, "Sent: $command")
    }

    /**
     * Sends a command and collects output lines until the done sentinel appears,
     * up to [timeoutMs]. Returns collected lines (sentinel excluded).
     *
     * Intended for AI tool-fallback calls where the caller needs the full output
     * before returning a [com.iris.assistant.domain.tools.ToolResult].
     *
     * UNTESTED — verify before use
     */
    suspend fun runAndCollect(
        command: String,
        timeoutMs: Long,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!isRunning) throw IllegalStateException("Shell is not running.")

        val lines = mutableListOf<ShellLine>()
        val doneChannel = Channel<Int>(1) // carries exit code from sentinel

        // Collect from the shared flow until sentinel arrives
        val collectScope = CoroutineScope(Dispatchers.IO)
        val collectJob = collectScope.launch {
            output.collect { line ->
                if (line.text.startsWith("__IRIS_CMD_DONE_")) {
                    // Sentinel format: __IRIS_CMD_DONE_<exitCode>__
                    val exitCode = line.text
                        .removePrefix("__IRIS_CMD_DONE_")
                        .removeSuffix("__")
                        .toIntOrNull() ?: 0
                    doneChannel.trySend(exitCode)
                    return@collect
                }
                lines.add(line)
            }
        }

        send(command)

        val exitCode = withTimeoutOrNull(timeoutMs) {
            doneChannel.receive()
        }

        collectJob.cancel()

        ShellCommandResult(
            command  = command,
            lines    = lines,
            exitCode = exitCode ?: -1,
            timedOut = exitCode == null,
        )
    }

    /**
     * Sends SIGTERM to the shell process, waits up to 2 s, then SIGKILL.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val proc = process ?: return@withContext
        stdinWriter?.runCatching { close() }
        proc.destroy()
        val exited = proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        if (!exited) proc.destroyForcibly()
        process     = null
        stdinWriter = null
        Log.d(TAG, "Shell session stopped")
    }

    /**
     * Interrupts the currently running foreground command by sending Ctrl-C (ETX).
     * Does not stop the shell session itself.
     */
    suspend fun interrupt() = withContext(Dispatchers.IO) {
        stdinWriter?.apply {
            write("\u0003\n") // ETX = Ctrl-C
            flush()
        }
        Log.d(TAG, "Sent interrupt (Ctrl-C)")
    }
}

// ── Data types ────────────────────────────────────────────────────────────────

/** A single line of output from the shell. */
data class ShellLine(
    val text   : String,
    val stream : Stream,
) {
    enum class Stream { STDOUT, STDERR }
}

/** Result of a single [EmbeddedShell.runAndCollect] call. */
data class ShellCommandResult(
    val command  : String,
    val lines    : List<ShellLine>,
    val exitCode : Int,
    val timedOut : Boolean,
) {
    /** Concatenated output as a single string, suitable for LLM consumption. */
    fun outputText(): String = lines.joinToString("\n") { it.text }
    val succeeded: Boolean get() = exitCode == 0 && !timedOut
}