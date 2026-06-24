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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent bash session running inside the embedded Termux bootstrap environment.
 *
 * Launches bash via /system/bin/linker64 (or /system/bin/linker on 32-bit) to
 * bypass Android SELinux restrictions that prevent executing binaries stored in
 * the app's private data directory. The DT_RUNPATH of the bash ELF has been
 * patched to "$ORIGIN/../lib" during bootstrap install so that the system
 * linker can locate shared libraries (libreadline, libncurses, etc.).
 *
 * Lifecycle:
 *   start() → [running] = true → commands flow in via [send] → stop() cleans up
 */
@Singleton
class EmbeddedShell @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bootstrapDownloader: BootstrapDownloader,
) {
    companion object {
        private const val TAG = "EmbeddedShell"

        private const val CMD_DONE_SENTINEL = "__IRIS_CMD_DONE_\$?__"

        private const val SHELL_BIN = "usr/bin/bash"
    }

    private val _output = MutableSharedFlow<ShellLine>(replay = 200, extraBufferCapacity = 500)
    val output: Flow<ShellLine> = _output.asSharedFlow()

    @Volatile private var process: Process? = null
    @Volatile private var stdinWriter: BufferedWriter? = null

    val isRunning: Boolean get() = process?.isAlive == true

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val termuxDir  = File(context.filesDir, "termux")
        val shellBin   = File(termuxDir, SHELL_BIN)
        val prefixPath = bootstrapDownloader.prefixDir.absolutePath
        val homePath   = bootstrapDownloader.homeDir.absolutePath

        if (!shellBin.exists()) {
            throw IllegalStateException(
                "Termux bootstrap not installed: ${shellBin.absolutePath}"
            )
        }

        val linker = if (File("/system/bin/linker64").exists()) {
            "/system/bin/linker64"
        } else {
            "/system/bin/linker"
        }

        val proc = try {
            ProcessBuilder(linker, shellBin.absolutePath, "--login")
                .redirectErrorStream(false)
                .apply {
                    environment().apply {
                        put("HOME",          homePath)
                        put("PREFIX",        prefixPath)
                        put("PATH",          "$prefixPath/bin:$prefixPath/sbin:/system/bin:/system/xbin")
                        put("TMPDIR",        "$prefixPath/tmp")
                        put("TERM",          "xterm-256color")
                        put("LANG",          "en_US.UTF-8")
                        put("TERMUX_PREFIX", prefixPath)
                    }
                    directory(File(homePath))
                }
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start shell: ${e.message}")
            throw e
        }

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

        Log.d(TAG, "Shell session started (pid=${proc.pid()})")
    }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        val writer = stdinWriter
            ?: throw IllegalStateException("Shell is not running. Call start() first.")
        writer.write("$command\n")
        writer.write("echo \"$CMD_DONE_SENTINEL\"\n")
        writer.flush()
        Log.d(TAG, "Sent: $command")
    }

    suspend fun runAndCollect(
        command: String,
        timeoutMs: Long,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!isRunning) throw IllegalStateException("Shell is not running.")

        val lines = mutableListOf<ShellLine>()
        val doneChannel = Channel<Int>(1)

        val collectScope = CoroutineScope(Dispatchers.IO)
        val collectJob = collectScope.launch {
            output.collect { line ->
                if (line.text.startsWith("__IRIS_CMD_DONE_")) {
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

    suspend fun stop() = withContext(Dispatchers.IO) {
        val proc = process ?: return@withContext
        stdinWriter?.runCatching { close() }
        proc.destroy()
        val exited = proc.waitFor(2, TimeUnit.SECONDS)
        if (!exited) proc.destroyForcibly()
        process     = null
        stdinWriter = null
        Log.d(TAG, "Shell session stopped")
    }

    suspend fun interrupt() = withContext(Dispatchers.IO) {
        stdinWriter?.apply {
            write("\u0003\n")
            flush()
        }
        Log.d(TAG, "Sent interrupt (Ctrl-C)")
    }
}

data class ShellLine(
    val text   : String,
    val stream : Stream,
) {
    enum class Stream { STDOUT, STDERR }
}

data class ShellCommandResult(
    val command  : String,
    val lines    : List<ShellLine>,
    val exitCode : Int,
    val timedOut : Boolean,
) {
    fun outputText(): String = lines.joinToString("\n") { it.text }
    val succeeded: Boolean get() = exitCode == 0 && !timedOut
}
