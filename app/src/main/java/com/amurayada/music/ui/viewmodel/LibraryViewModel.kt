package com.amurayada.music.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.MediaRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = MediaRepository(application)
    // Add SearchRepository for online Library
    private val searchRepository = com.amurayada.music.data.repository.SearchRepositoryImpl(application.applicationContext) // Init correctly
    
    // Download Repository for integrating downloaded songs
    private val downloadDatabase = com.amurayada.music.data.database.DownloadDatabase.getDatabase(application)
    private val streamRepository = com.amurayada.music.data.repository.StreamRepositoryImpl()
    private val downloadRepository = com.amurayada.music.data.repository.DownloadRepositoryImpl(
        application,
        downloadDatabase.downloadDao(),
        streamRepository
    )
    
    private var debounceJob: kotlinx.coroutines.Job? = null
    
    private val _permissionEvent = androidx.lifecycle.MutableLiveData<androidx.activity.result.IntentSenderRequest?>()
    val permissionEvent: androidx.lifecycle.LiveData<androidx.activity.result.IntentSenderRequest?> = _permissionEvent
    
    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            android.util.Log.d("LibraryViewModel", "ContentObserver.onChange triggered! uri=$uri")
            triggerLibraryUpdate()
        }
        
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            android.util.Log.d("LibraryViewModel", "ContentObserver.onChange (no uri) triggered!")
            triggerLibraryUpdate()
        }
    }
    
    private fun triggerLibraryUpdate() {
        android.util.Log.d("LibraryViewModel", "triggerLibraryUpdate called, debouncing...")
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            // Debounce for 2 seconds to allow multiple file operations to complete
            // and to let the system MediaScanner finish its work
            kotlinx.coroutines.delay(2000) 
            android.util.Log.d("LibraryViewModel", "Debounce completed, clearing image cache and calling loadLibrary()")
            // Clear image cache so any changed album art reloads
            com.amurayada.music.MusicApplication.getInstance()?.clearImageCache()
            loadLibrary()
        }
    }

    init {
        // Observe downloads database changes to auto-refresh library
        viewModelScope.launch {
            downloadRepository.observeDownloadedSongs().collect { songs ->
                android.util.Log.d("LibraryViewModel", "Downloads changed (size: ${songs.size}), refreshing library...")
                // We use loadLibrary directly here as the DB change is already "debounced" by flow emission usually
                // and we want immediate feedback when download finishes
                loadLibrary()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
    }
    
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    
    var genres by mutableStateOf<List<com.amurayada.music.data.model.Genre>>(emptyList())
        private set
        
    var userPlaylists by mutableStateOf<List<com.amurayada.music.data.model.Playlist>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set
    
    var searchQuery by mutableStateOf("")
        private set
    
    val filteredSongs: List<Song> by derivedStateOf {
        if (searchQuery.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    val filteredAlbums: List<Album> by derivedStateOf {
        if (searchQuery.isEmpty()) {
            albums
        } else {
            albums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    val filteredArtists: List<Artist> by derivedStateOf {
        if (searchQuery.isEmpty()) {
            artists
        } else {
            artists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredGenres: List<com.amurayada.music.data.model.Genre> by derivedStateOf {
        if (searchQuery.isEmpty()) {
            genres
        } else {
            genres.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
        
    val recentlyAddedSongs: List<Song> by derivedStateOf {
        songs.sortedByDescending { it.dateAdded }.take(20)
    }

    val timeCapsuleSongs: List<Song> by derivedStateOf {
        // Songs added more than 1 year ago (approx 365 days)
        // Ensure we filter out songs with 0 or invalid dates (downloaded songs might have 0)
        val validSongs = songs.filter { it.dateAdded > 1000000 } // Basic check: > 1970
        
        val oneYearAgo = System.currentTimeMillis() / 1000 - (365 * 24 * 60 * 60)
        val oldSongs = validSongs.filter { it.dateAdded < oneYearAgo }
        
        if (oldSongs.size >= 10) {
            oldSongs.shuffled().take(20)
        } else {
            // Fallback to just the oldest 20 songs if we don't have enough "old" songs
            validSongs.sortedBy { it.dateAdded }.take(20).shuffled()
        }
    }
    
    var libraryVersion by mutableStateOf(System.currentTimeMillis())
        private set

    fun loadLibrary() {
        viewModelScope.launch {
            isLoading = true
            try {
                android.util.Log.d("LibraryViewModel", "loadLibrary() starting...")
                
                // Move heavy calculation to IO dispatcher
                val result = withContext(Dispatchers.IO) {
                    // Clean up "ghost" downloads (files deleted externally)
                    downloadRepository.validateDownloads()
                    
                    val localSongs = repository.getAllSongs()
                    
                    // Get downloaded songs and combine with local
                    val downloadedSongs = downloadRepository.getDownloadedSongs()
                    android.util.Log.d("LibraryViewModel", "Found ${downloadedSongs.size} downloaded songs")
                    
                    // Combine and deduplicate by title and artist (case-insensitive)
                    // Combine and deduplicate by path (to avoid duplicates if MediaStore indexed the downloaded file)
                    val allSongs = (localSongs + downloadedSongs).distinctBy { it.path }
                    val sortedSongs = allSongs.sortedBy { it.title }

                    // Merge Albums: Start with MediaStore albums, add downloaded albums
                    val mediaStoreAlbums = repository.getAllAlbums()
                    val downloadedAlbums = downloadedSongs.map { song ->
                        Album(
                            id = song.albumId,
                            name = song.album ?: "Unknown Album",
                            artist = song.artist,
                            artworkUri = song.albumArtUri,
                            songCount = 1, // Will be corrected by grouping
                            year = 0 // Metadata might be missing
                        )
                    }.distinctBy { it.id }
                    
                    // Combine albums, favoring MediaStore versions (usually better metadata), but adding missing ones
                    val allAlbums = (mediaStoreAlbums + downloadedAlbums).distinctBy { it.id }
                    // Recalculate song counts correctly based on FULL song list
                    val finalAlbums = allAlbums.map { album ->
                         val count = sortedSongs.count { it.albumId == album.id }
                         if (count != album.songCount) album.copy(songCount = count) else album
                    }.filter { it.songCount > 0 }.sortedBy { it.name }

                    // Merge Artists: Start with MediaStore artists, add downloaded artists
                    val mediaStoreArtists = repository.getAllArtists()
                    // Map names to existing IDs to avoid duplicates for same artist
                    val knownArtistNames = mediaStoreArtists.associate { it.name.lowercase() to it.id }

                    val downloadedArtists = downloadedSongs.map { song ->
                        // Reuse existing ID if artist allows matches, otherwise generate hash
                        val existingId = knownArtistNames[song.artist.lowercase()]
                        Artist(
                            id = existingId ?: song.artist.hashCode().toLong(),
                            name = song.artist,
                            albumCount = 0, // Recalculate below
                            songCount = 0  // Recalculate below
                        )
                    }.distinctBy { it.id }
                    
                    // Combine artists
                    val allArtists = (mediaStoreArtists + downloadedArtists).distinctBy { it.id }
                    // Recalculate counts
                    val finalArtists = allArtists.map { artist ->
                        // Count by NAME as Song might not have consistent artistId across sources
                        val tracksCount = sortedSongs.count { it.artist.equals(artist.name, ignoreCase = true) }
                        val albumsCount = finalAlbums.count { it.artist.equals(artist.name, ignoreCase = true) }
                        artist.copy(songCount = tracksCount, albumCount = albumsCount)
                    }.filter { it.songCount > 0 }.sortedBy { it.name }

                    val finalGenres = repository.getAllGenres()
                    
                    // Return result as a data class
                    LibraryData(
                        songs = sortedSongs,
                        albums = finalAlbums,
                        artists = finalArtists,
                        genres = finalGenres,
                        playlists = emptyList() // Don't block for playlists
                    )
                }
                
                // Update State on Main Thread
                songs = result.songs
                albums = result.albums
                artists = result.artists
                genres = result.genres
                
                // Update version to force UI refresh (especially images)
                libraryVersion = System.currentTimeMillis()
                
                android.util.Log.d("LibraryViewModel", "loadLibrary() completed: ${songs.size} songs, ${albums.size} albums")
                
                // NOW fetch online playlists asynchronously
                fetchOnlinePlaylists()

            } finally {
                isLoading = false
            }
        }
    }
    
    private fun fetchOnlinePlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                 if (com.amurayada.music.data.auth.YouTubeAuthManager.getInstance(getApplication()).isLoggedIn()) {
                     val result = searchRepository.getUserPlaylists()
                     if (result.isNotEmpty()) {
                         android.util.Log.d("LibraryViewModel", "Loaded ${result.size} online playlists")
                         withContext(Dispatchers.Main) {
                             userPlaylists = result
                         }
                     }
                 }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    
    fun updateSearchQuery(query: String) {
        searchQuery = query
    }
    
    fun getSongsByAlbum(albumId: Long): List<Song> {
        return songs.filter { it.albumId == albumId }
    }
    
    fun getSongsByArtist(artistName: String): List<Song> {
        return songs.filter { it.artist == artistName }
    }

    suspend fun getSongsByGenre(genreId: Long): List<Song> {
        return repository.getSongsByGenre(genreId)
    }

    fun deleteSong(song: Song, context: android.content.Context) {
        viewModelScope.launch {
            try {
                repository.deleteSong(song.id)
                // The ContentObserver will trigger a reload, but we can also optimistically remove it
                songs = songs.filter { it.id != song.id }
            } catch (e: android.app.RecoverableSecurityException) {
                // Request permission from user
                // This needs to be handled by the Activity/Fragment
                // For now, we'll just rethrow or let the caller handle it if possible
                // Since we are in ViewModel, we can't startIntentSender directly easily without context
                // We passed context, but starting intent sender requires Activity context usually
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                     val intentSender = e.userAction.actionIntent.intentSender
                     // We need to signal the UI to launch this intent sender
                     // For simplicity in this iteration, we might need a SharedFlow for events
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Simplified version for now that assumes permission is granted or handles it via standard flow
    // In a real app, we'd use a channel to send the IntentSenderRequest to the UI
    suspend fun performDeleteSong(songId: Long) {
        repository.deleteSong(songId)
    }

    suspend fun getAlbumGenre(albumId: Long): String? {
        // Find a song from this album to get the audio ID
        val song = songs.find { it.albumId == albumId }
        return if (song != null) {
            repository.getGenreForAudio(song.id)
        } else {
            null
        }
    }

    suspend fun performUpdateAlbum(albumId: Long, newTitle: String, newArtist: String, newGenre: String, imageUri: android.net.Uri?): Result<Long> {
        return try {
            val result = repository.updateAlbum(albumId, newTitle, newArtist, newGenre, imageUri)
            if (result.isSuccess) {
                // Clear Coil's image cache so album art reloads with new data
                com.amurayada.music.MusicApplication.getInstance()?.clearImageCache()
                loadLibrary() // Reload to reflect changes
            }
            result
        } catch (e: Exception) {
            // Handle error
            if (e is com.amurayada.music.data.repository.MediaRepository.RequiresPermissionException) {
                _permissionEvent.value = androidx.activity.result.IntentSenderRequest.Builder(e.intentSender).build()
                Result.failure(e)
            } else if (e is android.app.RecoverableSecurityException) {
                _permissionEvent.value = androidx.activity.result.IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                Result.failure(e)
            } else {
                Result.failure(e)
            }
        }
    }

    // Hoisted state for LibraryScreen tabs
    var selectedLibraryTab by mutableStateOf(0) // 0: Songs, 1: Albums, 2: Artists, 3: Genres

    // Search History
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("music_app_prefs", android.content.Context.MODE_PRIVATE)
    }

    private fun loadSearchHistory() {
        val historyString = prefs.getString("search_history", "") ?: ""
        if (historyString.isNotEmpty()) {
            searchHistory = historyString.split("|").filter { it.isNotEmpty() }
        }
    }

    private fun saveSearchHistory() {
        val historyString = searchHistory.joinToString("|")
        prefs.edit().putString("search_history", historyString).apply()
    }

    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val currentHistory = searchHistory.toMutableList()
        currentHistory.remove(query) // Remove if exists to move to top
        currentHistory.add(0, query)
        if (currentHistory.size > 5) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        searchHistory = currentHistory
        saveSearchHistory()
    }

    fun removeFromSearchHistory(query: String) {
        val currentHistory = searchHistory.toMutableList()
        currentHistory.remove(query)
        searchHistory = currentHistory
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        saveSearchHistory()
    }

    init {
        loadLibrary()
        loadSearchHistory()
        val contentResolver = application.contentResolver
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }
    private data class LibraryData(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val genres: List<com.amurayada.music.data.model.Genre>,
        val playlists: List<com.amurayada.music.data.model.Playlist>
    )
}
