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
        private const val TIMEOUT_SECONDS = 300L
        private const val PROGRESS_INTERVAL_BYTES = 50_000L // report progress every ~50KB
        private const val USER_AGENT = "IRIS-Android/1.0"
    }

    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Application-scoped — survives ViewModel lifecycle
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

        _downloadStates.update { it + (modelId to DownloadState.Downloading(0f, 0, 0)) }

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
        Log.d(TAG, "Downloading: $downloadUrl")

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, error)
                onProgress(DownloadState.Error(error))
                return
            }

            val body = response.body ?: run {
                onProgress(DownloadState.Error("Empty response body"))
                return
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = targetFile.outputStream()

            val buffer = ByteArray(8192)
            var bytesRead: Long = 0
            var lastReportedPct = -1
            var lastReportedBytes: Long = 0

            try {
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    if (!currentCoroutineContext().isActive) {
                        Log.d(TAG, "Download cancelled for ${model.id}")
                        return
                    }

                    outputStream.write(buffer, 0, read)
                    bytesRead += read

                    val bytesSinceLastReport = bytesRead - lastReportedBytes
                    val shouldReport = if (contentLength > 0) {
                        val pct = (bytesRead * 100 / contentLength).toInt()
                        pct > lastReportedPct || bytesSinceLastReport >= PROGRESS_INTERVAL_BYTES
                    } else {
                        bytesSinceLastReport >= PROGRESS_INTERVAL_BYTES
                    }

                    if (shouldReport) {
                        if (contentLength > 0) {
                            lastReportedPct = (bytesRead * 100 / contentLength).toInt()
                        }
                        lastReportedBytes = bytesRead
                        onProgress(
                            DownloadState.Downloading(
                                if (contentLength > 0) (bytesRead.toFloat() / contentLength * 100f).coerceAtMost(100f)
                                else -1f,
                                bytesRead,
                                contentLength
                            )
                        )
                    }
                }
                outputStream.flush()
                Log.d(TAG, "Download complete: ${targetFile.absolutePath}")
                onProgress(DownloadState.Ready)
            } catch (e: IOException) {
                targetFile.delete()
                Log.e(TAG, "Download error", e)
                onProgress(DownloadState.Error(e.message ?: "Download interrupted"))
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: IOException) {
            targetFile.delete()
            Log.e(TAG, "Download error", e)
            onProgress(DownloadState.Error(e.message ?: "Download failed"))
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
