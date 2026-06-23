package com.iris.assistant.domain.model

/**
 * Represents the lifecycle of the Termux bootstrap installation.
 *
 * State machine:
 *   Idle ──► Checking ──► Downloading ──► Extracting ──► Installed
 *                │              │               │
 *                └──────────────┴───────────────┴──► Error
 */
sealed interface BootstrapState {

    /** Bootstrap has never been started or was explicitly reset. */
    data object Idle : BootstrapState

    /** Fetching the latest release tag from GitHub API. */
    data object Checking : BootstrapState

    /**
     * Actively downloading the bootstrap zip.
     *
     * @param bytesDownloaded bytes received so far
     * @param totalBytes      content-length from server; -1 if unknown
     */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : BootstrapState {
        /** 0.0–1.0 progress fraction; null if total size is unknown. */
        val fraction: Float?
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else null
    }

    /** Extracting zip contents into the Termux prefix directory. */
    data object Extracting : BootstrapState

    /**
     * Bootstrap extracted; now running `apt install` for required packages (e.g. proot).
     *
     * @param packageName  name of the package currently being installed
     */
    data class InstallingPackages(
        val packageName: String,
    ) : BootstrapState

    /**
     * Bootstrap is fully installed and ready.
     *
     * @param prefixPath absolute path to the Termux prefix (`$filesDir/termux/usr`)
     * @param version    release tag that was installed (e.g. "bootstrap-2025.12.14-r1+apt-android-7")
     */
    data class Installed(
        val prefixPath: String,
        val version: String,
    ) : BootstrapState

    /**
     * A step failed.
     *
     * @param message    human-readable English description for logging
     * @param cause      original exception, if any
     * @param retryable  true if the caller may safely retry the entire install
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val retryable: Boolean = true,
    ) : BootstrapState
}