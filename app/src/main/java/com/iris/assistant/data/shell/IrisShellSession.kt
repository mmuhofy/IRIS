package com.iris.assistant.data.shell

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileDescriptor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IrisShellSession @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val bootstrapInstaller: BootstrapInstaller,
) {
    companion object {
        private const val TAG = "IrisShellSession"
        private const val CMD_DONE_SENTINEL = "__IRIS_CMD_DONE_\$?__"

        init {
            System.loadLibrary("iris-bootstrap")
        }

        @JvmStatic
        private external fun nativeCreateSubprocess(
            shellPath: String,
            cwd: String,
            args: Array<String>?,
            envVars: Array<String>?,
            pid: IntArray,
        ): FileDescriptor
    }

    private val _output = MutableSharedFlow<ShellLine>(replay = 200, extraBufferCapacity = 500)
    val output: Flow<ShellLine> = _output.asSharedFlow()

    @Volatile private var fd: FileDescriptor? = null
    @Volatile private var processPid: Int = -1

    val isRunning: Boolean get() = fd != null && processPid > 0

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val prefixPath = bootstrapInstaller.prefixDir.absolutePath
        val homePath = bootstrapInstaller.homeDir.absolutePath
        val shellBin = File(prefixPath, "bin/bash")

        if (!shellBin.exists()) {
            throw IllegalStateException("Bootstrap not installed: ${shellBin.absolutePath}")
        }
        if (!shellBin.canExecute()) shellBin.setExecutable(true)

        val envVars = arrayOf(
            "HOME=$homePath",
            "PREFIX=$prefixPath",
            "PATH=$prefixPath/bin:$prefixPath/sbin:/system/bin:/system/xbin",
            "TMPDIR=$prefixPath/tmp",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "LD_LIBRARY_PATH=$prefixPath/lib",
            "TERMUX_PREFIX=$prefixPath",
        )

        val pid = IntArray(1)
        val result = nativeCreateSubprocess(
            shellBin.absolutePath,
            homePath,
            arrayOf("--login"),
            envVars,
            pid,
        )

        processPid = pid[0]
        fd = result

        Log.i(TAG, "Shell session started (pid=$processPid, fd=$result)")

        Thread({
            try {
                val buf = ByteArray(4096)
                val sb = StringBuilder()
                while (fd != null) {
                    val n = try {
                        Os.read(fd!!, buf, 0, buf.size)
                    } catch (_: ErrnoException) {
                        break
                    }
                    if (n <= 0) break
                    sb.append(buf.decodeToString(0, n))
                    val text = sb.toString()
                    val lines = text.split("\n")
                    if (text.endsWith("\n")) {
                        lines.dropLast(1).forEach { _output.tryEmit(ShellLine(it, ShellLine.Stream.STDOUT)) }
                        sb.clear()
                    } else {
                        lines.dropLast(1).forEach { _output.tryEmit(ShellLine(it, ShellLine.Stream.STDOUT)) }
                        sb.replace(0, sb.length, lines.last())
                    }
                }
                if (sb.isNotEmpty()) {
                    _output.tryEmit(ShellLine(sb.toString(), ShellLine.Stream.STDOUT))
                }
            } catch (e: Exception) {
                Log.d(TAG, "PTY reader ended: ${e.message}")
            }
            Log.d(TAG, "PTY reader thread finished")
        }, "iris-pty-reader").also { it.isDaemon = true; it.start() }
    }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        val f = fd ?: throw IllegalStateException("Shell not running")
        val data = "$command\n".encodeToByteArray()
        Os.write(f, data, 0, data.size)
        Log.d(TAG, "Sent: $command")
    }

    private suspend fun sendDoneSentinel() = withContext(Dispatchers.IO) {
        val f = fd ?: return@withContext
        val data = "echo \"$CMD_DONE_SENTINEL\"\n".encodeToByteArray()
        Os.write(f, data, 0, data.size)
    }

    suspend fun runAndCollect(
        command: String,
        timeoutMs: Long,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!isRunning) throw IllegalStateException("Shell not running")

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
        sendDoneSentinel()

        val exitCode = withTimeoutOrNull(timeoutMs) {
            doneChannel.receive()
        }

        collectJob.cancel()

        ShellCommandResult(
            command = command,
            lines = lines,
            exitCode = exitCode ?: -1,
            timedOut = exitCode == null,
        )
    }

    suspend fun interrupt() = withContext(Dispatchers.IO) {
        if (processPid > 0) {
            runCatching { Os.kill(processPid, OsConstants.SIGINT) }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (processPid > 0) {
            runCatching { Os.kill(processPid, OsConstants.SIGTERM) }
            // Allow 2s for graceful shutdown
            Thread.sleep(200)
            runCatching { Os.kill(processPid, OsConstants.SIGKILL) }
        }
        runCatching { fd?.let { Os.close(it) } }
        fd = null
        processPid = -1
        Log.d(TAG, "Shell session stopped")
    }
}
