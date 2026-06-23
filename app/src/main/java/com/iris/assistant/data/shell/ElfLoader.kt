package com.iris.assistant.data.shell

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object ElfLoader {
    private const val TAG = "ElfLoader"

    init {
        System.loadLibrary("elfloader")
    }

    private external fun nativeExecute(
        elfPath: String,
        libPath: String,
        args: Array<String>,
        env: Array<String>,
    ): IntArray

    internal external fun nativeWaitForPid(pid: Int): Int

    internal external fun nativeKill(pid: Int, signal: Int)

    fun execute(
        elfPath: String,
        libPath: String,
        args: Array<String> = emptyArray(),
        env: Array<String> = emptyArray(),
    ): ElfProcess {
        val elfFile = File(elfPath)
        if (!elfFile.exists()) throw IllegalStateException("ELF not found: $elfPath")
        val libDir = File(libPath)
        if (!libDir.isDirectory) throw IllegalStateException("Lib dir not found: $libPath")

        val result = nativeExecute(elfPath, libPath, args, env)
        val pid      = result[0]
        val stdinFd  = result[1]
        val stdoutFd = result[2]
        val stderrFd = result[3]

        return ElfProcess(pid, stdinFd, stdoutFd, stderrFd)
    }
}

class ElfProcess internal constructor(
    val pid: Int,
    stdinFd: Int,
    stdoutFd: Int,
    stderrFd: Int,
) {
    companion object {
        private const val TAG = "ElfProcess"
    }

    private val stdinPfd: ParcelFileDescriptor  = ParcelFileDescriptor.adoptFd(stdinFd)
    private val stdoutPfd: ParcelFileDescriptor = ParcelFileDescriptor.adoptFd(stdoutFd)
    private val stderrPfd: ParcelFileDescriptor = ParcelFileDescriptor.adoptFd(stderrFd)

    val outputStream: OutputStream  = FileOutputStream(stdinPfd.fileDescriptor)
    val inputStream: InputStream    = FileInputStream(stdoutPfd.fileDescriptor)
    val errorStream: InputStream    = FileInputStream(stderrPfd.fileDescriptor)

    @Volatile private var exited = false
    @Volatile private var exitCode: Int = -1

    val isAlive: Boolean get() = !exited

    fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        if (exited) return true
        val start = System.nanoTime()
        val timeoutNs = unit.toNanos(timeout)
        while (System.nanoTime() - start < timeoutNs) {
            val result = ElfLoader.nativeWaitForPid(pid)
            if (result >= 0) {
                exitCode = result
                exited = true
                closeStreams()
                return true
            }
            Thread.sleep(10)
        }
        return false
    }

    fun waitFor(): Int {
        if (exited) return exitCode
        while (true) {
            val result = ElfLoader.nativeWaitForPid(pid)
            if (result >= 0) {
                exitCode = result
                exited = true
                closeStreams()
                return exitCode
            }
            Thread.sleep(10)
        }
    }

    fun destroy() {
        ElfLoader.nativeKill(pid, 15) // SIGTERM
        try { Thread.sleep(200) } catch (_: InterruptedException) {}
        if (!exited) {
            val result = ElfLoader.nativeWaitForPid(pid)
            if (result >= 0) {
                exitCode = result
                exited = true
            } else {
                ElfLoader.nativeKill(pid, 9) // SIGKILL
                val sigkillResult = ElfLoader.nativeWaitForPid(pid)
                if (sigkillResult >= 0) exitCode = sigkillResult
                exited = true
            }
        }
        closeStreams()
    }

    fun destroyForcibly() {
        ElfLoader.nativeKill(pid, 9)
        val result = ElfLoader.nativeWaitForPid(pid)
        if (result >= 0) exitCode = result
        exited = true
        closeStreams()
    }

    fun exitValue(): Int {
        if (!exited) throw IllegalThreadStateException("Process not exited")
        return exitCode
    }

    private fun closeStreams() {
        try { outputStream.close() } catch (_: Exception) {}
        try { inputStream.close() } catch (_: Exception) {}
        try { errorStream.close() } catch (_: Exception) {}
        try { stdinPfd.close() } catch (_: Exception) {}
        try { stdoutPfd.close() } catch (_: Exception) {}
        try { stderrPfd.close() } catch (_: Exception) {}
    }
}
