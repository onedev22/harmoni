package com.amurayada.music.data.repository

import com.amurayada.music.data.model.DownloadProgress
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    /**
     * Download a song for offline playback
     */
    suspend fun downloadSong(song: Song): Flow<DownloadProgress>
    
    /**
     * Cancel an ongoing download
     */
    suspend fun cancelDownload(songId: Long)
    
    /**
     * Get all downloaded songs
     */
    suspend fun getDownloadedSongs(): List<Song>
    
    /**
     * Observe downloaded songs (reactive)
     */
    fun observeDownloadedSongs(): Flow<List<Song>>
    
    /**
     * Delete a downloaded song
     */
    suspend fun deleteDownload(songId: Long)
    
    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(songId: Long): Boolean
    
    /**
     * Get download progress for a specific song
     */
    fun getDownloadProgress(songId: Long): Flow<DownloadProgress?>
    
    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): Flow<List<DownloadProgress>>

    /**
     * Verifies if downloaded files still exist on storage and cleans up database if not.
     */
    suspend fun validateDownloads()
}
