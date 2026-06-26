package com.iris.assistant.data.shell

import android.content.Context
import android.system.Os
import android.util.Log
import com.iris.assistant.domain.model.BootstrapState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the IRIS bootstrap into the app's private files directory.
 *
 * The bootstrap zip is embedded in libiris-bootstrap.so at compile time
 * via NDK (iris-bootstrap-zip.S + iris-bootstrap.c) and extracted at
 * runtime through JNI. This avoids the need to bundle a large zip in assets.
 *
 * The zip contains a fully extracted filesystem under the prefix:
 *   data/data/com.iris.assistant/files/
 * which is stripped during extraction, landing everything under [context.filesDir].
 *
 * Layout after installation:
 *   filesDir/
 *     usr/     ← PREFIX
 *     home/    ← HOME
 */
@Singleton
class BootstrapInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG               = "BootstrapInstaller"
        private const val STRIP_PREFIX      = "data/data/com.iris.assistant/files/"
        private const val VERSION_FILE      = "bootstrap/INSTALLED_VERSION"
        private const val BOOTSTRAP_VERSION = "iris-1"
        private const val BUFFER_SIZE       = 8096

        init {
            System.loadLibrary("iris-bootstrap")
        }

        @JvmStatic
        private external fun nativeGetBootstrapZip(): ByteArray
    }

    val prefixDir: File
        get() = File(context.filesDir, "usr")

    val homeDir: File
        get() = File(context.filesDir, "home").also { it.mkdirs() }

    private val stagingDir: File
        get() = File(context.filesDir, "usr-staging")

    private val versionFile: File
        get() = File(context.filesDir, VERSION_FILE)

    fun isInstalled(): Boolean =
        versionFile.exists() &&
        prefixDir.exists() &&
        prefixDir.list()?.isNotEmpty() == true

    fun installedVersion(): String? =
        if (versionFile.exists()) versionFile.readText().trim() else null

    fun install(): Flow<BootstrapState> = flow {
        if (isInstalled()) {
            emit(BootstrapState.Installed(prefixDir.absolutePath, installedVersion()!!))
            return@flow
        }

        emit(BootstrapState.Extracting)

        val buffer = ByteArray(BUFFER_SIZE)

        try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            prefixDir.deleteRecursively()

            val zipBytes = resolveNestedZip(nativeGetBootstrapZip())
            ZipInputStream(zipBytes.inputStream().buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break

                    val name = entry.name
                    if (!name.startsWith(STRIP_PREFIX)) {
                        zis.closeEntry()
                        continue
                    }

                    val relative = name.removePrefix(STRIP_PREFIX)
                    if (relative.isEmpty()) {
                        zis.closeEntry()
                        continue
                    }

                    val destFile: File = if (relative.startsWith("usr/")) {
                        File(stagingDir, relative.removePrefix("usr/"))
                    } else {
                        File(context.filesDir, relative)
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            while (true) {
                                val n = zis.read(buffer)
                                if (n == -1) break
                                out.write(buffer, 0, n)
                            }
                        }
                        if (relative.startsWith("usr/bin/") ||
                            relative.startsWith("usr/sbin/") ||
                            relative.startsWith("usr/libexec/") ||
                            relative.startsWith("usr/lib/apt/")
                        ) {
                            destFile.setExecutable(true, false)
                        }
                    }
                    zis.closeEntry()
                }
            }

            if (!stagingDir.renameTo(prefixDir)) {
                throw RuntimeException(
                    "Failed to rename ${stagingDir.absolutePath} → ${prefixDir.absolutePath}"
                )
            }

            homeDir.mkdirs()

            // ── Create short symlink to lib directory ─────────────────────
            // The bootstrap binaries have DT_RUNPATH = /data/data/com.termux/files/usr/lib
            // (34 bytes). We replace it with a shorter path that fits in the
            // same ELF string table slot, then symlink that short path to the
            // real lib directory.
            val shortLibDir = File("/data/data/${context.packageName}/u/lib")
            shortLibDir.parentFile?.mkdirs()
            try { Os.remove(shortLibDir.absolutePath) } catch (_: android.system.ErrnoException) { }
            try {
                Os.symlink(File(prefixDir, "lib").absolutePath, shortLibDir.absolutePath)
                Log.d(TAG, "Short lib symlink: ${shortLibDir.absolutePath} -> ${File(prefixDir, "lib").absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Short lib symlink failed: ${e.message}")
            }

            // ── Patch DT_RUNPATH in key binaries ──────────────────────────
            patchElfRunpath(File(prefixDir, "bin/bash"), shortLibDir.absolutePath)
            patchElfRunpath(File(prefixDir, "bin/apt-get"), shortLibDir.absolutePath)

            // ── Verify DT_RUNPATH ─────────────────────────────────────────
            File(prefixDir, "bin/bash").let { f ->
                if (f.exists()) {
                    val actual = readElfRunpath(f)
                    if (actual == shortLibDir.absolutePath) {
                        Log.i(TAG, "✓ DT_RUNPATH: $actual")
                    } else if (actual != null) {
                        Log.e(TAG, "✗ DT_RUNPATH mismatch: expected '${shortLibDir.absolutePath}', got '$actual'")
                    }
                }
            }

            // ── Add ANDROID_LD_LIBRARY_PATH note ──────────────────────────
            addAndroidNote(File(prefixDir, "bin/bash"))
            addAndroidNote(File(prefixDir, "bin/apt-get"))

            // ── Patch hardcoded Termux paths in ELF binaries ──────────────
            // Bash and other binaries were compiled with
            // --prefix=/data/data/com.termux/files/usr, so strings like
            // SYSCONFDIR are hardcoded to /data/data/com.termux/files/usr/etc.
            // We replace /data/data/com.termux/files/usr with a shorter symlink
            // path /data/data/<pkg>/p, then create that symlink.
            patchTermuxDataPaths(File(prefixDir, "bin/bash"))
            patchTermuxDataPaths(File(prefixDir, "bin/apt-get"))

            val shortPrefixLink = File("/data/data/${context.packageName}/p")
            shortPrefixLink.parentFile?.mkdirs()
            try { Os.remove(shortPrefixLink.absolutePath) } catch (_: android.system.ErrnoException) { }
            try {
                Os.symlink(prefixDir.absolutePath, shortPrefixLink.absolutePath)
                Log.i(TAG, "Short prefix symlink: ${shortPrefixLink.absolutePath} -> ${prefixDir.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Short prefix symlink failed: ${e.message}")
            }

            // ── Verify libraries exist ────────────────────────────────────
            val libDir = File(prefixDir, "lib")
            listOf("libreadline.so.8", "libncursesw.so.6", "libandroid-support.so").forEach { name ->
                val libFile = File(libDir, name)
                if (libFile.exists()) {
                    Log.i(TAG, "✓ '$name' (${libFile.length()} bytes)")
                } else {
                    Log.w(TAG, "✗ '$name' NOT FOUND")
                }
            }

            versionFile.parentFile?.mkdirs()
            versionFile.writeText(BOOTSTRAP_VERSION)

            Log.i(TAG, "Bootstrap installed at ${prefixDir.absolutePath}")

        } catch (ex: Exception) {
            stagingDir.deleteRecursively()
            Log.e(TAG, "Bootstrap install failed", ex)
            emit(
                BootstrapState.Error(
                    message   = "Bootstrap install failed: ${ex.message}",
                    cause     = ex,
                    retryable = true,
                )
            )
            return@flow
        }

        emit(BootstrapState.Installed(prefixDir.absolutePath, BOOTSTRAP_VERSION))

    }.flowOn(Dispatchers.IO)

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        prefixDir.deleteRecursively()
        stagingDir.deleteRecursively()
        homeDir.deleteRecursively()
        versionFile.delete()
        Log.i(TAG, "Bootstrap uninstalled")
    }

    /**
     * If [zipBytes] is a double-zipped archive (a zip containing a single zip),
     * extract and return the inner zip bytes. Otherwise return as-is.
     */
    private fun resolveNestedZip(zipBytes: ByteArray): ByteArray {
        val firstEntry: ZipEntry
        val firstEntryBytes: ByteArray
        try {
            val stream = zipBytes.inputStream().buffered()
            ZipInputStream(stream).use { zis ->
                firstEntry = zis.nextEntry ?: return zipBytes
                val buf = ByteArray(BUFFER_SIZE)
                val inner = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = zis.read(buf)
                    if (n == -1) break
                    inner.write(buf, 0, n)
                }
                firstEntryBytes = inner.toByteArray()
                if (zis.nextEntry != null) return zipBytes
            }
        } catch (_: Exception) {
            return zipBytes
        }
        if (firstEntryBytes.size < 4) return zipBytes
        if (firstEntryBytes[0] != 0x50.toByte() || firstEntryBytes[1] != 0x4B.toByte()) return zipBytes
        Log.i(TAG, "Detected nested zip, unwrapping: '${firstEntry.name}' (${firstEntryBytes.size} bytes)")
        return firstEntryBytes
    }

    // ── ELF patching helpers ────────────────────────────────────────────────

    private fun patchElfRunpath(elfFile: File, libAbsPath: String) {
        if (!elfFile.isFile) return
        val data = elfFile.readBytes()
        if (data.size < 64) return
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return
        if (data[4] != 2.toByte()) return

        val phoff = ByteBuffer.wrap(data, 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val phentsize = ByteBuffer.wrap(data, 54, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val phnum = ByteBuffer.wrap(data, 56, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        data class Seg(val pOffset: Long, val pVaddr: Long, val pMemsz: Long)
        val loads = mutableListOf<Seg>()
        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0L || phOff.toInt() + 56 > data.size) return
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pOffset = ByteBuffer.wrap(data, phOff.toInt() + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pVaddr = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pMemsz = ByteBuffer.wrap(data, phOff.toInt() + 40, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (pType == 1) loads.add(Seg(pOffset, pVaddr, pMemsz))
        }
        if (loads.isEmpty()) return

        var strtab = 0L
        var strsz = 0L
        var runpathStrOffset = -1L

        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0L || phOff.toInt() + 56 > data.size) return
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (pType != 2) continue
            val pVaddr = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pFilesz = ByteBuffer.wrap(data, phOff.toInt() + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val load = loads.firstOrNull { pVaddr in it.pVaddr until (it.pVaddr + it.pMemsz) } ?: return
            val dynOff = load.pOffset + (pVaddr - load.pVaddr)
            if (dynOff.toInt() + 8 > data.size || dynOff + pFilesz > data.size.toLong()) return
            var off = dynOff
            while (off < dynOff + pFilesz) {
                val idx = off.toInt()
                val dTag = ByteBuffer.wrap(data, idx, 8).order(ByteOrder.LITTLE_ENDIAN).long
                val dVal = ByteBuffer.wrap(data, idx + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
                when (dTag) {
                    5L  -> strtab = dVal
                    10L -> strsz = dVal
                    15L -> runpathStrOffset = dVal
                    29L -> runpathStrOffset = dVal
                }
                if (dTag == 0L) break
                off += 24
            }
            break
        }

        if (runpathStrOffset < 0L || strtab == 0L) return

        val strLoad = loads.firstOrNull { strtab in it.pVaddr until (it.pVaddr + it.pMemsz) } ?: return
        val strOff = strLoad.pOffset + (strtab - strLoad.pVaddr) + runpathStrOffset
        if (strOff < 0L || strOff.toInt() >= data.size) return

        var endPos = strOff.toInt()
        while (endPos < data.size && data[endPos] != 0.toByte()) endPos++
        val oldLen = endPos - strOff.toInt()

        if (libAbsPath.length > oldLen) {
            Log.w(TAG, "Cannot patch ${elfFile.name}: new (${libAbsPath.length}) > old ($oldLen)")
            return
        }

        val oldPath = data.copyOfRange(strOff.toInt(), endPos).decodeToString()
        data.fill(0, strOff.toInt(), endPos)
        libAbsPath.encodeToByteArray().copyInto(data, strOff.toInt())
        data[endPos.coerceAtMost(strOff.toInt() + libAbsPath.length)] = 0

        elfFile.writeBytes(data)
        Log.i(TAG, "Patched DT_RUNPATH in ${elfFile.name}: '$oldPath' -> '$libAbsPath'")
    }

    private fun readElfRunpath(elfFile: File): String? {
        if (!elfFile.isFile) return null
        val data = elfFile.readBytes()
        if (data.size < 64) return null
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return null
        if (data[4] != 2.toByte()) return null

        val phoff = ByteBuffer.wrap(data, 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val phentsize = ByteBuffer.wrap(data, 54, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val phnum = ByteBuffer.wrap(data, 56, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        data class Seg(val pOffset: Long, val pVaddr: Long, val pMemsz: Long)
        val loads = mutableListOf<Seg>()
        var strtab = 0L
        var runpathStrOffset = -1L

        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0L || phOff.toInt() + 56 > data.size) return null
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pOffset = ByteBuffer.wrap(data, phOff.toInt() + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pVaddr = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pMemsz = ByteBuffer.wrap(data, phOff.toInt() + 40, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (pType == 1) loads.add(Seg(pOffset, pVaddr, pMemsz))
            if (pType != 2) continue
            val pVaddrDyn = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pFilesz = ByteBuffer.wrap(data, phOff.toInt() + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val load = loads.firstOrNull { pVaddrDyn in it.pVaddr until (it.pVaddr + it.pMemsz) } ?: return null
            val dynOff = load.pOffset + (pVaddrDyn - load.pVaddr)
            if (dynOff < 0L || dynOff + pFilesz > data.size.toLong()) return null
            var off = dynOff
            while (off < dynOff + pFilesz) {
                val idx = off.toInt()
                val dTag = ByteBuffer.wrap(data, idx, 8).order(ByteOrder.LITTLE_ENDIAN).long
                val dVal = ByteBuffer.wrap(data, idx + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
                when (dTag) {
                    5L  -> strtab = dVal
                    15L -> if (runpathStrOffset < 0L) runpathStrOffset = dVal
                    29L -> runpathStrOffset = dVal
                }
                if (dTag == 0L) break
                off += 24
            }
        }
        if (runpathStrOffset < 0L || strtab == 0L) return null
        val strLoad = loads.firstOrNull { strtab in it.pVaddr until (it.pVaddr + it.pMemsz) } ?: return null
        val strOff = (strLoad.pOffset + (strtab - strLoad.pVaddr) + runpathStrOffset).toInt()
        if (strOff < 0 || strOff >= data.size) return null
        var end = strOff
        while (end < data.size && data[end] != 0.toByte()) end++
        return data.copyOfRange(strOff, end).decodeToString()
    }

    private fun addAndroidNote(elfFile: File) {
        if (!elfFile.isFile) return
        val data = elfFile.readBytes()
        if (data.size < 64) return
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return
        if (data[4] != 2.toByte()) return

        val phoff = ByteBuffer.wrap(data, 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val phentsize = ByteBuffer.wrap(data, 54, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val phnum = ByteBuffer.wrap(data, 56, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (phnum == 0 || phentsize == 0) return

        var noteOff = -1L
        var noteFilesz = 0L
        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0L || phOff.toInt() + 56 > data.size) return
            if (ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int != 4) continue
            noteOff = ByteBuffer.wrap(data, phOff.toInt() + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
            noteFilesz = ByteBuffer.wrap(data, phOff.toInt() + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
            break
        }
        if (noteOff < 0L) return

        var notesEnd = noteOff
        while (notesEnd < noteOff + noteFilesz) {
            val idx = notesEnd.toInt()
            if (idx + 12 > data.size) break
            val nNamesz = ByteBuffer.wrap(data, idx, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val nDescsz = ByteBuffer.wrap(data, idx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (nNamesz <= 0 || nNamesz > 256 || nDescsz < 0 || nDescsz > 65536) break
            val namePadded = ((nNamesz + 3) and 0x7FFFFFFC).toLong()
            val descPadded = ((nDescsz + 3) and 0x7FFFFFFC).toLong()
            notesEnd += 12L + namePadded + descPadded
            if (notesEnd >= noteOff + noteFilesz) break
        }

        if (notesEnd + 16 > noteOff + noteFilesz) {
            Log.w(TAG, "PT_NOTE too small in ${elfFile.name}")
            return
        }

        val pos = notesEnd.toInt()
        val bb = ByteBuffer.wrap(data, pos, data.size - pos).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(2)
        bb.putInt(0)
        bb.putInt(0x40000000)
        data[pos + 12] = 'L'.code.toByte()
        data[pos + 13] = 0
        data[pos + 14] = 0
        data[pos + 15] = 0
        elfFile.writeBytes(data)
        Log.i(TAG, "Added ANDROID_LD_LIBRARY_PATH note to ${elfFile.name}")
    }

    private fun patchTermuxDataPaths(elfFile: File) {
        if (!elfFile.isFile) return
        val data = elfFile.readBytes()
        if (data.size < 64) return

        val oldBytes = "/data/data/com.termux/files/usr".encodeToByteArray()
        val newBytes = "/data/data/${context.packageName}/p".encodeToByteArray()
        if (oldBytes.size != newBytes.size) {
            Log.w(TAG, "Cannot patch ${elfFile.name}: length mismatch (old=${oldBytes.size}, new=${newBytes.size})")
            return
        }

        var count = 0
        var pos = 0
        while (true) {
            val idx = data.indexOf(oldBytes, pos)
            if (idx < 0) break
            newBytes.copyInto(data, idx)
            count++
            pos = idx + oldBytes.size
        }

        if (count > 0) {
            elfFile.writeBytes(data)
            Log.i(TAG, "Patched $count /data/data/com.termux/files/usr path(s) in ${elfFile.name}")
        } else {
            Log.d(TAG, "No Termux paths found in ${elfFile.name}")
        }
    }

    private fun ByteArray.indexOf(pattern: ByteArray, startIndex: Int): Int {
        outer@ for (i in startIndex..size - pattern.size) {
            for (j in pattern.indices) {
                if (this[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}