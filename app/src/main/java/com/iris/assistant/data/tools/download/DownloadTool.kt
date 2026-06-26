package com.iris.assistant.data.tools.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import com.iris.assistant.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadTool"

// UNTESTED — verify download behavior on device before use

@Singleton
class DownloadTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : JarvisTool {

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override val name = "download_file"
    override val description = "Downloads a file from the specified URL and saves it to Downloads/IrisDownloads/ folder. Supports any file type (images, videos, documents, ZIPs, APKs, etc.)."
    override val parameters = JSONObject("""
        {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "Direct download URL of the file"
                },
                "filename": {
                    "type": "string",
                    "description": "Optional custom filename with extension (e.g. document.pdf). If omitted, extracted from URL or Content-Disposition header."
                }
            },
            "required": ["url"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val url = args.optString("url").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("İndirme linki belirtilmedi.")

        val customFilename = args.optString("filename").takeIf { it.isNotBlank() }
        if (customFilename != null && customFilename.contains("/")) {
            return ToolResult.Error("Dosya adı '/' içermemeli.")
        }

        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val writePermission = "android.permission.WRITE_EXTERNAL_STORAGE"
                val permissionGranted = try {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, writePermission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) { false }
                if (!permissionGranted) {
                    return@runCatching ToolResult.PermissionRequired(
                        permission = writePermission,
                        rationale = "Dosya indirmek için depolama izni gerekiyor."
                    )
                }
            }

            val request = Request.Builder().url(url).get().build()
            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@runCatching ToolResult.Error("Sunucu HTTP ${response.code} döndü.")
            }

            val body = response.body
                ?: return@runCatching ToolResult.Error("Yanıt gövdesi boş.")

            val filename = customFilename ?: resolveFilename(response, url)
            val safeFilename = sanitizeFilename(filename)

            val downloadedFile = saveToIrisDownloads(body.byteStream(), safeFilename, response)

            val fileSize = downloadedFile.length()
            val sizeText = formatFileSize(fileSize)

            ToolResult.Success(
                displayText = "Dosya indirildi: $safeFilename ($sizeText). İndirilenler/IrisDownloads/ klasörüne kaydedildi.",
                data = mapOf(
                    "filename" to safeFilename,
                    "size_bytes" to fileSize.toString(),
                    "size_display" to sizeText,
                    "path" to downloadedFile.absolutePath
                )
            )
        }.getOrElse { e ->
            when (e) {
                is java.net.SocketTimeoutException, is java.net.UnknownHostException, is java.net.ConnectException ->
                    ToolResult.Error("İndirme başarısız: İnternet bağlantısı veya sunucu sorunu: ${e.message}")
                is java.io.FileNotFoundException ->
                    ToolResult.Error("Dosya bulunamadı veya erişim reddedildi: ${e.message}")
                is java.io.IOException ->
                    ToolResult.Error("Dosya indirilemedi: ${e.message}")
                else ->
                    ToolResult.Error("Beklenmeyen hata: ${e.message}", e)
            }
        }
    }

    private fun resolveFilename(response: okhttp3.Response, url: String): String {
        val disposition = response.header("Content-Disposition")
        if (disposition != null) {
            val parts = disposition.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("filename*=utf-8''", ignoreCase = true)) {
                    val encoded = trimmed.substringAfter("filename*=utf-8''")
                        .trim('"', '\'')
                    if (encoded.isNotBlank()) {
                        return try {
                            java.net.URLDecoder.decode(encoded, "UTF-8")
                        } catch (_: Exception) { encoded }
                    }
                }
                if (trimmed.startsWith("filename=", ignoreCase = true)) {
                    val fn = trimmed.substringAfter("filename=")
                        .trim('"', '\'')
                    if (fn.isNotBlank()) return fn
                }
            }
        }

        return runCatching {
            val path = URL(url).path
            val name = path.substringAfterLast('/')
            if (name.isNotBlank() && !name.contains("?")) name else "download"
        }.getOrElse { "download" }
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .take(200)
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.ifBlank { "download" }
    }

    @Suppress("DEPRECATION")
    private fun saveToIrisDownloads(
        inputStream: java.io.InputStream,
        filename: String,
        response: okhttp3.Response
    ): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            saveUsingMediaStore(inputStream, filename, response)
        } else {
            saveToLegacyStorage(inputStream, filename)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyStorage(inputStream: java.io.InputStream, filename: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val irisDownloads = File(downloadsDir, Constants.IRIS_DOWNLOADS_DIR)
        irisDownloads.mkdirs()

        val file = File(irisDownloads, filename)
        FileOutputStream(file).use { output ->
            inputStream.use { it.copyTo(output, bufferSize = 8192) }
        }
        return file
    }

    private fun saveUsingMediaStore(
        inputStream: java.io.InputStream,
        filename: String,
        response: okhttp3.Response
    ): File {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.RELATIVE_PATH, Constants.IRIS_DOWNLOADS_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
            val mimeType = response.header("Content-Type")
            if (mimeType != null) {
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw java.io.IOException("MediaStore'a kayıt eklenemedi.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                inputStream.use { it.copyTo(output, bufferSize = 8192) }
            } ?: throw java.io.IOException("Çıktı akışı açılamadı.")

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        @Suppress("DEPRECATION")
        fun fallbackPath(): File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "${Constants.IRIS_DOWNLOADS_DIR}/$filename"
        )

        return fallbackPath()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
            else                    -> "$bytes B"
        }
    }
}
