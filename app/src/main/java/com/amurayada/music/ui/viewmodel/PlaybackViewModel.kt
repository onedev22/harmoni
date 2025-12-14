package com.amurayada.music.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amurayada.music.data.model.PlaybackMode
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.RepeatMode
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.LyricsRepository
import com.amurayada.music.data.repository.LyricsResult
import com.amurayada.music.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    // SharedPreferences for persistence
    private val prefs = application.getSharedPreferences("playback_data", Context.MODE_PRIVATE)
    
    // Lyrics repository
    private val lyricsRepository = LyricsRepository(application)
    
    var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Idle)
        private set
    
    var currentSong by mutableStateOf<Song?>(null)
        private set
    
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    
    var currentPosition by mutableStateOf(0L)
        private set
    
    var playbackMode by mutableStateOf(PlaybackMode())
        private set
    
    // Favorites list - persisted
    private val _favorites = mutableStateListOf<Song>()
    val favorites: List<Song> get() = _favorites.toList()
    
    // Recently played (history) - persisted
    private val _recentlyPlayed = mutableStateListOf<Song>()
    val recentlyPlayed: List<Song> get() = _recentlyPlayed.toList()
    
    // Most played tracking - persisted
    private val playCountMap = mutableMapOf<Long, Int>()
    val mostPlayed: List<Song>
        get() = _recentlyPlayed
            .distinctBy { it.id }
            .sortedByDescending { playCountMap[it.id] ?: 0 }
            .take(20)
            
    // Event to expand player from other screens (e.g. widget)
    private val _expandPlayerEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val expandPlayerEvent = _expandPlayerEvent.asSharedFlow()
    
    fun expandPlayer() {
        viewModelScope.launch {
            _expandPlayerEvent.emit(Unit)
        }
    }
    
    init {
        loadPersistedData()
        initializeMediaController()
        startPositionUpdater()
    }
    
    private fun loadPersistedData() {
        // Load favorites
        val favoritesJson = prefs.getString("favorites", null)
        if (favoritesJson != null) {
            try {
                val jsonArray = JSONArray(favoritesJson)
                for (i in 0 until jsonArray.length()) {
                    val songJson = jsonArray.getJSONObject(i)
                    _favorites.add(songFromJson(songJson))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        // Load history
        val historyJson = prefs.getString("history", null)
        if (historyJson != null) {
            try {
                val jsonArray = JSONArray(historyJson)
                for (i in 0 until jsonArray.length()) {
                    val songJson = jsonArray.getJSONObject(i)
                    _recentlyPlayed.add(songFromJson(songJson))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        // Load play counts
        val playCountsJson = prefs.getString("play_counts", null)
        if (playCountsJson != null) {
            try {
                val jsonObject = JSONObject(playCountsJson)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    playCountMap[key.toLong()] = jsonObject.getInt(key)
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }
    
    private fun saveFavorites() {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                _favorites.take(100).forEach { song -> // Limit to 100
                    jsonArray.put(songToJson(song))
                }
                prefs.edit().putString("favorites", jsonArray.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }
    
    private fun saveHistory() {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                _recentlyPlayed.take(50).forEach { song -> // Limit to 50
                    jsonArray.put(songToJson(song))
                }
                prefs.edit().putString("history", jsonArray.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }
    
    private fun savePlayCounts() {
        viewModelScope.launch {
            try {
                val jsonObject = JSONObject()
                playCountMap.entries.take(100).forEach { (id, count) ->
                    jsonObject.put(id.toString(), count)
                }
                prefs.edit().putString("play_counts", jsonObject.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }
    
    private fun songToJson(song: Song): JSONObject {
        return JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("album", song.album)
            put("duration", song.duration)
            put("path", song.path)
            put("albumArtUri", song.albumArtUri?.toString() ?: "")
            put("dateAdded", song.dateAdded)
            put("albumId", song.albumId)
        }
    }
    
    private fun songFromJson(json: JSONObject): Song {
        val albumArtUriString = json.optString("albumArtUri", "")
        return Song(
            id = json.getLong("id"),
            title = json.getString("title"),
            artist = json.getString("artist"),
            album = json.getString("album"),
            duration = json.getLong("duration"),
            albumArtUri = if (albumArtUriString.isNotEmpty()) Uri.parse(albumArtUriString) else null,
            path = json.getString("path"),
            dateAdded = json.optLong("dateAdded", 0L),
            albumId = json.optLong("albumId", 0L)
        )
    }
    
    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicPlaybackService::class.java)
        )
        
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListener()
            syncWithController()
        }, MoreExecutors.directExecutor())
    }
    
    private fun syncWithController() {
        mediaController?.let { controller ->
            val item = controller.currentMediaItem
            if (item != null) {
                val metadata = item.mediaMetadata
                val song = Song(
                    id = item.mediaId.toLongOrNull() ?: 0L,
                    title = metadata.title?.toString() ?: "Unknown",
                    artist = metadata.artist?.toString() ?: "Unknown",
                    album = metadata.albumTitle?.toString() ?: "Unknown",
                    duration = controller.duration.takeIf { it > 0 } ?: 0L,
                    albumArtUri = metadata.artworkUri,
                    path = "", // Path not strictly needed for UI display
                    dateAdded = 0L,
                    albumId = 0L
                )
                currentSong = song
                loadLyrics(song.id)
                updatePlaybackState()
            }
        }
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }
            
            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Force update when track changes
                mediaController?.let { controller ->
                    // Reset position for the new track
                    currentPosition = 0L
                    // Try to get from queue first
                    val currentIndex = controller.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < queue.size) {
                        val newSong = queue[currentIndex]
                        currentSong = newSong
                        addToHistory(newSong)
                        playCountMap[newSong.id] = (playCountMap[newSong.id] ?: 0) + 1
                        savePlayCounts()
                        loadLyrics(newSong.id)
                        updatePlaybackState()
                    } else {
                        // Fallback to sync from controller if queue is empty (e.g. after restart)
                        syncWithController()
                    }
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Also update on position discontinuity (skip next/prev)
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || 
                    reason == Player.DISCONTINUITY_REASON_SEEK) {
                    mediaController?.let { controller ->
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < queue.size) {
                            val newSong = queue[currentIndex]
                            if (currentSong?.id != newSong.id) {
                                currentSong = newSong
                                addToHistory(newSong)
                                playCountMap[newSong.id] = (playCountMap[newSong.id] ?: 0) + 1
                                savePlayCounts()
                                loadLyrics(newSong.id)
                            }
                        } else {
                             syncWithController()
                        }
                    }
                }
            }
        })
    }
    
    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val song = currentSong ?: return
        
        playbackState = when {
            controller.isPlaying -> PlaybackState.Playing(song, controller.currentPosition)
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackState.Loading
            controller.playbackState == Player.STATE_READY -> PlaybackState.Paused(song, controller.currentPosition)
            else -> PlaybackState.Idle
        }
    }
    
    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    mediaController?.let { controller ->
                        if (controller.isPlaying) {
                            val currentMediaId = controller.currentMediaItem?.mediaId
                            if (currentMediaId == currentSong?.id?.toString()) {
                                currentPosition = controller.currentPosition
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore exceptions during position update
                }
                delay(500L)
            }
        }
    }
    
    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        currentSong = song
        queue = songList
        
        // Add to history and save
        addToHistory(song)
        
        // Increment play count and save
        playCountMap[song.id] = (playCountMap[song.id] ?: 0) + 1
        savePlayCounts()
        
        val startIndex = songList.indexOf(song).coerceAtLeast(0)
        val mediaItems = songList.map { createMediaItem(it) }
        
        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }
        
        playbackState = PlaybackState.Loading
        
        loadLyrics(song.id)
    }
    
    fun removeFromQueue(song: Song) {
        val index = queue.indexOfFirst { it.id == song.id }
        if (index != -1) {
            val newQueue = queue.toMutableList().apply { removeAt(index) }
            queue = newQueue
            
            // If removed current song, skip to next
            if (currentSong?.id == song.id) {
                if (newQueue.isNotEmpty()) {
                    val nextIndex = index.coerceAtMost(newQueue.lastIndex)
                    playSong(newQueue[nextIndex], newQueue)
                } else {
                    mediaController?.stop()
                }
            } else {
                // Update controller queue without interrupting playback
                // Note: This is a simplified approach. Ideally we'd use MediaController.removeMediaItem
                // But for now we just update the local list and let the user re-select if they want to play from the new queue
                // Or we can try to sync with controller if possible
                mediaController?.removeMediaItem(index)
            }
        }
    }
    
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= queue.size || toIndex >= queue.size) return
        
        val newQueue = queue.toMutableList()
        val item = newQueue.removeAt(fromIndex)
        newQueue.add(toIndex, item)
        queue = newQueue
        
        // Update controller
        mediaController?.moveMediaItem(fromIndex, toIndex)
    }

    private fun addToHistory(song: Song) {
        // Remove if already exists (to move it to front)
        _recentlyPlayed.removeAll { it.id == song.id }
        // Add to front
        _recentlyPlayed.add(0, song)
        // Keep only last 50 items
        while (_recentlyPlayed.size > 50) {
            _recentlyPlayed.removeLast()
        }
        // Save to persistence
        saveHistory()
    }
    
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }
    
    fun skipToNext() {
        mediaController?.seekToNext()
        updateCurrentSongFromController()
    }
    
    fun skipToPrevious() {
        mediaController?.seekToPrevious()
        updateCurrentSongFromController()
    }
    
    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        currentPosition = position
    }
    
    fun toggleShuffle() {
        val newMode = playbackMode.copy(isShuffleEnabled = !playbackMode.isShuffleEnabled)
        playbackMode = newMode
        mediaController?.shuffleModeEnabled = newMode.isShuffleEnabled
    }
    
    fun toggleRepeatMode() {
        val newRepeatMode = when (playbackMode.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackMode = playbackMode.copy(repeatMode = newRepeatMode)
        
        mediaController?.repeatMode = when (newRepeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }
    
    // Favorites functions - with persistence
    fun toggleFavorite(song: Song) {
        if (_favorites.any { it.id == song.id }) {
            _favorites.removeAll { it.id == song.id }
        } else {
            _favorites.add(0, song)
        }
        saveFavorites()
    }
    
    fun isFavorite(song: Song): Boolean {
        return _favorites.any { it.id == song.id }
    }
    
    fun clearHistory() {
        _recentlyPlayed.clear()
        saveHistory()
    }
    
    private fun updateCurrentSongFromController() {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex >= 0 && currentIndex < queue.size) {
                val newSong = queue[currentIndex]
                if (currentSong?.id != newSong.id) {
                    currentSong = newSong
                    addToHistory(newSong)
                    playCountMap[newSong.id] = (playCountMap[newSong.id] ?: 0) + 1
                    savePlayCounts()
                }
            } else {
                 syncWithController()
            }
        }
    }
    
    private fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()
        
        return MediaItem.Builder()
            .setUri(Uri.parse(song.path))
            .setMediaMetadata(metadata)
            .setMediaId(song.id.toString())
            .build()
    }
    
    // Sleep Timer
    var sleepTimerDuration by mutableStateOf<Long?>(null)
        private set
    var isSleepTimerRunning by mutableStateOf(false)
        private set
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // Lyrics
    var lyrics by mutableStateOf<String?>(null)
        private set
    
    var lyricsLoadingState by mutableStateOf<LyricsLoadingState>(LyricsLoadingState.Idle)
        private set

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val durationMillis = minutes * 60 * 1000L
        sleepTimerDuration = durationMillis
        isSleepTimerRunning = true
        
        sleepTimerJob = viewModelScope.launch {
            delay(durationMillis)
            mediaController?.pause()
            cancelSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        isSleepTimerRunning = false
        sleepTimerDuration = null
    }

    // Lyrics Management
    fun saveLyrics(songId: Long, content: String) {
        viewModelScope.launch {
            lyricsRepository.saveLyrics(songId, content)
            if (currentSong?.id == songId) {
                lyrics = content
                lyricsLoadingState = LyricsLoadingState.Success
            }
        }
    }
    
    fun importLrcFile(songId: Long, lrcContent: String) {
        viewModelScope.launch {
            val success = lyricsRepository.importLrcFile(songId, lrcContent)
            if (success && currentSong?.id == songId) {
                lyrics = lrcContent
                lyricsLoadingState = LyricsLoadingState.Success
            }
        }
    }
    
    fun openLyricsSearch(context: android.content.Context, song: Song) {
        val searchQuery = "${song.title} ${song.artist}".replace(" ", "%20")
        val url = "https://lyrics.simpmusic.org/search?q=$searchQuery"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    }
    
    fun retryLoadLyrics() {
        currentSong?.let { song ->
            loadLyrics(song)
        }
    }

    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            lyricsLoadingState = LyricsLoadingState.Loading
            
            when (val result = lyricsRepository.getLyrics(song)) {
                is LyricsResult.Success -> {
                    lyrics = result.lyrics
                    lyricsLoadingState = LyricsLoadingState.Success
                }
                is LyricsResult.NotFound -> {
                    lyrics = null
                    lyricsLoadingState = LyricsLoadingState.NotFound
                }
                is LyricsResult.Error -> {
                    lyrics = null
                    lyricsLoadingState = LyricsLoadingState.Error(result.message)
                }
            }
        }
    }
    
    private fun loadLyrics(songId: Long) {
        // Legacy method - find song and call new method
        currentSong?.let { song ->
            if (song.id == songId) {
                loadLyrics(song)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        cancelSleepTimer()
    }
}

/**
 * Loading states for lyrics fetching
 */
sealed class LyricsLoadingState {
    data object Idle : LyricsLoadingState()
    data object Loading : LyricsLoadingState()
    data object Success : LyricsLoadingState()
    data object NotFound : LyricsLoadingState()
    data class Error(val message: String) : LyricsLoadingState()
}
