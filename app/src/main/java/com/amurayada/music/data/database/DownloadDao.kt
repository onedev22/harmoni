package com.amurayada.music.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun getDownload(songId: Long): DownloadEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE songId = :songId)")
    suspend fun isDownloaded(songId: Long): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)
    
    @Delete
    suspend fun deleteDownload(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteDownloadById(songId: Long)
    
    @Query("DELETE FROM downloads")
    suspend fun deleteAllDownloads()
    
    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun getDownloadCount(): Int
    
    @Query("SELECT SUM(fileSize) FROM downloads")
    suspend fun getTotalSize(): Long?
}
