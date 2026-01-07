package com.amurayada.music.data.youtube.parsers

import com.amurayada.music.data.model.AlbumsResult
import com.amurayada.music.data.model.ArtistInfo
import com.amurayada.music.data.model.Thumbnail
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for album results from YouTube Music API.
 * Handles albums, EPs, and singles.
 */
object AlbumResultParser {
    
    /**
     * Parse a single album item from the API response.
     * 
     * @param item The musicResponsiveListItemRenderer JSON object
     * @return AlbumsResult if valid album, null otherwise
     */
    fun parse(item: JSONObject): AlbumsResult? {
        try {
            // Get browse ID - required
            val navigationEndpoint = item.optJSONObject("navigationEndpoint")
            val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            
            // Albums have browse IDs starting with MPREb_ or OLAK5uy_
            if (browseId.isNullOrEmpty()) return null
            if (!browseId.startsWith("MPREb_") && !browseId.startsWith("OLAK5uy_")) {
                // Check if explicitly marked as album in subtitle
                val flexColumns = item.optJSONArray("flexColumns")
                val subtitleRuns = flexColumns?.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                
                if (!isExplicitlyAlbum(subtitleRuns)) return null
            }
            
            // Parse flex columns for title
            val flexColumns = item.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() == 0) return null
            
            // Album title from first column
            val title = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: return null
            
            // Parse subtitle for artists, year, and type
            val subtitleRuns = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            
            val (artists, year, albumType, isExplicit) = parseSubtitle(subtitleRuns)
            
            // Parse thumbnails and upgrade to high resolution
            val thumbnails = parseThumbnails(item)
            
            return AlbumsResult(
                title = title,
                browseId = browseId,
                artists = artists,
                year = year,
                type = albumType,
                isExplicit = isExplicit,
                thumbnails = thumbnails
            )
            
        } catch (e: Exception) {
            android.util.Log.e("AlbumParser", "Error parsing album", e)
            return null
        }
    }
    
    /**
     * Check if subtitle explicitly marks this as an album.
     */
    private fun isExplicitlyAlbum(runs: JSONArray?): Boolean {
        if (runs == null) return false
        
        for (i in 0 until runs.length()) {
            val text = runs.optJSONObject(i)?.optString("text")?.lowercase() ?: continue
            if (text == "album" || text == "álbum" || text == "ep" || text == "single") {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Parse subtitle runs to extract artists, year, type, and explicit status.
     */
    private fun parseSubtitle(runs: JSONArray?): AlbumSubtitleInfo {
        val artists = mutableListOf<ArtistInfo>()
        var year = ""
        var albumType = "Album"
        var isExplicit = false
        
        if (runs == null) return AlbumSubtitleInfo(artists, year, albumType, isExplicit)
        
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text")
            if (text == " • " || text.isBlank()) continue
            
            val browseId = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            
            val lowerText = text.lowercase()
            
            when {
                // Type indicators
                lowerText == "album" || lowerText == "álbum" -> albumType = "Album"
                lowerText == "ep" -> albumType = "EP"
                lowerText == "single" || lowerText == "sencillo" -> albumType = "Single"
                
                // Year (4 digits)
                text.matches(Regex("\\d{4}")) -> year = text
                
                // Artist (has browse ID starting with UC)
                browseId?.startsWith("UC") == true -> {
                    artists.add(ArtistInfo(browseId, text))
                }
                
                // First non-type, non-year text is probably artist
                artists.isEmpty() && !text.matches(Regex("\\d+")) -> {
                    artists.add(ArtistInfo(null, text))
                }
            }
        }
        
        return AlbumSubtitleInfo(artists, year, albumType, isExplicit)
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
    
    private data class AlbumSubtitleInfo(
        val artists: List<ArtistInfo>,
        val year: String,
        val type: String,
        val isExplicit: Boolean
    )
}
