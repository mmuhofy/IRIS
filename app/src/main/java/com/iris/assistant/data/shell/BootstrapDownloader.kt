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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

        // ── Step 10: create short symlink to lib directory ──────────────
        // The system linker (linker64) resolves $ORIGIN based on its OWN
        // executable path (/system/bin), not the loaded binary's path.
        // So $ORIGIN/../lib in DT_RUNPATH resolves to /system/lib64 —
        // useless. We need an absolute DT_RUNPATH.
        //
        // The original Termux path (34 bytes) in the ELF string table
        // limits our replacement to ≤34 chars. /data/data/<pkg>/u/lib
        // (33 chars) fits. We create a symlink at that short path
        // pointing to the real lib directory.
        val shortLibDir = File("/data/data/${context.packageName}/u/lib")
        shortLibDir.parentFile?.mkdirs()
        try {
            // Remove old symlink first (if any)
            Os.remove(shortLibDir.absolutePath)
        } catch (_: android.system.ErrnoException) { }
        try {
            Os.symlink(File(prefixDir, "lib").absolutePath, shortLibDir.absolutePath)
            Log.d(TAG, "Created short lib symlink: ${shortLibDir.absolutePath} -> ${File(prefixDir, "lib").absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create short lib symlink: ${e.message}")
        }

        // ── Step 11: patch DT_RUNPATH in key binaries ───────────────────
        // Replace the hardcoded Termux runpath with our absolute short lib
        // path so the system linker can find shared libraries.
        File(prefixDir, "bin/bash").let { if (it.exists()) patchElfRunpath(it, shortLibDir.absolutePath) }
        File(prefixDir, "bin/apt-get").let { if (it.exists()) patchElfRunpath(it, shortLibDir.absolutePath) }

        // ── Step 12: add ANDROID_LD_LIBRARY_PATH note to key binaries ────
        // Without this ELF note, the dynamic linker ignores our
        // LD_LIBRARY_PATH env var. Adding it forces the linker to honour
        // LD_LIBRARY_PATH, giving the shell a working library search path
        // even if DT_RUNPATH patching is insufficient (e.g. when running
        // through /system/bin/linker64 which may not observe DT_RUNPATH).
        File(prefixDir, "bin/bash").let { if (it.exists()) addAndroidNote(it) }
        File(prefixDir, "bin/apt-get").let { if (it.exists()) addAndroidNote(it) }

        // ── Step 13: install proot via apt ───────────────────────────────
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

        // ── Step 14: write version marker ───────────────────────────────
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
     * Replaces symlink-stored-as-regular-file entries (a limitation of
     * ZipInputStream) with actual ELF copies. The Termux bootstrap zip uses
     * symlinks for versioned .so files (e.g. libreadline.so.8 →
     * libreadline.so.8.0). Without real files at the versioned name, the
     * system linker cannot resolve DT_NEEDED entries like "libreadline.so.8".
     *
     * Symlink chains can be multiple levels deep (libfoo.so → libfoo.so.26 →
     * libfoo.so.26.0.1). We resolve the entire chain recursively until we
     * find the real ELF file (≥ 100 bytes), then copy it to every node in
     * the chain.
     */
    private fun fixSymlinks(dir: File) {
        // Collect all potential symlink-text files first (stable iteration)
        data class Link(val file: File, val targetName: String)
        val links = mutableListOf<Link>()

        dir.walkTopDown().forEach { file ->
            if (!file.isFile || file.length() > 100L) return@forEach
            val content = file.readBytes()
                .filterNot { it == 0x0.toByte() || it == 0x0a.toByte() || it == 0x0d.toByte() }
                .toByteArray()
                .decodeToString()
                .trim()
            if (content.isEmpty() || content.length > 64) return@forEach
            val target = File(file.parentFile, content)
            if (target.exists() && target.isFile && target != file) {
                links.add(Link(file, content))
            }
        }

        // Recursively follow a symlink chain to the real file
        fun resolveReal(start: File, visited: MutableSet<String>): File? {
            if (start.length() >= 100L) return start
            if (!visited.add(start.absolutePath)) return null
            val content = start.readBytes()
                .filterNot { it == 0x0.toByte() || it == 0x0a.toByte() || it == 0x0d.toByte() }
                .toByteArray()
                .decodeToString()
                .trim()
            if (content.isEmpty() || content.length > 64) return null
            val next = File(start.parentFile, content)
            if (!next.exists() || next == start) return null
            return resolveReal(next, visited)
        }

        for ((linkFile, _) in links) {
            val real = resolveReal(linkFile, mutableSetOf()) ?: continue
            if (real.length() < 100L) continue
            linkFile.delete()
            try {
                real.copyTo(linkFile, overwrite = false)
                Log.d(TAG, "Resolved symlink chain: ${linkFile.name} -> ... -> ${real.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy ${real.name} -> ${linkFile.name}: ${e.message}")
            }
        }
    }

    /**
     * Patches the DT_RUNPATH (or DT_RPATH) of a 64-bit ELF binary to point
     * to [libAbsPath]. This is required because Termux's prebuilt binaries
     * have hardcoded RPATHs like /data/data/com.termux/files/usr/lib, which
     * are invalid when the bootstrap is extracted to our app's data directory.
     *
     * The new path MUST be ≤ the old path length (otherwise we'd need to
     * resize the .dynstr section). The caller should ensure this constraint
     * holds (see step 10 where a short symlink path is created).
     */
    private fun patchElfRunpath(elfFile: File, libAbsPath: String) {
        if (!elfFile.isFile) return
        val data = elfFile.readBytes()
        if (data.size < 64) return

        // ELF magic
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return

        // Only handle 64-bit ELF (arm64-v8a)
        if (data[4] != 2.toByte()) return

        val phoff = ByteBuffer.wrap(data, 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val phentsize = ByteBuffer.wrap(data, 54, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val phnum = ByteBuffer.wrap(data, 56, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        // Collect PT_LOAD segments to convert vaddr → file offset
        data class LoadSeg(val pOffset: Long, val pVaddr: Long, val pMemsz: Long)
        val loads = mutableListOf<LoadSeg>()

        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0 || phOff.toInt() + 56 > data.size) return
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pFlags = ByteBuffer.wrap(data, phOff.toInt() + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val pOffset = ByteBuffer.wrap(data, phOff.toInt() + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pVaddr = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pMemsz = ByteBuffer.wrap(data, phOff.toInt() + 40, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (pType == 1) { // PT_LOAD (any — .dynamic/.dynstr may not have PF_X)
                loads.add(LoadSeg(pOffset, pVaddr, pMemsz))
            }
        }
        if (loads.isEmpty()) return

        var strtab = 0L
        var strsz = 0L
        var runpathStrOffset = -1L

        // Locate PT_DYNAMIC and parse its entries
        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0 || phOff.toInt() + 56 > data.size) return
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (pType != 2) continue // PT_DYNAMIC

            val pVaddr = ByteBuffer.wrap(data, phOff.toInt() + 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val pFilesz = ByteBuffer.wrap(data, phOff.toInt() + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long

            // Convert vaddr to file offset using the first executable PT_LOAD
            val load = loads.firstOrNull { pVaddr in it.pVaddr until (it.pVaddr + it.pMemsz) }
                ?: return
            val dynFileOff = load.pOffset + (pVaddr - load.pVaddr)

            if (dynFileOff.toInt() + 8 > data.size || dynFileOff + pFilesz > data.size) return

            var dynOff = dynFileOff
            while (dynOff < dynFileOff + pFilesz) {
                val idx = dynOff.toInt()
                val dTag = ByteBuffer.wrap(data, idx, 8).order(ByteOrder.LITTLE_ENDIAN).long
                val dVal = ByteBuffer.wrap(data, idx + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long

                when (dTag) {
                    5L  -> strtab = dVal           // DT_STRTAB
                    10L -> strsz = dVal            // DT_STRSZ
                    15L -> runpathStrOffset = dVal // DT_RPATH (legacy)
                    29L -> runpathStrOffset = dVal // DT_RUNPATH
                }

                if (dTag == 0L) break // DT_NULL marks end
                dynOff += 24 // sizeof(Elf64_Dyn)
            }
            break
        }

        if (runpathStrOffset < 0L || strtab == 0L) {
            Log.w(TAG, "No DT_RUNPATH/DT_RPATH in ${elfFile.name} — fallback to LD_LIBRARY_PATH")
            return
        }

        // strtab is also a vaddr — convert to file offset
        val strLoad = loads.firstOrNull { strtab in it.pVaddr until (it.pVaddr + it.pMemsz) }
            ?: return
        val strFileOff = strLoad.pOffset + (strtab - strLoad.pVaddr)
        val runpathFilePos = strFileOff + runpathStrOffset

        if (runpathFilePos.toInt() + 1 >= data.size) return

        var endPos = -1
        for (j in runpathFilePos.toInt() until data.size) {
            if (data[j] == 0.toByte()) { endPos = j; break }
        }
        val oldLen = if (endPos >= 0) endPos - runpathFilePos.toInt() else data.size - runpathFilePos.toInt()

        if (libAbsPath.length > oldLen) {
            Log.w(TAG, "Cannot patch ${elfFile.name}: new RUNPATH (${libAbsPath.length}) longer than old ($oldLen)")
            return
        }

        val oldPath = data.copyOfRange(runpathFilePos.toInt(), runpathFilePos.toInt() + oldLen).decodeToString()
        val newBytes = libAbsPath.encodeToByteArray()
        newBytes.copyInto(data, runpathFilePos.toInt())
        // Null-pad remaining bytes
        data.fill(0, runpathFilePos.toInt() + newBytes.size, runpathFilePos.toInt() + oldLen)
        if (endPos >= 0) data[runpathFilePos.toInt() + newBytes.size] = 0

        elfFile.writeBytes(data)
        Log.i(TAG, "Patched DT_RUNPATH in ${elfFile.name}: '$oldPath' -> '$libAbsPath'")
    }

    /**
     * Adds an ANDROID_LD_LIBRARY_PATH ELF note (NT_ANDROID_LD_LIBRARY_PATH,
     * type 0x40000000, name "L") to the given 64-bit ELF binary. This note
     * signals to the Android dynamic linker that LD_LIBRARY_PATH from the
     * process environment should be honoured when resolving DT_NEEDED
     * entries, even for non-system PIE executables.
     *
     * The note requires 16 bytes: 12-byte Elf64_Nhdr + 4-byte padded name
     * "L\0". We append it after the last existing note in the PT_NOTE
     * segment. If the segment does not exist or the space is insufficient we
     * log a warning and skip — the binary may still work via DT_RUNPATH or
     * direct exec.
     */
    private fun addAndroidNote(elfFile: File) {
        if (!elfFile.isFile) return
        val data = elfFile.readBytes()
        if (data.size < 64) return

        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return
        if (data[4] != 2.toByte()) return // 64-bit only

        val phoff = ByteBuffer.wrap(data, 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val phentsize = ByteBuffer.wrap(data, 54, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val phnum = ByteBuffer.wrap(data, 56, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (phnum == 0 || phentsize == 0) return

        // Step 1: find PT_NOTE segment
        var noteOff = -1L
        var noteFilesz = 0L

        for (i in 0 until phnum) {
            val phOff = phoff + i * phentsize
            if (phOff < 0 || phOff.toInt() + 56 > data.size) return
            val pType = ByteBuffer.wrap(data, phOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (pType != 4) continue // PT_NOTE

            noteOff = ByteBuffer.wrap(data, phOff.toInt() + 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
            noteFilesz = ByteBuffer.wrap(data, phOff.toInt() + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
            break
        }

        if (noteOff < 0L) {
            Log.w(TAG, "No PT_NOTE in ${elfFile.name} — cannot add ANDROID note")
            return
        }

        // Step 2: walk existing notes to find the end
        var notesEnd = noteOff
        while (notesEnd < noteOff + noteFilesz) {
            val idx = notesEnd.toInt()
            if (idx + 12 > data.size) break
            val nNamesz = ByteBuffer.wrap(data, idx, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val nDescsz = ByteBuffer.wrap(data, idx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (nNamesz <= 0 || nNamesz > 256 || nDescsz < 0 || nDescsz > 65536) break

            val namePadded = ((nNamesz + 3) and 0x7FFFFFFC).toLong()
            val descPadded = ((nDescsz + 3) and 0x7FFFFFFC).toLong()
            val noteSize = 12L + namePadded + descPadded

            if (idx + noteSize > data.size) break
            notesEnd += noteSize
            if (notesEnd >= noteOff + noteFilesz) break
        }

        // Step 3: check for room (16 bytes: 12 header + 4 padded name "L\0")
        if (notesEnd + 16 > noteOff + noteFilesz) {
            Log.w(TAG, "Not enough space in PT_NOTE segment of ${elfFile.name} " +
                       "(${noteOff + noteFilesz - notesEnd} bytes free, need 16)")
            return
        }

        // Step 4: write NT_ANDROID_LD_LIBRARY_PATH note
        val pos = notesEnd.toInt()
        val bb = ByteBuffer.wrap(data, pos, data.size - pos).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(2)          // n_namesz = 2 ("L\0")
        bb.putInt(0)          // n_descsz = 0
        bb.putInt(0x40000000) // n_type = NT_ANDROID_LD_LIBRARY_PATH
        data[pos + 12] = 'L'.code.toByte()
        data[pos + 13] = 0   // null terminator
        data[pos + 14] = 0   // padding
        data[pos + 15] = 0   // padding

        elfFile.writeBytes(data)
        Log.i(TAG, "Added ANDROID_LD_LIBRARY_PATH note to ${elfFile.name}")
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