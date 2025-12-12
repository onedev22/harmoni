package com.amurayada.music.ui.viewmodel

import android.app.Application
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

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = MediaRepository(application)
    
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    
    var genres by mutableStateOf<List<com.amurayada.music.data.model.Genre>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set
    
    var searchQuery by mutableStateOf("")
        private set
    
    val filteredSongs: List<Song>
        get() = if (searchQuery.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    
    val filteredAlbums: List<Album>
        get() = if (searchQuery.isEmpty()) {
            albums
        } else {
            albums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    
    val filteredArtists: List<Artist>
        get() = if (searchQuery.isEmpty()) {
            artists
        } else {
            artists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

    val filteredGenres: List<com.amurayada.music.data.model.Genre>
        get() = if (searchQuery.isEmpty()) {
            genres
        } else {
            genres.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
        
    val recentlyAddedSongs: List<Song>
        get() = songs.sortedByDescending { it.dateAdded }.take(20)
    
    fun loadLibrary() {
        viewModelScope.launch {
            isLoading = true
            try {
                songs = repository.getAllSongs()
                albums = repository.getAllAlbums()
                artists = repository.getAllArtists()
                genres = repository.getAllGenres()
            } finally {
                isLoading = false
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

    // Hoisted state for LibraryScreen tabs
    var selectedLibraryTab by mutableStateOf(0) // 0: Songs, 1: Albums, 2: Artists, 3: Genres
}
