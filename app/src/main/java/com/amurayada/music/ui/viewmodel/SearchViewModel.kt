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
import com.amurayada.music.data.repository.ArtistDetails
import com.amurayada.music.data.repository.SearchRepository
import com.amurayada.music.data.repository.SearchRepositoryImpl
import com.amurayada.music.data.repository.HomeSection
import com.amurayada.music.data.model.Playlist
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// State for search
data class SearchScreenState(
    val searchType: SearchType = SearchType.ALL,
    val isLoading: Boolean = false,

    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val suggestions: List<String> = emptyList(),
    // Combined result for ALL view if needed, or just use the separate lists in UI
    val error: String? = null
)

enum class SearchType {
    ALL, SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searchRepository: SearchRepository = SearchRepositoryImpl(application.applicationContext)
    private var searchJob: kotlinx.coroutines.Job? = null
    private var suggestionsJob: kotlinx.coroutines.Job? = null

    var state by mutableStateOf(SearchScreenState())
        private set

    // Backward compatibility delegates for existing UI
    val searchResults: List<Song> get() = state.songs
    val artistResults: List<Artist> get() = state.artists
    val albumResults: List<Album> get() = state.albums
    val playlistResults: List<Playlist> get() = state.playlists
    val isLoading: Boolean get() = state.isLoading




    var selectedArtistDetails by mutableStateOf<ArtistDetails?>(null)
        private set

    var onlineHomeSections by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    
    // Internal separate lists to manage merging
    private var youtubeSections = listOf<HomeSection>()
    private var userSearchSections = mutableListOf<HomeSection>() // Mutable to accumulate
    
    // Track last search to personalize Home
    var lastSearchQuery by mutableStateOf<String?>(null)
        private set

    var userLibPlaylists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    // State for Home Pagination
    var isHomeLoading by mutableStateOf(false)
        private set
    
    var isHomeContinuing by mutableStateOf(false) // For background pagination
        private set
        
    var isHomeExhausted by mutableStateOf(false) // Reached end of content
        private set

    fun loadOnlineHome(forceRefresh: Boolean = false) {
        if (state.isLoading) return
        
        // If forcing refresh, clear existing data
        if (forceRefresh) {
            youtubeSections = emptyList()
            userLibPlaylists = emptyList()
            isHomeExhausted = false // Reset exhaustion
        }
        
        viewModelScope.launch {
            try {
                isHomeLoading = true
                isHomeExhausted = false // Reset checks
                
                // 1. Load CACHE first if empty
                if (youtubeSections.isEmpty()) {
                    val cached = searchRepository.getCachedHomeSections()
                    if (cached != null && cached.isNotEmpty()) {
                        youtubeSections = cached
                        updateOnlineHomeSections()
                    }
                }
                
                // 2. Fetch Network
                // Fetch YouTube base content
                val fetchedSections = searchRepository.getOnlineHomeSections()
                
                if (fetchedSections.isNotEmpty()) {
                    youtubeSections = fetchedSections
                    // Save to Cache
                    searchRepository.saveCachedHomeSections(fetchedSections)
                }
                
                // Fetch User Playlists
                userLibPlaylists = searchRepository.getUserPlaylists()
                
                // Merge
                updateOnlineHomeSections()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isHomeLoading = false
            }
        }
    }
    
