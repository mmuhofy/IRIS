package com.iris.assistant.data.remote.local

import android.content.Context
import android.util.Log
import com.iris.assistant.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODELS_DIR = "iris_models"
        private const val TIMEOUT_CONNECT_SECONDS = 60L
        private const val TIMEOUT_READ_SECONDS = 120L
        private const val USER_AGENT = "IRIS-Android/1.0"
    }

    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_CONNECT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_READ_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_CONNECT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeJobs = mutableMapOf<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun modelsDir(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(model: LocalModelInfo): File =
        File(modelsDir(), model.hfFilename)

    fun isDownloaded(model: LocalModelInfo): Boolean =
        getModelFile(model).exists()

    fun startDownload(modelId: String) {
        val model = LocalModelManifest.models.find { it.id == modelId } ?: return
        if (activeJobs[modelId]?.isActive == true) return

        _downloadStates.update {
            it + (modelId to DownloadState.Downloading(
                progress = 0f,
                bytesDownloaded = 0,
                totalBytes = model.sizeMb * 1_000_000L
            ))
        }

        val job = downloadScope.launch {
            download(model) { state ->
                _downloadStates.update { it + (modelId to state) }
            }
            activeJobs.remove(modelId)
        }
        activeJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs[modelId]?.cancel()
        activeJobs.remove(modelId)
        _downloadStates.update { it + (modelId to DownloadState.Idle) }
    }

    private suspend fun download(
        model: LocalModelInfo,
        onProgress: (DownloadState) -> Unit
    ) {
        val targetFile = getModelFile(model)
        if (targetFile.exists()) {
            Log.d(TAG, "${model.displayName} already downloaded")
            onProgress(DownloadState.Ready)
            return
        }

        val downloadUrl = "https://huggingface.co/${model.hfRepoId}/resolve/main/${model.hfFilename}"
        val estimatedTotalBytes = model.sizeMb * 1_000_000L
        Log.d(TAG, "Downloading: $downloadUrl (estimated: ${estimatedTotalBytes}B)")

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = try {
                    response.body?.string()?.take(200) ?: ""
                } catch (_: Exception) { "" }
                val error = "HTTP ${response.code}: ${response.message}. $errorBody"
                Log.e(TAG, "Download error: $error")
                onProgress(DownloadState.Error(error))
                return
            }

            val body = response.body ?: run {
                onProgress(DownloadState.Error("Sunucudan boş yanıt alındı"))
                return
            }

            val contentLength = body.contentLength()
            val actualTotalBytes = if (contentLength > 0L) contentLength else estimatedTotalBytes
            if (contentLength > 0L) {
                Log.d(TAG, "Content-Length: $contentLength bytes (${contentLength / 1_000_000} MB)")
            } else {
                Log.d(TAG, "Content-Length unknown, using estimate: ${estimatedTotalBytes}B")
            }

            val inputStream = body.byteStream()
            val outputStream = targetFile.outputStream()

            val buffer = ByteArray(8192)
            var bytesRead: Long = 0
            var lastReportedPct = -1
            var lastReportedBytes: Long = 0
            var lastReportedTimeMs = 0L

            try {
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    if (!currentCoroutineContext().isActive) {
                        targetFile.delete()
                        Log.d(TAG, "Download cancelled for ${model.id}")
                        return
                    }

                    outputStream.write(buffer, 0, read)
                    bytesRead += read

                    val now = System.currentTimeMillis()
                    val bytesSinceReport = bytesRead - lastReportedBytes
                    val timeSinceReport = now - lastReportedTimeMs

                    val pct = (bytesRead * 100 / actualTotalBytes).toInt()
                    val shouldReport = pct > lastReportedPct
                        || bytesSinceReport >= 8_192
                        || timeSinceReport >= 250L

                    if (shouldReport) {
                        lastReportedPct = pct
                        lastReportedBytes = bytesRead
                        lastReportedTimeMs = now
                        val progress = (bytesRead.toFloat() / actualTotalBytes * 100f)
                            .coerceAtMost(100f)
                        onProgress(
                            DownloadState.Downloading(
                                progress = progress,
                                bytesDownloaded = bytesRead,
                                totalBytes = actualTotalBytes
                            )
                        )
                    }
                }
                outputStream.flush()
                Log.d(TAG, "Download complete: ${targetFile.absolutePath} (${bytesRead} bytes)")
                onProgress(DownloadState.Ready)
            } catch (e: IOException) {
                targetFile.delete()
                Log.e(TAG, "Download error during transfer", e)
                onProgress(DownloadState.Error(e.message ?: "İndirme sırasında hata oluştu"))
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
                try { outputStream.close() } catch (_: Exception) {}
            }
        } catch (e: IOException) {
            targetFile.delete()
            Log.e(TAG, "Download error (network)", e)
            onProgress(DownloadState.Error(e.message ?: "İndirme başlatılamadı"))
        }
    }

    fun deleteModel(model: LocalModelInfo): Boolean {
        cancelDownload(model.id)
        val file = getModelFile(model)
        return if (file.exists()) file.delete() else false
    }

    fun getModelSizeBytes(model: LocalModelInfo): Long {
        val file = getModelFile(model)
        return if (file.exists()) file.length() else 0L
    }
}
