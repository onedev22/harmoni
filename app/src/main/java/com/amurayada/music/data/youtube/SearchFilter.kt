package com.amurayada.music.data.youtube

/**
 * Search filter parameters for YouTube Music API.
 * These are the exact params used by SimpMusic/kotlinYtmusicScraper.
 * 
 * The params are base64-encoded protobuf messages that tell YouTube Music
 * what type of content to filter for.
 */
enum class SearchFilter(val param: String) {
    /**
     * Filter for songs only (official tracks from artists/labels).
     * Excludes videos, user-generated content, and other non-song results.
     */
    FILTER_SONG("EgWKAQIIAWoMEAMQBRAJEAoQBBAO"),
    
    /**
     * Filter for music videos only.
     */
    FILTER_VIDEO("EgWKAQIQAWoMEAMQBRAJEAoQBBAO"),
    
    /**
     * Filter for albums (including EPs).
     */
    FILTER_ALBUM("EgWKAQIYAWoMEAMQBRAJEAoQBBAO"),
    
    /**
     * Filter for artists only.
     * Returns verified YouTube Music artists, not random user channels.
     */
    FILTER_ARTIST("EgWKAQIgAWoMEAMQBRAJEAoQBBAO"),
    
    /**
     * Filter for community playlists (user-created).
     */
    FILTER_COMMUNITY_PLAYLIST("EgWKAQIoAWoMEAMQBRAJEAoQBBAO"),
    
    /**
     * Filter for featured/curated playlists (YouTube Music official).
     */
    FILTER_FEATURED_PLAYLIST("EgWKAQIoAWoKEAQQAhADEAUQCQ%3D%3D"),
    
    /**
     * Filter for podcasts.
     */
    FILTER_PODCAST("EgeKAQQoAUEC");
    
    companion object {
        /**
         * Get the appropriate filter for a search type.
         */
        fun fromSearchType(type: String): SearchFilter? {
            return when (type.lowercase()) {
                "song", "songs", "canción", "canciones" -> FILTER_SONG
                "video", "videos", "vídeo", "vídeos" -> FILTER_VIDEO
                "album", "albums", "álbum", "álbumes" -> FILTER_ALBUM
                "artist", "artists", "artista", "artistas" -> FILTER_ARTIST
                "playlist", "playlists" -> FILTER_COMMUNITY_PLAYLIST
                "podcast", "podcasts" -> FILTER_PODCAST
                else -> null
            }
        }
    }
}

/**
 * Additional YouTube search filters for duration, upload date, etc.
 * These are from SmartTube/SearchFilterHelper.java
 */
object YouTubeSearchFilters {
    // Duration filters
    const val DURATION_UNDER_4_MIN = "EgQQARgB"
    const val DURATION_4_TO_20_MIN = "EgQQARgD"
    const val DURATION_OVER_20_MIN = "EgQQARgC"
    
    // Content type filters
    const val TYPE_VIDEO = "EgIQAQ%3D%3D"
    const val TYPE_CHANNEL = "EgIQAg%3D%3D"
    const val TYPE_PLAYLIST = "EgIQAw%3D%3D"
    const val TYPE_MOVIE = "EgIQBA%3D%3D"
    
    // Features
    const val FEATURE_LIVE = "EgJAAQ%3D%3D"
    const val FEATURE_4K = "EgJwAQ%3D%3D"
    const val FEATURE_HDR = "EgPIAQE%3D"
    
    // Upload date
    const val UPLOAD_LAST_HOUR = "EgQIARAB"
    const val UPLOAD_TODAY = "EgQIAhAB"
    const val UPLOAD_THIS_WEEK = "EgQIAxAB"
    const val UPLOAD_THIS_MONTH = "EgQIBBAB"
    const val UPLOAD_THIS_YEAR = "EgQIBRAB"
    
    // Sort order
    const val SORT_BY_UPLOAD_DATE = "CAI%3D"
    const val SORT_BY_VIEW_COUNT = "CAMSAhAB"
    const val SORT_BY_RATING = "CAESAhAB"
}