    fun loadMoreHome() {
        if (isHomeContinuing || isHomeLoading || isHomeExhausted) return
        
        viewModelScope.launch {
            try {
                isHomeContinuing = true
                android.util.Log.d("SearchViewModel", "Loading more home sections...")
                
                val result = searchRepository.getContinueHome("") // Repo manages token state internally
                result.onSuccess { newSections ->
                    if (newSections.isNotEmpty()) {
                        android.util.Log.d("SearchViewModel", "Loaded ${newSections.size} more sections.")
                        youtubeSections = youtubeSections + newSections
                        updateOnlineHomeSections()
                    } else {
                        // Empty result usually means end of list
                        isHomeExhausted = true
                    }
                }.onFailure {
                     // Failure (likely "No continuation token") means we are done
                     android.util.Log.e("SearchViewModel", "Home continuation failed/ended", it)
                     isHomeExhausted = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isHomeExhausted = true
            } finally {
                isHomeContinuing = false
            }
        }
    }
    
    private fun updateOnlineHomeSections() {
        val finalSections = mutableListOf<HomeSection>()
        val usedSongIds = mutableSetOf<Long>()
        val usedAlbumIds = mutableSetOf<Long>()

        // 0. User Playlists (Priority TOP)
        if (userLibPlaylists.isNotEmpty()) {
            finalSections.add(
                HomeSection(
                    title = "Tus Playlists",
                    songs = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    playlists = userLibPlaylists
                )
            )
        }
        
        // 1. YouTube Content FIRST (Priority - comes from API, is "fresh")
        val filteredYoutube = youtubeSections.filterNot { it.title.isEmpty() }
        
        for (section in filteredYoutube) {
            // Deduplicate songs within this section against already-used IDs
            val uniqueSongs = section.songs.filter { it.id !in usedSongIds }
            val uniqueAlbums = section.albums.filter { it.id !in usedAlbumIds }
            
            if (uniqueSongs.isNotEmpty() || uniqueAlbums.isNotEmpty() || section.artists.isNotEmpty()) {
                finalSections.add(
                    section.copy(
                        songs = uniqueSongs,
                        albums = uniqueAlbums
                    )
                )
                usedSongIds.addAll(uniqueSongs.map { it.id })
                usedAlbumIds.addAll(uniqueAlbums.map { it.id })
            }
        }
        
        // 2. User Search Sections (Personalized - only add if NOT already covered by YouTube)
        // Check if YouTube already provided "Quick Picks" or similar
        val hasQuickPicks = finalSections.any { 
            val t = it.title.lowercase()
            t.contains("quick") || t.contains("rápid") || t.contains("picks") || t.contains("selecciones")
        }
        
        if (!hasQuickPicks && userSearchSections.isNotEmpty()) {
            // Build "Sugerencias para ti" from user searches, excluding already-used songs
            val allUserSongs = userSearchSections.flatMap { it.songs }
                .filter { it.id !in usedSongIds }
                .distinctBy { it.id }
                .take(24)
            
            if (allUserSongs.isNotEmpty()) {
                // Insert after playlists if present, or at top
                val insertIndex = if (userLibPlaylists.isNotEmpty()) 1 else 0
                finalSections.add(insertIndex, 
                    HomeSection(
                        title = "Sugerencias para ti",
                        songs = allUserSongs,
                        albums = emptyList(),
                        artists = emptyList()
                    )
                )
                usedSongIds.addAll(allUserSongs.map { it.id })
            }
        }
        
        // 3. User Album Sections (Personalized)
        for (section in userSearchSections.filter { it.albums.isNotEmpty() }) {
            val uniqueAlbums = section.albums.filter { it.id !in usedAlbumIds }
            if (uniqueAlbums.isNotEmpty()) {
                finalSections.add(section.copy(albums = uniqueAlbums))
                usedAlbumIds.addAll(uniqueAlbums.map { it.id })
            }
        }
        
        onlineHomeSections = finalSections.distinctBy { it.title }
    }
    
    /**
     * Update Home with search results - called after a successful search.
     * Accumulates personalized sections dynamically.
     */
    /**
     * Update Home with search results - called after a successful search.
     * Accumulates personalized sections dynamically.
     */
    init {
        // User requested to REMOVE old persisted content loading.
        // We start fresh every time or rely on loadOnlineHome().
        // com.amurayada.music.utils.UserHomePersistence.loadSections() removed.
        
        // If we want to persist ONLY explicitly saved user playlists or something else, we do it here.
        // But for "Search History Sections", we skip it to avoid "Old Code" flash.
    }
    
    fun updateHomeWithSearchResults(query: String, songs: List<Song>, albums: List<Album>, artists: List<Artist>) {
        if (query.isBlank() || (songs.isEmpty() && albums.isEmpty() && artists.isEmpty())) return
        
        // Quality Filter: Ignore short queries unless exact match found
        // Prevents "por" or "bad" (incomplete) from cluttering Home
        val cleanQuery = query.trim()
        val hasExactMatch = artists.any { it.name.equals(cleanQuery, true) }
        if (cleanQuery.length < 3 && !hasExactMatch) return

        lastSearchQuery = query

        // Smart valid name extraction
        val displayName = artists.firstOrNull()?.name 
            ?: albums.firstOrNull()?.artist 
            ?: songs.firstOrNull()?.artist 
            ?: query

        // Clean up duplicates
        userSearchSections.removeAll { 
            it.title.contains(query, ignoreCase = true) || it.title.contains(displayName, ignoreCase = true)
        }
        
        val newSections = mutableListOf<HomeSection>()
        
        // Add songs section
        if (songs.isNotEmpty()) {
            newSections.add(
                HomeSection(
                    title = displayName, // Minimalist Title: "Imagine Dragons"
                    songs = songs.take(10),
                    albums = emptyList(),
                    artists = emptyList()
                )
            )
        }
        
        // Add albums section
        if (albums.isNotEmpty()) {
            newSections.add(
                HomeSection(
                    title = "$displayName • Álbumes",
                    songs = emptyList(),
                    albums = albums.take(6),
                    artists = emptyList()
                )
            )
        }
        
        // Add to TOP
        userSearchSections.addAll(0, newSections)
        
        // Limit history (Increased limit because we now merge them, so we want more data)
        if (userSearchSections.size > 20) {
            val trimCount = userSearchSections.size - 20
            repeat(trimCount) { userSearchSections.removeLast() }
        }
        
        // Persist
        com.amurayada.music.utils.UserHomePersistence.saveSections(userSearchSections)
        
        updateOnlineHomeSections()
    }
    
    /**
     * Force refresh Home - clears personalized content
     */
    fun refreshHome() {
        lastSearchQuery = null
        userSearchSections.clear()
        youtubeSections = emptyList() // Force reload
        updateOnlineHomeSections() // Clear UI immediately
        loadOnlineHome()
    }
        
    var selectedAlbumSongs by mutableStateOf<List<Song>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        // Load suggestions as user types (debounced)
        loadSuggestions(query)
    }
    
