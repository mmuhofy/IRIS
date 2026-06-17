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
) {
    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODELS_DIR = "iris_models"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
        private const val USER_AGENT = "IRIS-Android/1.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun modelsDir(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(model: LocalModelInfo): File = File(modelsDir(), model.hfFilename)

    fun isDownloaded(model: LocalModelInfo): Boolean = getModelFile(model).exists()

    fun startDownload(modelId: String) {
        val model = LocalModelManifest.models.find { it.id == modelId } ?: return
        if (activeJobs[modelId]?.isActive == true) return

        _downloadStates.update { it + (modelId to DownloadState.Connecting) }

        val job = scope.launch {
            download(model)
        }
        job.invokeOnCompletion {
            if (!activeJobs.containsKey(modelId)) return@invokeOnCompletion
            activeJobs.remove(modelId)
        }
        activeJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs[modelId]?.cancel()
        activeJobs.remove(modelId)
        _downloadStates.update { it + (modelId to DownloadState.Idle) }
    }

    private suspend fun download(model: LocalModelInfo) {
        val modelId = model.id
        val targetFile = getModelFile(model)
        if (targetFile.exists()) {
            Log.d(TAG, "${model.displayName} already downloaded")
            _downloadStates.update { it + (modelId to DownloadState.Ready) }
            return
        }

        val url = "https://huggingface.co/${model.hfRepoId}/resolve/main/${model.hfFilename}?download=1"
        val estimatedBytes = model.sizeMb.toLong() * 1_000_000L
        Log.d(TAG, "Downloading $url (est: ${estimatedBytes}B)")

        try {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code}: ${response.message}")
                _downloadStates.update {
                    it + (modelId to DownloadState.Error(
                        "Sunucu hatası (HTTP ${response.code})"
                    ))
                }
                return
            }

            val body = response.body ?: run {
                _downloadStates.update { it + (modelId to DownloadState.Error("Boş yanıt")) }
                return
            }

            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0L) contentLength else estimatedBytes

            val input = body.byteStream()
            val output = targetFile.outputStream()
            val buf = ByteArray(8192)

            var read: Long = 0
            var lastPct = -1
            var lastBytes: Long = 0
            var lastMs = System.currentTimeMillis()
            var firstChunk = true

            try {
                while (true) {
                    if (!currentCoroutineContext().isActive) {
                        targetFile.delete()
                        Log.d(TAG, "Download cancelled: $modelId")
                        _downloadStates.update { it + (modelId to DownloadState.Idle) }
                        return
                    }

                    val n = input.read(buf)
                    if (n == -1) break

                    output.write(buf, 0, n)
                    read += n

                    if (firstChunk) {
                        firstChunk = false
                        Log.d(TAG, "First chunk received for $modelId")
                    }

                    val now = System.currentTimeMillis()
                    val pct = (read * 100 / totalBytes).toInt()
                    val bytesSince = read - lastBytes
                    val msSince = now - lastMs

                    if (pct > lastPct || bytesSince >= 8_192 || msSince >= 250L) {
                        lastPct = pct
                        lastBytes = read
                        lastMs = now
                        _downloadStates.update {
                            it + (modelId to DownloadState.Downloading(
                                progress = (read.toFloat() / totalBytes * 100f).coerceAtMost(100f),
                                bytesDownloaded = read,
                                totalBytes = totalBytes
                            ))
                        }
                    }
                }

                output.flush()
                Log.d(TAG, "Downloaded: ${targetFile.absolutePath} ($read bytes)")
                _downloadStates.update { it + (modelId to DownloadState.Ready) }
            } catch (e: IOException) {
                targetFile.delete()
                Log.e(TAG, "Download transfer error", e)
                _downloadStates.update {
                    it + (modelId to DownloadState.Error("İndirme kesildi: ${e.message}"))
                }
            } finally {
                try { input.close() } catch (_: Exception) {}
                try { output.close() } catch (_: Exception) {}
            }

        } catch (e: IOException) {
            targetFile.delete()
            Log.e(TAG, "Download network error", e)
            _downloadStates.update {
                it + (modelId to DownloadState.Error(
                    "Bağlantı hatası: ${e.message ?: "Sunucuya ulaşılamadı"}"
                ))
            }
        }
    }

    fun deleteModel(model: LocalModelInfo): Boolean {
        cancelDownload(model.id)
        return getModelFile(model).delete()
    }

    fun getModelSizeBytes(model: LocalModelInfo): Long {
        val f = getModelFile(model)
        return if (f.exists()) f.length() else 0L
    }
}
