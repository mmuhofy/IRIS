package com.iris.assistant.data.shell

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import com.iris.assistant.domain.model.BootstrapState
import com.iris.assistant.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads, verifies, and extracts the Termux bootstrap zip into the app's
 * private files directory.
 *
 * Layout after installation:
 *   filesDir/
 *     termux/
 *       usr/          ← TERMUX_PREFIX  (extracted bootstrap contents)
 *       home/         ← TERMUX_HOME    (created empty, used as $HOME)
 *     bootstrap/
 *       bootstrap-{ABI}.zip   ← temporary download; deleted after extraction
 *       INSTALLED_VERSION     ← plain-text version tag of installed bootstrap
 *
 * The bootstrap zip is sourced from termux/termux-packages GitHub Releases.
 * Asset naming: bootstrap-aarch64.zip / bootstrap-arm.zip / bootstrap-x86_64.zip / bootstrap-i686.zip
 *
 * The SHA-256 checksums are published as a separate asset named CHECKSUMS.sha256
 * in the same release. We fetch and parse that file to verify the download.
 *
 * UNTESTED — verify before use
 */
@Singleton
class BootstrapDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "BootstrapDownloader"

        // GitHub API — latest release of termux-packages bootstrap
        private const val GITHUB_RELEASES_API =
            "https://api.github.com/repos/termux/termux-packages/releases"

        // How many releases to fetch when looking for a bootstrap release
        private const val RELEASE_FETCH_COUNT = 5

        // Download buffer size
        private const val BUFFER_SIZE = 8 * 1024 // 8 KB

        // Name of the file we write to record which version is installed
        private const val VERSION_FILE_NAME = "INSTALLED_VERSION"
    }

    // ── Directories ──────────────────────────────────────────────────────────

    /** Root dir for all bootstrap-related files: `filesDir/bootstrap/` */
    private val bootstrapDir: File
        get() = File(context.filesDir, "bootstrap").also { it.mkdirs() }

    /** Where the downloaded zip lives temporarily. Deleted after extraction. */
    private fun zipFile(abiAsset: String): File = File(bootstrapDir, abiAsset)

    /** Persisted version tag of the currently installed bootstrap. */
    private val versionFile: File
        get() = File(bootstrapDir, VERSION_FILE_NAME)

    /** Termux prefix — the extracted bootstrap lands here. */
    val prefixDir: File
        get() = File(context.filesDir, "termux/usr").also { it.mkdirs() }

    /** Termux home directory. */
    val homeDir: File
        get() = File(context.filesDir, "termux/home").also { it.mkdirs() }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns true if a bootstrap is already installed and the version marker
     * file is present. Does NOT verify zip integrity of the installed files.
     */
    fun isInstalled(): Boolean = versionFile.exists() && prefixDir.list()?.isNotEmpty() == true

    /**
     * Returns the installed version tag, or null if not installed.
     */
    fun installedVersion(): String? =
        if (versionFile.exists()) versionFile.readText().trim() else null

    /**
     * Full install pipeline emitted as [BootstrapState] values.
     *
     * Sequence:
     *   Checking → Downloading(…) → Extracting → Installed
     *   or at any point → Error
     *
     * Callers collect this Flow and mirror the state into their ViewModel.
     * Safe to re-call: if already installed, emits [BootstrapState.Installed] immediately.
     */
    fun install(): Flow<BootstrapState> = flow {
        // ── Fast-path: already installed ─────────────────────────────────
        if (isInstalled()) {
            emit(BootstrapState.Installed(prefixDir.absolutePath, installedVersion()!!))
            return@flow
        }

        // ── Step 1: detect ABI ───────────────────────────────────────────
        val abiAsset = resolveAbiAsset()
        if (abiAsset == null) {
            emit(BootstrapState.Error(
                message  = "Unsupported ABI: ${Build.SUPPORTED_ABIS.firstOrNull()}",
                retryable = false,
            ))
            return@flow
        }
        Log.d(TAG, "Resolved ABI asset: $abiAsset")

        // ── Step 2: fetch latest release tag ─────────────────────────────
        emit(BootstrapState.Checking)
        val release = runCatching { fetchLatestBootstrapRelease() }.getOrElse { ex ->
            emit(BootstrapState.Error(
                message   = "Failed to fetch release info: ${ex.message}",
                cause     = ex,
                retryable = true,
            ))
            return@flow
        }
        Log.d(TAG, "Latest bootstrap release: ${release.tagName}")

        // ── Step 3: resolve download URL + expected SHA-256 ──────────────
        val zipAsset = release.assets.find { it.name == abiAsset }
        if (zipAsset == null) {
            emit(BootstrapState.Error(
                message   = "Asset '$abiAsset' not found in release ${release.tagName}",
                retryable = true,
            ))
            return@flow
        }

        val expectedSha256 = runCatching {
            fetchExpectedChecksum(release, abiAsset)
        }.getOrElse { ex ->
            // Non-fatal: log warning but proceed without checksum validation
            Log.w(TAG, "Could not fetch CHECKSUMS.sha256, skipping verification: ${ex.message}")
            null
        }

        // ── Step 4: download ─────────────────────────────────────────────
        val destZip = zipFile(abiAsset)
        runCatching {
            downloadFile(zipAsset.browserDownloadUrl, destZip) { downloaded, total ->
                // Emit progress — trySend equivalent in flow context
                // Each emission here is a new Downloading state
                emit(BootstrapState.Downloading(downloaded, total))
            }
        }.onFailure { ex ->
            destZip.delete()
            emit(BootstrapState.Error(
                message   = "Download failed: ${ex.message}",
                cause     = ex,
                retryable = true,
            ))
            return@flow
        }

        // ── Step 5: verify SHA-256 ───────────────────────────────────────
        if (expectedSha256 != null) {
            val actualSha256 = computeSha256(destZip)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                destZip.delete()
                emit(BootstrapState.Error(
                    message   = "SHA-256 mismatch. Expected $expectedSha256, got $actualSha256",
                    retryable = true,
                ))
                return@flow
            }
            Log.d(TAG, "SHA-256 verified: $actualSha256")
        }

        // ── Step 6: extract ──────────────────────────────────────────────
        emit(BootstrapState.Extracting)
        runCatching {
            extractZip(destZip, prefixDir)
        }.onFailure { ex ->
            destZip.delete()
            prefixDir.deleteRecursively()
            emit(BootstrapState.Error(
                message   = "Extraction failed: ${ex.message}",
                cause     = ex,
                retryable = true,
            ))
            return@flow
        }

        // ── Step 7: cleanup zip + create home dir ────────────────────────
        destZip.delete()
        homeDir.mkdirs()

        // ── Step 8: fix binary permissions ───────────────────────────────
        // Extracted binaries must be executable. ZipInputStream does not
        // preserve Unix permissions, so we chmod the bin/ and sbin/ dirs.
        fixPermissions(prefixDir)

        // ── Step 9: fix symlinks ─────────────────────────────────────────
        // Symlinks in the bootstrap zip are stored as regular files
        // containing the target path (ZipInputStream limitation). The system
        // linker needs actual symlinks to resolve DT_NEEDED versioned names
        // like libreadline.so.8 → libreadline.so.8.0.
        fixSymlinks(prefixDir)

        // ── Step 9: install proot via apt ────────────────────────────────
        // proot is not bundled in the bootstrap zip — it must be pulled via
        // the Termux package manager. We run apt in a one-shot subprocess
        // (not via EmbeddedShell, which isn't started yet) to avoid a
        // circular dependency.
        emit(BootstrapState.InstallingPackages("proot"))
        runCatching {
            installPackage("proot")
        }.onFailure { ex ->
            // Non-fatal: proot install failure is logged but does not block
            // bootstrap from being marked Installed. The shell can still be
            // used without proot for non-chroot workflows. The UI should
            // surface a warning when proot is required.
            Log.w(TAG, "proot install failed (non-fatal): ${ex.message}")
        }

        // ── Step 10: write version marker ───────────────────────────────
        versionFile.writeText(release.tagName)

        Log.d(TAG, "Bootstrap installed at ${prefixDir.absolutePath} (${release.tagName})")
        emit(BootstrapState.Installed(prefixDir.absolutePath, release.tagName))

    }.flowOn(Dispatchers.IO)

    /**
     * Completely removes the installed bootstrap.
     * Call from a coroutine on [Dispatchers.IO].
     */
    suspend fun uninstall() = withContext(Dispatchers.IO) {
        prefixDir.deleteRecursively()
        homeDir.deleteRecursively()
        bootstrapDir.deleteRecursively()
        Log.d(TAG, "Bootstrap uninstalled")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Maps the device's primary ABI to the corresponding Termux bootstrap asset name.
     * Returns null for unsupported ABIs.
     *
     * Termux bootstrap asset names (verified against termux/termux-packages releases):
     *   arm64-v8a  → bootstrap-aarch64.zip
     *   armeabi-v7a → bootstrap-arm.zip
     *   x86_64     → bootstrap-x86_64.zip
     *   x86        → bootstrap-i686.zip
     */
    private fun resolveAbiAsset(): String? {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when (primaryAbi) {
            "arm64-v8a"   -> "bootstrap-aarch64.zip"
            "armeabi-v7a" -> "bootstrap-arm.zip"
            "x86_64"      -> "bootstrap-x86_64.zip"
            "x86"         -> "bootstrap-i686.zip"
            else          -> null
        }
    }

    /**
     * Fetches the GitHub Releases list and returns the first release whose tag
     * starts with "bootstrap-". This is how termux/termux-packages names its
     * bootstrap releases (e.g. "bootstrap-2025.12.14-r1+apt-android-7").
     */
    private fun fetchLatestBootstrapRelease(): GitHubRelease {
        val url = "$GITHUB_RELEASES_API?per_page=$RELEASE_FETCH_COUNT"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "IRIS-Assistant")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub API error: ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from GitHub API")

            val releases = json.decodeFromString<List<GitHubRelease>>(body)
            return releases.firstOrNull { it.tagName.startsWith("bootstrap-") }
                ?: throw IllegalStateException("No bootstrap release found in last $RELEASE_FETCH_COUNT releases")
        }
    }

    /**
     * Fetches the CHECKSUMS.sha256 asset from the release and parses the expected
     * hash for the given [abiAsset] filename.
     *
     * The checksum file format (one entry per line):
     *   <sha256hex>  <filename>
     * e.g.:
     *   1e3d80bd...  bootstrap-aarch64.zip
     */
    private fun fetchExpectedChecksum(release: GitHubRelease, abiAsset: String): String {
        val checksumAsset = release.assets.find { it.name == "CHECKSUMS.sha256" }
            ?: throw IllegalStateException("CHECKSUMS.sha256 not found in release ${release.tagName}")

        val request = Request.Builder()
            .url(checksumAsset.browserDownloadUrl)
            .header("User-Agent", "IRIS-Assistant")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch checksums: ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty checksums file")

            // Parse lines of the form "<sha256>  <filename>"
            return body.lineSequence()
                .map { it.trim() }
                .filter { it.endsWith(abiAsset) }
                .firstOrNull()
                ?.split("\\s+".toRegex())
                ?.firstOrNull()
                ?: throw IllegalStateException("No checksum found for $abiAsset")
        }
    }

    /**
     * Downloads [url] to [dest], emitting progress via [onProgress].
     *
     * @param onProgress called with (bytesDownloaded, totalBytes); totalBytes may be -1
     */
    private suspend fun downloadFile(
        url: String,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IRIS-Assistant")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            val body = response.body
                ?: throw IllegalStateException("Empty response body")

            val totalBytes = body.contentLength() // -1 if unknown
            var downloaded = 0L

            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
        }
    }

    /**
     * Extracts a zip file into [destDir].
     *
     * Security note: path traversal attack prevention — any entry whose
     * canonical path does not start with [destDir]'s canonical path is skipped
     * with a warning. This guards against malformed zips with "../" entries.
     */
    private fun extractZip(zipFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        var entryCount = 0

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val entryFile = File(destDir, entry.name)

                // Path traversal guard
                if (!entryFile.canonicalPath.startsWith(canonicalDest)) {
                    Log.w(TAG, "Skipping potentially malicious zip entry: ${entry.name}")
                    zis.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    entryFile.outputStream().use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val n = zis.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                zis.closeEntry()
                entryCount++
            }
        }
        Log.d(TAG, "Extracted $entryCount entries to ${destDir.absolutePath}")
    }

    /**
     * Recursively marks all files inside [prefixDir]/bin and [prefixDir]/sbin
     * as executable. ZipInputStream strips Unix permission bits, so this step
     * is required after extraction.
     *
     * UNTESTED — verify before use
     */
    private fun fixPermissions(prefixDir: File) {
        listOf("bin", "sbin", "lib", "lib/apt/methods").forEach { sub ->
            File(prefixDir, sub).walkTopDown()
                .filter { it.isFile }
                .forEach { f ->
                    if (!f.canExecute()) f.setExecutable(true, false)
                }
        }
        Log.d(TAG, "Binary permissions fixed")
    }

    /**
     * Converts symlink-stored-as-regular-file entries (a limitation of
     * ZipInputStream) into actual filesystem symlinks. The Termux bootstrap
     * zip uses symlinks for versioned .so files (e.g. libreadline.so.8 →
     * libreadline.so.8.0). Without real symlinks, the system linker cannot
     * resolve DT_NEEDED entries like "libreadline.so.8".
     *
     * Detection heuristic: a file < 100 bytes whose content is a relative
     * path matching a real file in the same directory is a stored symlink.
     */
    private fun fixSymlinks(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (!file.isFile || file.length() > 100L) return@forEach
            val content = file.readText().trim()
            if (content.isEmpty() || content.contains('\n') || content.contains('\u0000')) return@forEach
            val target = File(file.parentFile, content)
            if (target.exists() && target.isFile && target != file) {
                file.delete()
                try {
                    Os.symlink(content, file.absolutePath)
                    Log.d(TAG, "Symlink: ${file.name} -> $content")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create symlink ${file.name} -> $content: ${e.message}")
                }
            }
        }
    }

    /**
     * Runs `apt-get install -y <packageName>` inside the Termux prefix as a
     * one-shot blocking subprocess. Does NOT use [EmbeddedShell] — this runs
     * before the persistent shell session is started.
     *
     * Environment mirrors what [EmbeddedShell.start] sets up so that apt can
     * locate its libraries and configuration.
     *
     * Throws [IllegalStateException] if the process exits with a non-zero code
     * or if the apt binary is not found.
     *
     * UNTESTED — verify before use
     */
    private fun installPackage(packageName: String) {
        val prefix   = prefixDir.absolutePath
        val aptBin   = File(prefixDir, "bin/apt-get")
        if (!aptBin.exists()) {
            throw IllegalStateException(
                "apt-get not found at ${aptBin.absolutePath}. " +
                "Bootstrap may be incomplete or not an apt-based release."
            )
        }
        if (!aptBin.canExecute()) aptBin.setExecutable(true, false)

        val linker = if (File("/system/bin/linker64").exists()) {
            "/system/bin/linker64"
        } else {
            "/system/bin/linker"
        }

        Log.d(TAG, "Running: $linker ${aptBin.absolutePath} install -y $packageName")

        val proc = ProcessBuilder(linker, aptBin.absolutePath, "install", "-y", packageName)
            .redirectErrorStream(true) // merge stderr into stdout for logging
            .apply {
                environment().apply {
                    put("HOME",             homeDir.absolutePath)
                    put("PREFIX",           prefix)
                    put("PATH",             "$prefix/bin:$prefix/sbin:/system/bin:/system/xbin")
                    put("TMPDIR",           "$prefix/tmp")
                    put("TERM",             "dumb")
                    put("LANG",             "en_US.UTF-8")
                    put("LD_LIBRARY_PATH",  "$prefix/lib")
                    put("TERMUX_PREFIX",    prefix)
                    // Suppress interactive prompts
                    put("DEBIAN_FRONTEND",  "noninteractive")
                }
                directory(homeDir)
            }
            .start()

        // Log all output from apt at DEBUG level
        val output = proc.inputStream.bufferedReader().readText()
        val exit   = proc.waitFor()
        Log.d(TAG, "apt-get install $packageName exit=$exit\n$output")

        if (exit != 0) {
            throw IllegalStateException(
                "apt-get install $packageName failed with exit code $exit"
            )
        }
    }

    /**
     * Computes the SHA-256 hex digest of [file].
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── JSON models (GitHub Releases API) ────────────────────────────────────

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("assets")   val assets: List<GitHubAsset>,
    )

    @Serializable
    private data class GitHubAsset(
        @SerialName("name")                 val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        @SerialName("size")                 val size: Long = -1L,
    )
}