package com.iris.assistant.data.shell

import android.content.Context
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
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the IRIS bootstrap from assets into the app's private files directory.
 *
 * The bootstrap zip lives at app/src/main/assets/bootstrap-aarch64.zip.
 * It contains a fully extracted filesystem under the prefix:
 *   data/data/com.iris.assistant/files/
 * which is stripped during extraction, landing everything under [context.filesDir].
 *
 * Layout after installation:
 *   filesDir/
 *     usr/     ← PREFIX
 *     home/    ← HOME
 *
 * UNTESTED — verify before use
 */
@Singleton
class BootstrapInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG               = "BootstrapInstaller"
        private const val ASSET_NAME        = "bootstrap-aarch64.zip"
        private const val STRIP_PREFIX      = "data/data/com.iris.assistant/files/"
        private const val VERSION_FILE      = "bootstrap/INSTALLED_VERSION"
        private const val BOOTSTRAP_VERSION = "iris-1"
        private const val BUFFER_SIZE       = 8096
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

            context.assets.open(ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream.buffered()).use { zis ->
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

                        // usr/ → stagingDir for atomic rename; rest → filesDir directly
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
            }

            if (!stagingDir.renameTo(prefixDir)) {
                throw RuntimeException(
                    "Failed to rename ${stagingDir.absolutePath} → ${prefixDir.absolutePath}"
                )
            }

            homeDir.mkdirs()
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
}