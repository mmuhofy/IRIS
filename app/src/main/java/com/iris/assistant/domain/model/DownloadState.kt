package com.iris.assistant.domain.model

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Connecting : DownloadState()
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    data object Ready : DownloadState()
    data class Error(val message: String) : DownloadState()
}