    /**
     * Load search suggestions with debounce.
     */
    fun loadSuggestions(query: String) {
        suggestionsJob?.cancel()
        if (query.length < 2) {
            state = state.copy(suggestions = emptyList())
            return
        }
        
        suggestionsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Debounce 300ms
            try {
                val suggestions = searchRepository.getSearchSuggestions(query)
                state = state.copy(suggestions = suggestions)
            } catch (e: Exception) {
                // Silently fail - suggestions are optional
            }
        }
    }
    
    /**
     * Clear suggestions when search is executed.
     */
    fun clearSuggestions() {
        suggestionsJob?.cancel()
        state = state.copy(suggestions = emptyList())
    }
    
    fun setSearchType(type: SearchType) {
        state = state.copy(searchType = type)
        // If query exists, re-trigger search for specific type if needed
        // For now, our searchAll fetches everything anyway, so just filtering in UI is fine
        // strictly speaking, we could optimize to only fetch what is needed.
        if (searchQuery.isNotBlank()) {
            search(searchQuery, debounce = false, type = type)
        }
    }

    fun search(query: String, debounce: Boolean = false, type: SearchType = state.searchType) {
        if (query.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) {
                kotlinx.coroutines.delay(500)
            }
            
            // Set loading, clear previous results only if new search
            state = state.copy(
                isLoading = true,
                searchType = type,
                // Don't clear immediately to avoid flashing? Or clear to show loading indicator over old content.
                // Standard behavior: clear or show loading indicator over old content.
                // Converting to reference logic:

                songs = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList()
            )
            
            if (type == SearchType.ALL) {
                searchAllParallel(query)
            } else {
                // Specific search
                when (type) {
                    SearchType.SONGS -> {
                        val result = searchRepository.searchSongs(query)
                        state = state.copy(songs = result, isLoading = false)
                    }
                    SearchType.ARTISTS -> {
                        val result = searchRepository.searchArtists(query)
                        state = state.copy(artists = result, isLoading = false)
                    }
                    SearchType.ALBUMS -> {
                        val result = searchRepository.searchAlbums(query)
                        state = state.copy(albums = result, isLoading = false)
                    }
                    SearchType.PLAYLISTS -> {
                        val result = searchRepository.searchPlaylists(query)
                        state = state.copy(playlists = result, isLoading = false)
                    }
                    else -> searchAllParallel(query)
                }
            }
        }
    }

    private suspend fun searchAllParallel(query: String) {
        var songsList = emptyList<Song>()
        var artistsList = emptyList<Artist>()
        var albumsList = emptyList<Album>()
        var playlistsList = emptyList<Playlist>()

        val jobSongs = viewModelScope.launch {
            try {
                songsList = searchRepository.searchSongs(query)
                // Progressive update
                state = state.copy(songs = songsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val jobArtists = viewModelScope.launch {
            try {
                artistsList = searchRepository.searchArtists(query)
                state = state.copy(artists = artistsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val jobAlbums = viewModelScope.launch {
            try {
                albumsList = searchRepository.searchAlbums(query)
                state = state.copy(albums = albumsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val jobPlaylists = viewModelScope.launch {
            try {
                playlistsList = searchRepository.searchPlaylists(query)
                state = state.copy(playlists = playlistsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        joinAll(jobSongs, jobArtists, jobAlbums, jobPlaylists)
        
        state = state.copy(
            isLoading = false,
            songs = songsList,
            artists = artistsList,
            albums = albumsList,
            playlists = playlistsList
        )
        
        // 🔥 UPDATE HOME WITH SEARCH RESULTS - Dynamic personalization!
        updateHomeWithSearchResults(query, songsList, albumsList, artistsList)
    }
    
    fun clearSearch() {
        state = SearchScreenState()
        searchQuery = ""
    }
    
    private var currentArtistUrl: String? = null

    fun getArtistDetails(url: String) {
        if (currentArtistUrl == url && selectedArtistDetails != null) return

        viewModelScope.launch {
            // Clear previous details immediately to avoid showing stale data
            selectedArtistDetails = null
            currentArtistUrl = url
            // We could add detailLoading to state if we wanted, but sticking to existing pattern for details for now
            try {
                selectedArtistDetails = searchRepository.getArtistDetails(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun getAlbumDetails(url: String) {
        viewModelScope.launch {
            selectedAlbumSongs = emptyList()
            try {
                selectedAlbumSongs = searchRepository.getAlbumDetails(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun clearSelectedArtist() {
        selectedArtistDetails = null
    }
    
    fun clearSelectedAlbum() {
        selectedAlbumSongs = emptyList()
    }
}


