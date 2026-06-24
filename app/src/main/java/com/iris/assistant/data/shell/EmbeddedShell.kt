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
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent shell session backed by Android's system shell (/system/bin/sh).
 *
 * IRIS bootstrap binaries (in $PREFIX/bin) cannot be executed directly
 * because Android SELinux blocks `execute` on app_data_file. Instead we
 * proxy them through `/system/bin/linker64` (or `/system/bin/linker` on
 * 32-bit), which IS executable by appdomain and can read the ELF as a data
 * file. A thin `t` shell function is injected at startup so callers can
 * write `t ls -la` to run any bootstrap binary with proper library resolution.
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
    }

    private val _output = MutableSharedFlow<ShellLine>(replay = 200, extraBufferCapacity = 500)
    val output: Flow<ShellLine> = _output.asSharedFlow()

    @Volatile private var process: Process? = null
    @Volatile private var stdinWriter: BufferedWriter? = null

    val isRunning: Boolean get() = process?.isAlive == true

    private val linker: String
        get() = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val prefixPath = bootstrapDownloader.prefixDir.absolutePath
        val homePath   = bootstrapDownloader.homeDir.absolutePath

        val proc = try {
            ProcessBuilder("/system/bin/sh")
                .redirectErrorStream(false)
                .apply {
                    environment().apply {
                        put("HOME",             homePath)
                        put("PREFIX",           prefixPath)
                        put("PATH",             "$prefixPath/bin:$prefixPath/sbin:/system/bin:/system/xbin")
                        put("TMPDIR",           "$prefixPath/tmp")
                        put("TERM",             "xterm-256color")
                        put("LANG",             "en_US.UTF-8")
                        put("LD_LIBRARY_PATH",  "$prefixPath/lib")
                        put("TERMUX_PREFIX",    prefixPath)
                        put("LINKER",           linker)
                    }
                    directory(File(homePath))
                }
                .start()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start system shell: ${e.message}")
            throw e
        }

        process     = proc
        stdinWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))

        // Inject the Termux command wrapper — `t ls -la` runs a Termux binary
        // via linker64 so shared library resolution happens correctly.
        // Note: regular string (not raw) so `\$` produces a literal `$` in output.
        val writer = stdinWriter!!
        writer.write(
            "t() { \"$linker\" \"$prefixPath/bin/\\\$1\" \"\\\${@:2}\"; }\n"
        )
        writer.write("__irix_ready__\n")
        writer.flush()

        Thread({
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        _output.tryEmit(ShellLine(line, ShellLine.Stream.STDOUT))
                    }
                }
            } catch (_: Exception) { }
        }, "iris-shell-stdout").also { it.isDaemon = true; it.start() }

        Thread({
            try {
                BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                    reader.forEachLine { line ->
                        _output.tryEmit(ShellLine(line, ShellLine.Stream.STDERR))
                    }
                }
            } catch (_: Exception) { }
        }, "iris-shell-stderr").also { it.isDaemon = true; it.start() }

        Log.d(TAG, "Shell session started (system sh, linker=$linker)")
    }

    /**
     * Runs a bootstrap binary directly via linker64 and returns the
     * output. Use this from Kotlin code when you need a tool without
     * going through the persistent shell session.
     */
    suspend fun runTermux(
        binary: String,
        vararg args: String,
        timeoutMs: Long = 30_000,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        val prefixPath = bootstrapDownloader.prefixDir.absolutePath
        val binPath    = "$prefixPath/bin/$binary"

        if (!File(binPath).exists()) {
            return@withContext ShellCommandResult(
                command  = "t $binary ${args.joinToString(" ")}",
                lines    = listOf(ShellLine("Binary not found: $binPath", ShellLine.Stream.STDERR)),
                exitCode = 127,
                timedOut = false,
            )
        }

        val lines = mutableListOf<ShellLine>()
        try {
            val proc = ProcessBuilder(linker, binPath, *args)
                .redirectErrorStream(true)
                .apply {
                    environment().apply {
                        put("HOME",             bootstrapDownloader.homeDir.absolutePath)
                        put("PREFIX",           prefixPath)
                        put("LD_LIBRARY_PATH",  "$prefixPath/lib")
                        put("PATH",             "$prefixPath/bin:$prefixPath/sbin:/system/bin:/system/xbin")
                        put("TMPDIR",           "$prefixPath/tmp")
                        put("TERM",             "dumb")
                        put("LANG",             "en_US.UTF-8")
                        put("TERMUX_PREFIX",    prefixPath)
                    }
                }
                .start()

            val reader = Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { r ->
                        r.forEachLine { line ->
                            lines.add(ShellLine(line, ShellLine.Stream.STDOUT))
                        }
                    }
                } catch (_: Exception) { }
            }, "iris-termux-reader").also { it.isDaemon = true; it.start() }

            val exited = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!exited) {
                proc.destroyForcibly()
                reader.join(1000)
                return@withContext ShellCommandResult(
                    command  = "t $binary ${args.joinToString(" ")}",
                    lines    = lines,
                    exitCode = -1,
                    timedOut = true,
                )
            }
            reader.join(1000)

            ShellCommandResult(
                command  = "t $binary ${args.joinToString(" ")}",
                lines    = lines,
                exitCode = proc.exitValue(),
                timedOut = false,
            )
        } catch (e: Exception) {
            Log.e(TAG, "runTermux failed: ${e.message}")
            ShellCommandResult(
                command  = "t $binary ${args.joinToString(" ")}",
                lines    = lines + ShellLine("Error: ${e.message}", ShellLine.Stream.STDERR),
                exitCode = -1,
                timedOut = false,
            )
        }
    }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        val writer = stdinWriter
            ?: throw IllegalStateException("Shell is not running. Call start() first.")
        writer.write("$command\n")
        writer.write("echo \"$CMD_DONE_SENTINEL\"\n")
        writer.flush()
        Log.d(TAG, "Sent: $command")
    }

    /** Send a command to the persistent shell via stdin. */
    suspend fun sendRaw(cmd: String) = withContext(Dispatchers.IO) {
        val writer = stdinWriter
            ?: throw IllegalStateException("Shell is not running.")
        writer.write(cmd)
        writer.flush()
    }

    suspend fun runAndCollect(
        command: String,
        timeoutMs: Long,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!isRunning) throw IllegalStateException("Shell is not running.")

        val lines = mutableListOf<ShellLine>()
        val doneChannel = Channel<Int>(1)

        val collectJob = CoroutineScope(Dispatchers.IO).launch {
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
