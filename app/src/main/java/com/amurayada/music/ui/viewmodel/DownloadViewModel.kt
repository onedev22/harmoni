package com.amurayada.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amurayada.music.data.database.DownloadDatabase
import com.amurayada.music.data.model.DownloadProgress
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.DownloadRepositoryImpl
import com.amurayada.music.data.repository.StreamRepositoryImpl
import com.amurayada.music.worker.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val downloadDao = DownloadDatabase.getDatabase(application).downloadDao()
    private val streamRepository = StreamRepositoryImpl()
    private val downloadRepository = DownloadRepositoryImpl(application, downloadDao, streamRepository)
    private val context = application
    
    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()
    
    private val _activeDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadProgress>> = _activeDownloads.asStateFlow()
    
    init {
        observeDownloadedSongs()
        observeActiveDownloads()
    }
    
    private fun observeDownloadedSongs() {
        viewModelScope.launch {
            downloadRepository.observeDownloadedSongs().collect { songs ->
                _downloadedSongs.value = songs
            }
        }
    }
    
    private fun observeActiveDownloads() {
        viewModelScope.launch {
            downloadRepository.getActiveDownloads().collect { downloads ->
                _activeDownloads.value = downloads
            }
        }
    }
    
    private val _showDownloadLocationDialog = MutableStateFlow(false)
    val showDownloadLocationDialog: StateFlow<Boolean> = _showDownloadLocationDialog.asStateFlow()
    
    private val pendingSongs = mutableListOf<Song>()
    
    fun setDownloadLocation(mode: String, uri: String?) {
        val prefs = context.getSharedPreferences("music_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("download_path_mode", mode)
            putString("download_path_uri", uri)
            apply()
        }
        _showDownloadLocationDialog.value = false
        
        // Process pending
        if (pendingSongs.isNotEmpty()) {
            val songsToDownload = pendingSongs.toList()
            pendingSongs.clear()
            processDownloads(songsToDownload)
        }
    }
    
    fun dismissDialog() {
        _showDownloadLocationDialog.value = false
        pendingSongs.clear()
    }
    
    fun requestChangeLocation() {
        _showDownloadLocationDialog.value = true
    }

    fun downloadSong(song: Song) {
        checkAndDownload(listOf(song))
    }

    fun downloadAlbum(songs: List<Song>) {
        checkAndDownload(songs)
    }
    
    private fun checkAndDownload(songs: List<Song>) {
        val prefs = context.getSharedPreferences("music_settings", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getString("download_path_mode", null)
        
        if (mode == null) {
            // First time - ask user
            pendingSongs.addAll(songs)
            _showDownloadLocationDialog.value = true
        } else {
            // Already configured
            processDownloads(songs)
        }
    }
    
    private fun processDownloads(songs: List<Song>) {
        // Calculate path logic is now in Repository/Worker based on Prefs
        // But we still need to pass metadata to worker
        // 'path' in Song here is the Source URL
        songs.forEach { song ->
            enqueueWorker(song)
        }
    }
    
    private fun enqueueWorker(song: Song) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "songId" to song.id,
                    "title" to song.title,
                    "artist" to song.artist,
                    "album" to song.album,
                    "duration" to song.duration,
                    "path" to song.path,
                    "albumArtUri" to song.albumArtUri?.toString()
                )
            )
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
    
    fun cancelDownload(songId: Long) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(songId)
        }
    }
    
    fun deleteDownload(songId: Long) {
        viewModelScope.launch {
            try {
                downloadRepository.deleteDownload(songId)
                // No need to reload, observeDownloadedSongs() will auto-update
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun isDownloaded(songId: Long): Flow<Boolean> = flow {
        emit(downloadRepository.isDownloaded(songId))
    }
    
    fun getDownloadProgress(songId: Long): Flow<DownloadProgress?> {
        return downloadRepository.getDownloadProgress(songId)
    }
}
