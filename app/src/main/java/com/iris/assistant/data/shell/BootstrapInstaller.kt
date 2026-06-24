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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the IRIS bootstrap into the app's private files directory.
 *
 * The bootstrap zip is embedded in the APK as a native shared library
 * (libiris-bootstrap.so) via app/src/main/cpp/termux-bootstrap-zip.S.
 * At runtime, [getZip] loads the library and returns the raw zip bytes.
 *
 * Layout after installation:
 *   filesDir/
 *     usr/           ← PREFIX   (staging → rename → final)
 *     usr-staging/   ← STAGING  (deleted after rename)
 *     home/          ← HOME
 *
 * The bootstrap zip contains a SYMLINKS.txt entry in the format:
 *   <target>←<link_path_relative_to_prefix>
 * These are applied via [Os.symlink] after all files are extracted.
 *
 * UNTESTED — verify before use
 */
@Singleton
class BootstrapInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "BootstrapInstaller"
        private const val VERSION_FILE = "bootstrap/INSTALLED_VERSION"
        private const val BOOTSTRAP_VERSION = "iris-1" // bump when bootstrap zip changes
        private const val BUFFER_SIZE = 8096
    }

    // ── Paths ────────────────────────────────────────────────────────────────

    val prefixDir: File
        get() = File(context.filesDir, "usr")

    val homeDir: File
        get() = File(context.filesDir, "home").also { it.mkdirs() }

    private val stagingPrefixDir: File
        get() = File(context.filesDir, "usr-staging")

    private val versionFile: File
        get() = File(context.filesDir, VERSION_FILE)

    // ── Public API ────────────────────────────────────────────────────────────

    fun isInstalled(): Boolean =
        versionFile.exists() &&
        prefixDir.exists() &&
        prefixDir.list()?.isNotEmpty() == true

    fun installedVersion(): String? =
        if (versionFile.exists()) versionFile.readText().trim() else null

    /**
     * Full install pipeline as [BootstrapState] flow.
     * Fast-path if already installed.
     */
    fun install(): Flow<BootstrapState> = flow {
        if (isInstalled()) {
            emit(BootstrapState.Installed(prefixDir.absolutePath, installedVersion()!!))
            return@flow
        }

        emit(BootstrapState.Extracting)

        runCatching {
            // Clean up any leftover staging dir from a previous failed install
            stagingPrefixDir.deleteRecursively()
            stagingPrefixDir.mkdirs()

            // Delete broken prefix if present
            prefixDir.deleteRecursively()

            // Load the zip bytes from the embedded .so
            val zipBytes = loadZipBytes()
            Log.d(TAG, "Bootstrap zip loaded: ${zipBytes.size} bytes")

            // Extract — deferred symlinks collected from SYMLINKS.txt
            val symlinks = mutableListOf<Pair<String, String>>() // target → newPath
            val buffer = ByteArray(BUFFER_SIZE)

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break

                    if (entry.name == "SYMLINKS.txt") {
                        // Parse symlink manifest
                        zis.bufferedReader().forEachLine { line ->
                            val parts = line.split("←")
                            if (parts.size != 2) {
                                Log.w(TAG, "Malformed SYMLINKS.txt line: $line")
                                return@forEachLine
                            }
                            val target  = parts[0]
                            val newPath = "${stagingPrefixDir.absolutePath}/${parts[1]}"
                            symlinks.add(target to newPath)
                            // Ensure parent dir exists
                            File(newPath).parentFile?.mkdirs()
                        }
                    } else {
                        val targetFile = File(stagingPrefixDir, entry.name)
                        val isDir = entry.isDirectory

                        if (isDir) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { out ->
                                while (true) {
                                    val n = zis.read(buffer)
                                    if (n == -1) break
                                    out.write(buffer, 0, n)
                                }
                            }
                            // Match Termux's chmod logic exactly
                            if (entry.name.startsWith("bin/") ||
                                entry.name.startsWith("libexec") ||
                                entry.name.startsWith("lib/apt/apt-helper") ||
                                entry.name.startsWith("lib/apt/methods")) {
                                @Suppress("OctalInteger")
                                Os.chmod(targetFile.absolutePath, 0b111_000_000) // 0700
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }

            if (symlinks.isEmpty()) {
                throw RuntimeException("SYMLINKS.txt not found in bootstrap zip — zip may be corrupt")
            }

            // Apply symlinks
            for ((target, newPath) in symlinks) {
                Os.symlink(target, newPath)
            }

            // Atomic rename: staging → prefix
            if (!stagingPrefixDir.renameTo(prefixDir)) {
                throw RuntimeException(
                    "Failed to rename staging prefix '${stagingPrefixDir.absolutePath}' → '${prefixDir.absolutePath}'"
                )
            }

            homeDir.mkdirs()

            // Write version marker
            versionFile.parentFile?.mkdirs()
            versionFile.writeText(BOOTSTRAP_VERSION)

            Log.i(TAG, "Bootstrap installed at ${prefixDir.absolutePath} ($BOOTSTRAP_VERSION)")

        }.onFailure { ex ->
            stagingPrefixDir.deleteRecursively()
            Log.e(TAG, "Bootstrap install failed", ex)
            emit(BootstrapState.Error(
                message   = "Bootstrap install failed: ${ex.message}",
                cause     = ex,
                retryable = true,
            ))
            return@flow
        }

        emit(BootstrapState.Installed(prefixDir.absolutePath, BOOTSTRAP_VERSION))

    }.flowOn(Dispatchers.IO)

    /**
     * Removes the installed bootstrap entirely.
     */
    suspend fun uninstall() = withContext(Dispatchers.IO) {
        prefixDir.deleteRecursively()
        stagingPrefixDir.deleteRecursively()
        homeDir.deleteRecursively()
        versionFile.delete()
        Log.i(TAG, "Bootstrap uninstalled")
    }

    // ── Native ───────────────────────────────────────────────────────────────

    /**
     * Loads libiris-bootstrap.so and returns the embedded bootstrap zip bytes.
     * The .so is never dlopen'd again after the first call (Android caches it).
     */
    private fun loadZipBytes(): ByteArray {
        System.loadLibrary("iris-bootstrap")
        return getZip()
    }

    private external fun getZip(): ByteArray
}