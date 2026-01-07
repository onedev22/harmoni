package com.amurayada.music.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * Sealed interface for search result types.
 * Allows unified handling of different result types while maintaining type safety.
 */
sealed interface SearchResultType {
    fun objectType(): Type
    
    enum class Type {
        SONG, ARTIST, ALBUM, PLAYLIST, VIDEO, PODCAST
    }
}

/**
 * Thumbnail data with dimensions and URL.
 */
@Immutable
data class Thumbnail(
    val width: Int,
    val height: Int,
    val url: String
) {
    companion object {
        /**
         * Create a high-resolution thumbnail from a low-res URL.
         * Replaces dimension markers (w120, h120, s120) with higher resolution.
         */
        fun fromUrl(url: String, targetSize: Int = 544): Thumbnail {
            val highResUrl = url
                .replace(Regex("([wh])\\d+"), "$1$targetSize")
                .replace(Regex("=s\\d+"), "=s$targetSize")
                .replace(Regex("/s\\d+/"), "/s$targetSize/")
            
            return Thumbnail(targetSize, targetSize, highResUrl)
        }
    }
}

/**
 * Artist info for embedding in song/album results.
 */
@Immutable
data class ArtistInfo(
    val id: String?,
    val name: String
)

/**
 * Album info for embedding in song results.
 */
@Immutable
data class AlbumInfo(
    val id: String?,
    val name: String
)

/**
 * Feedback tokens for YouTube Music interactions (like/dislike).
 */
@Immutable
data class FeedbackTokens(
    val add: String? = null,
    val remove: String? = null
)

/**
 * Song search result from YouTube Music.
 * This is the internal representation; convert to Song for UI.
 */
@Immutable
data class SongsResult(
    val videoId: String,
    val title: String,
    val artists: List<ArtistInfo>,
    val album: AlbumInfo?,
    val duration: String?, // Formatted duration "3:45"
    val durationSeconds: Int,
    val thumbnails: List<Thumbnail>,
    val isExplicit: Boolean,
    val category: String = "Song",
    val resultType: String = "Song",
    val videoType: String = "Song",
    val year: String = "",
    val feedbackTokens: FeedbackTokens? = null
) : SearchResultType {
    override fun objectType() = SearchResultType.Type.SONG
    
    /**
     * Convert to UI Song model.
     */
    fun toSong(): Song = Song(
        id = videoId.hashCode().toLong(),
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.name ?: "YouTube Music",
        duration = durationSeconds * 1000L,
        albumArtUri = thumbnails.firstOrNull()?.let { Uri.parse(it.url) },
        path = "https://music.youtube.com/watch?v=$videoId",
        dateAdded = System.currentTimeMillis(),
        albumId = album?.id?.hashCode()?.toLong() ?: 0
    )
}

/**
 * Artist search result from YouTube Music.
 */
@Immutable
data class ArtistsResult(
    val artist: String,
    val browseId: String,
    val category: String = "Artist",
    val resultType: String = "Artist",
    val radioId: String = "",
    val shuffleId: String = "",
    val thumbnails: List<Thumbnail>
) : SearchResultType {
    override fun objectType() = SearchResultType.Type.ARTIST
    
    /**
     * Convert to UI Artist model.
     */
    fun toArtist(): Artist = Artist(
        id = browseId.hashCode().toLong(),
        name = artist,
        albumCount = 0,
        songCount = 0,
        path = "https://music.youtube.com/channel/$browseId",
        imageUrl = thumbnails.firstOrNull()?.url
    )
}

/**
 * Album search result from YouTube Music.
 */
@Immutable
data class AlbumsResult(
    val title: String,
    val browseId: String,
    val artists: List<ArtistInfo>,
    val year: String,
    val type: String = "Album", // Album, EP, Single
    val category: String = "Album",
    val resultType: String = "Album",
    val isExplicit: Boolean = false,
    val duration: String = "",
    val thumbnails: List<Thumbnail>
) : SearchResultType {
    override fun objectType() = SearchResultType.Type.ALBUM
    
    /**
     * Convert to UI Album model.
     */
    fun toAlbum(): Album = Album(
        id = browseId.hashCode().toLong(),
        name = title,
        artist = artists.joinToString(", ") { it.name },
        artworkUri = thumbnails.firstOrNull()?.let { Uri.parse(it.url) },
        year = year.toIntOrNull() ?: 0,
        songCount = 0,
        path = "https://music.youtube.com/browse/$browseId"
    )
}

/**
 * Playlist search result from YouTube Music.
 */
@Immutable
data class PlaylistsResult(
    val title: String,
    val browseId: String,
    val author: String,
    val itemCount: Int,
    val thumbnails: List<Thumbnail>
) : SearchResultType {
    override fun objectType() = SearchResultType.Type.PLAYLIST
}

/**
 * Container for search results with pagination support.
 */
data class SearchResultPage<T>(
    val items: List<T>,
    val continuation: String? = null
) {
    val hasMore: Boolean get() = continuation != null
}
