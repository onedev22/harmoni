package com.amurayada.music.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.amurayada.music.R
import com.amurayada.music.data.database.DownloadDatabase
import com.amurayada.music.data.model.DownloadStatus
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.DownloadRepositoryImpl
import com.amurayada.music.data.repository.StreamRepositoryImpl
import kotlinx.coroutines.flow.collect

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    override suspend fun doWork(): Result {
        val songId = inputData.getLong("songId", -1)
        val title = inputData.getString("title") ?: "Unknown"
        val artist = inputData.getString("artist") ?: "Unknown"
        val album = inputData.getString("album") ?: ""
        val duration = inputData.getLong("duration", 0)
        val path = inputData.getString("path") ?: return Result.failure()
        val albumArtUri = inputData.getString("albumArtUri")
        
        if (songId == -1L) {
            return Result.failure()
        }
        
        val song = Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            albumArtUri = albumArtUri?.let { android.net.Uri.parse(it) },
            path = path,
            dateAdded = System.currentTimeMillis(),
            albumId = 0
        )
        
        createNotificationChannel()
        val notificationId = songId.toInt()
        setForeground(createForegroundInfo(title, 0, notificationId))
        
        return try {
            val database = DownloadDatabase.getDatabase(applicationContext)
            val streamRepository = StreamRepositoryImpl()
            val downloadRepository = DownloadRepositoryImpl(
                applicationContext,
                database.downloadDao(),
                streamRepository
            )
            
            downloadRepository.downloadSong(song).collect { progress ->
                when (progress.status) {
                    DownloadStatus.DOWNLOADING -> {
                        setForeground(createForegroundInfo(title, (progress.progress * 100).toInt(), notificationId))
                    }
                    DownloadStatus.COMPLETED -> {
                        // Fetch lyrics immediately for offline availability
                        try {
                            val lyricsRepository = com.amurayada.music.data.repository.LyricsRepository(applicationContext)
                            lyricsRepository.getLyrics(song) 
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showCompletionNotification(title, notificationId)
                    }
                    DownloadStatus.FAILED -> {
                        showErrorNotification(title, progress.error, notificationId)
                    }
                    else -> {}
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorNotification(title, e.message, notificationId ?: title.hashCode())
            Result.failure(workDataOf("error" to e.message))
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Song download notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundInfo(title: String, progress: Int, notificationId: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setProgress(100, progress, progress == 0)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                notificationId, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        return ForegroundInfo(notificationId, notification)
    }
    
    private fun showCompletionNotification(title: String, notificationId: Int) {
        // Use a different ID so WorkManager doesn't cancel it when the worker finishes
        // WorkManager automatically cancels the foreground notification (notificationId)
        val completionId = notificationId + 14321 
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false) 
            .setProgress(0, 0, false)
            .build()
        
        notificationManager.notify(completionId, notification)
    }
    
    private fun showErrorNotification(title: String, error: String?, notificationId: Int) {
        // Use a different ID so WorkManager doesn't cancel it
        val errorId = notificationId + 14321
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download failed")
            .setContentText("$title: ${error ?: "Unknown error"}")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .build()
        
        notificationManager.notify(errorId, notification)
    }
    
    companion object {
        private const val CHANNEL_ID = "download_channel"
        // NOTIFICATION_ID removed in favor of dynamic IDs
    }
}
