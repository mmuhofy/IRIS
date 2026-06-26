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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootstrapInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "BootstrapInstaller"

        private const val BOOTSTRAP_TAG = "bootstrap-20260624-r1"
        private const val BOOTSTRAP_ZIP_NAME = "bootstrap-aarch64.zip"
        private const val CHECKSUMS_NAME = "CHECKSUMS.sha256"

        private val BASE_URL = "https://github.com/mmuhofy/IRIS/releases/download/$BOOTSTRAP_TAG"
        private val ZIP_URL = "$BASE_URL/$BOOTSTRAP_ZIP_NAME"
        private val CHECKSUMS_URL = "$BASE_URL/$CHECKSUMS_NAME"

        private const val STRIP_PREFIX = "data/data/com.iris.assistant/files/"
        private const val VERSION_FILE = "bootstrap/INSTALLED_VERSION"
        private const val BOOTSTRAP_VERSION = BOOTSTRAP_TAG
        private const val BUFFER_SIZE = 8096
        private const val CACHE_ZIP_NAME = "$BOOTSTRAP_TAG.zip"
    }

    val prefixDir: File
        get() = File(context.filesDir, "usr")

    val homeDir: File
        get() = File(context.filesDir, "home").also { it.mkdirs() }

    private val stagingDir: File
        get() = File(context.filesDir, "usr-staging")

    private val versionFile: File
        get() = File(context.filesDir, VERSION_FILE)

    private val cacheDir: File
        get() = File(context.filesDir, "bootstrap").also { it.mkdirs() }

    private val cachedZip: File
        get() = File(cacheDir, CACHE_ZIP_NAME)

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

        try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            prefixDir.deleteRecursively()

            if (!cachedZip.exists()) {
                emit(BootstrapState.Checking)
                val expectedSha256 = downloadChecksum()
                emit(BootstrapState.Downloading(0, -1))
                val tempFile = File(cacheDir, "${CACHE_ZIP_NAME}.tmp")
                downloadToFile(tempFile) { sent, total ->
                    emit(BootstrapState.Downloading(sent, total))
                }
                if (expectedSha256 != null) {
                    val actual = sha256File(tempFile)
                    if (expectedSha256 != actual) {
                        tempFile.delete()
                        throw RuntimeException(
                            "SHA-256 mismatch: expected=$expectedSha256 actual=$actual"
                        )
                    }
                    Log.i(TAG, "Bootstrap zip verified (SHA-256)")
                }
                tempFile.renameTo(cachedZip)
            } else {
                Log.i(TAG, "Using cached bootstrap zip: ${cachedZip.absolutePath}")
            }

            emit(BootstrapState.Extracting)
            val buffer = ByteArray(BUFFER_SIZE)

            val extractStream: InputStream = cachedZip.inputStream().buffered().let { outer ->
                val peekZis = ZipInputStream(outer)
                val firstEntry = peekZis.nextEntry
                if (firstEntry == null) {
                    peekZis.close()
                    throw RuntimeException("Empty bootstrap zip")
                }
                val firstBytes = ByteArrayOutputStream()
                val tmpBuf = ByteArray(4096)
                while (true) {
                    val n = peekZis.read(tmpBuf)
                    if (n == -1) break
                    firstBytes.write(tmpBuf, 0, n)
                }
                peekZis.closeEntry()
                val hasMore = peekZis.nextEntry != null
                peekZis.close()
                val data = firstBytes.toByteArray()
                if (!hasMore && data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
                    Log.i(TAG, "Detected nested zip: '${firstEntry.name}' (${data.size} bytes)")
                    data.inputStream().buffered()
                } else {
                    cachedZip.inputStream().buffered()
                }
            }

            ZipInputStream(extractStream).use { zis ->
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

            val shortLibDir = File("/data/data/${context.packageName}/u/lib")
            shortLibDir.parentFile?.mkdirs()
            try { Os.remove(shortLibDir.absolutePath) } catch (_: android.system.ErrnoException) { }
            try {
                Os.symlink(File(prefixDir, "lib").absolutePath, shortLibDir.absolutePath)
                Log.d(TAG, "Short lib symlink: ${shortLibDir.absolutePath} -> ${File(prefixDir, "lib").absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Short lib symlink failed: ${e.message}")
            }

            patchElfRunpath(File(prefixDir, "bin/bash"), shortLibDir.absolutePath)
            patchElfRunpath(File(prefixDir, "bin/apt-get"), shortLibDir.absolutePath)

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

    private fun downloadChecksum(): String? {
        return try {
            val request = Request.Builder().url(CHECKSUMS_URL).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            body.lineSequence().forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2 && parts[1].contains(BOOTSTRAP_ZIP_NAME)) {
                    return parts[0]
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download checksum: ${e.message}")
            null
        }
    }

    private suspend fun downloadToFile(dest: File, onProgress: suspend (Long, Long) -> Unit) {
        val request = Request.Builder().url(ZIP_URL).get().build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Download failed: HTTP ${response.code}")
        }
        val body = response.body ?: throw RuntimeException("Empty response body")
        val total = body.contentLength()
        val buffer = ByteArray(BUFFER_SIZE)
        var read = 0L
        var lastPct = 0
        body.byteStream().use { input ->
            FileOutputStream(dest).use { output ->
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    output.write(buffer, 0, n)
                    read += n
                    val pct = if (total > 0) (read * 100 / total).toInt() else 0
                    if (pct != lastPct || read >= total) {
                        lastPct = pct
                        onProgress(read, total)
                    }
                }
            }
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        prefixDir.deleteRecursively()
        stagingDir.deleteRecursively()
        homeDir.deleteRecursively()
        versionFile.delete()
        Log.i(TAG, "Bootstrap uninstalled")
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().buffered().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
                off += 16
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
                off += 16
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
