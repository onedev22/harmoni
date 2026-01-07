package com.amurayada.music.data.model

data class DownloadProgress(
    val songId: Long,
    val progress: Float, // 0.0 to 1.0
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
