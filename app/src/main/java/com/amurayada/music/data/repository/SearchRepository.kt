package com.amurayada.music.data.repository

import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song

data class SearchResult(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<com.amurayada.music.data.model.Playlist> = emptyList()
)

data class HomeSection(
    val title: String,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<com.amurayada.music.data.model.Playlist> = emptyList()
)

interface SearchRepository {
    suspend fun search(query: String): SearchResult
    suspend fun searchSongs(query: String): List<Song>
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchPlaylists(query: String): List<com.amurayada.music.data.model.Playlist>
    
    suspend fun getSearchSuggestions(query: String): List<String>
    
    suspend fun getTrending(): List<Song>
    suspend fun getOnlineHomeSections(): List<HomeSection>
    suspend fun getUserPlaylists(): List<com.amurayada.music.data.model.Playlist>

    suspend fun getArtistDetails(url: String): ArtistDetails
    suspend fun getAlbumDetails(url: String): List<Song>
    
    suspend fun getContinueHome(continuation: String): Result<List<HomeSection>>

    suspend fun getCachedHomeSections(): List<HomeSection>?
    suspend fun saveCachedHomeSections(sections: List<HomeSection>)
}

data class ArtistDetails(
    val artist: Artist,
    val topSongs: List<Song>,
    val albums: List<Album>,
    val singles: List<Album> = emptyList()
)
