package com.iris.assistant.data.remote.local

import android.content.Context
import android.util.Log
import com.iris.assistant.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun modelsDir(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(model: LocalModelInfo): File =
        File(modelsDir(), model.hfFilename)

    fun isDownloaded(model: LocalModelInfo): Boolean =
        getModelFile(model).exists()

    suspend fun download(
        model: LocalModelInfo,
        onProgress: (DownloadState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        onProgress(DownloadState.Downloading(0f, 0, 0))

        val targetFile = getModelFile(model)
        if (targetFile.exists()) {
            Log.d(TAG, "${model.displayName} zaten indirilmiş")
            onProgress(DownloadState.Ready)
            return@withContext Result.success(targetFile)
        }

        val downloadUrl = "https://huggingface.co/${model.hfRepoId}/resolve/main/${model.hfFilename}"
        Log.d(TAG, "İndirme başlıyor: $downloadUrl")

        val request = Request.Builder().url(downloadUrl).build()

        try {
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, error)
                onProgress(DownloadState.Error(error))
                return@withContext Result.failure(IOException(error))
            }

            val body = response.body ?: run {
                onProgress(DownloadState.Error("Boş yanıt"))
                return@withContext Result.failure(IOException("Empty response body"))
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = targetFile.outputStream()

            val buffer = ByteArray(8192)
            var bytesRead: Long = 0
            var lastReportedProgress = -1f

            try {
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read

                    if (contentLength > 0) {
                        val progress = (bytesRead.toFloat() / contentLength * 100f).coerceAtMost(100f)
                        if (progress.toInt() != lastReportedProgress.toInt()) {
                            lastReportedProgress = progress
                            onProgress(
                                DownloadState.Downloading(progress, bytesRead, contentLength)
                            )
                        }
                    }
                }
                outputStream.flush()
                Log.d(TAG, "İndirme tamamlandı: ${targetFile.absolutePath}")
                onProgress(DownloadState.Ready)
                Result.success(targetFile)
            } catch (e: IOException) {
                targetFile.delete()
                Log.e(TAG, "İndirme hatası", e)
                onProgress(DownloadState.Error(e.message ?: "İndirme kesildi"))
                Result.failure(e)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: IOException) {
            targetFile.delete()
            Log.e(TAG, "İndirme hatası", e)
            onProgress(DownloadState.Error(e.message ?: "İndirme başarısız"))
            Result.failure(e)
        }
    }

    fun deleteModel(model: LocalModelInfo): Boolean {
        val file = getModelFile(model)
        return if (file.exists()) file.delete() else false
    }

    fun getModelSizeBytes(model: LocalModelInfo): Long {
        val file = getModelFile(model)
        return if (file.exists()) file.length() else 0L
    }
}
