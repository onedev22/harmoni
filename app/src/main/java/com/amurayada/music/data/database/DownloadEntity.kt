package com.amurayada.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val youtubeUrl: String,
    val localPath: String?,
    val thumbnailUrl: String?,
    val canvasUrl: String? = null,
    val canvasThumbUrl: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0
)
