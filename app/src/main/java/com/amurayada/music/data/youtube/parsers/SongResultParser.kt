package com.amurayada.music.data.youtube.parsers

import com.amurayada.music.data.model.ArtistInfo
import com.amurayada.music.data.model.AlbumInfo
import com.amurayada.music.data.model.SongsResult
import com.amurayada.music.data.model.Thumbnail
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for song results from YouTube Music API.
 * Extracts song data from musicResponsiveListItemRenderer objects.
 */
object SongResultParser {
    
    private const val MAX_SONG_DURATION_SECONDS = 20 * 60 // 20 minutes - filter out interviews/concerts
    
    /**
     * Parse a single song item from the API response.
     * 
     * @param item The musicResponsiveListItemRenderer JSON object
     * @return SongsResult if valid song, null otherwise
     */
    fun parse(item: JSONObject): SongsResult? {
        try {
            // Get video ID - required
            val playlistItemData = item.optJSONObject("playlistItemData")
            val videoId = playlistItemData?.optString("videoId")
            if (videoId.isNullOrEmpty()) return null
            
            // Parse flex columns for title and metadata
            val flexColumns = item.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() == 0) return null
            
            // Title from first column
            val title = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: return null
            
            // Parse subtitle (second column) for artists, album, type
            val subtitleRuns = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            
            val (artists, album, contentType) = parseSubtitle(subtitleRuns)
            
            // Skip videos explicitly marked as such
            if (contentType == "video") {
                android.util.Log.d("SongParser", "Skipping video: $title")
                return null
            }
            
            // Parse duration from fixed columns
            var durationSeconds = 0
            val fixedColumns = item.optJSONArray("fixedColumns")
            if (fixedColumns != null && fixedColumns.length() > 0) {
                val durationText = fixedColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text")
                
                if (durationText != null) {
                    durationSeconds = parseDurationToSeconds(durationText)
                }
            }
            
            // Filter out long content (interviews, concerts, etc.)
            if (durationSeconds > MAX_SONG_DURATION_SECONDS) {
                android.util.Log.d("SongParser", "Skipping long item ($durationSeconds s): $title")
                return null
            }
            
            // Parse thumbnails and upgrade to high resolution
            val thumbnails = parseThumbnails(item)
            
            // Check for explicit content
            val badges = item.optJSONArray("badges")
            val isExplicit = badges?.let { hasExplicitBadge(it) } ?: false
            
            return SongsResult(
                videoId = videoId,
                title = title,
                artists = artists,
                album = album,
                duration = formatDuration(durationSeconds),
                durationSeconds = durationSeconds,
                thumbnails = thumbnails,
                isExplicit = isExplicit
            )
            
        } catch (e: Exception) {
            android.util.Log.e("SongParser", "Error parsing song", e)
            return null
        }
    }
    
    /**
     * Parse subtitle runs to extract artists, album, and content type.
     */
    private fun parseSubtitle(runs: JSONArray?): Triple<List<ArtistInfo>, AlbumInfo?, String> {
        val artists = mutableListOf<ArtistInfo>()
        var album: AlbumInfo? = null
        var contentType = "song"
        
        if (runs == null) return Triple(artists, album, contentType)
        
        val parts = mutableListOf<Pair<String, String?>>() // text to browseId
        
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text")
            if (text == " • " || text.isBlank()) continue
            
            val browseId = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            
            parts.add(text to browseId)
        }
        
        // Detect content type from parts
        val lowerParts = parts.map { it.first.lowercase() }
        when {
            lowerParts.any { it == "song" || it == "canción" } -> contentType = "song"
            lowerParts.any { it == "video" || it == "vídeo" } -> contentType = "video"
            lowerParts.any { it.contains("views") || it.contains("vistas") } -> contentType = "video"
        }
        
        // Filter out type indicators and parse artists/album
        val filteredParts = parts.filter { (text, _) ->
            val lower = text.lowercase()
            lower != "song" && lower != "canción" && lower != "video" && lower != "vídeo"
        }
        
        for ((text, browseId) in filteredParts) {
            when {
                browseId?.startsWith("UC") == true -> {
                    // This is an artist
                    artists.add(ArtistInfo(browseId, text))
                }
                browseId?.startsWith("MPREb_") == true || browseId?.startsWith("OLAK5uy_") == true -> {
                    // This is an album
                    album = AlbumInfo(browseId, text)
                }
                artists.isEmpty() && !text.matches(Regex("\\d{4}")) -> {
                    // First non-year text without browseId is probably the artist
                    artists.add(ArtistInfo(null, text))
                }
                album == null && !text.matches(Regex("\\d+:\\d+")) && !text.matches(Regex("\\d{4}")) -> {
                    // Non-duration, non-year text might be album
                    album = AlbumInfo(null, text)
                }
            }
        }
        
        return Triple(artists, album, contentType)
    }
    
    /**
     * Parse thumbnails and upgrade to high resolution.
     */
    private fun parseThumbnails(item: JSONObject): List<Thumbnail> {
        val thumbnailsArray = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return emptyList()
        
        if (thumbnailsArray.length() == 0) return emptyList()
        
        // Get the highest resolution thumbnail and upgrade it
        val lastThumbnail = thumbnailsArray.optJSONObject(thumbnailsArray.length() - 1)
        val url = lastThumbnail?.optString("url") ?: return emptyList()
        
        return listOf(Thumbnail.fromUrl(url, 544))
    }
    
    /**
     * Check if badges contain explicit marker.
     */
    private fun hasExplicitBadge(badges: JSONArray): Boolean {
        for (i in 0 until badges.length()) {
            val badge = badges.optJSONObject(i)
            val iconType = badge?.optJSONObject("musicInlineBadgeRenderer")
                ?.optJSONObject("icon")
                ?.optString("iconType")
            if (iconType == "MUSIC_EXPLICIT_BADGE") return true
        }
        return false
    }
    
    /**
     * Parse duration string (e.g., "3:45" or "1:23:45") to seconds.
     */
    private fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":")
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: 0
                val seconds = parts[1].toIntOrNull() ?: 0
                minutes * 60 + seconds
            }
            3 -> {
                val hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1].toIntOrNull() ?: 0
                val seconds = parts[2].toIntOrNull() ?: 0
                hours * 3600 + minutes * 60 + seconds
            }
            else -> 0
        }
    }
    
    /**
     * Format seconds to duration string.
     */
    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(minutes, secs)
    }
}
