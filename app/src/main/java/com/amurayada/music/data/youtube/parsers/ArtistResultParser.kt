package com.amurayada.music.data.youtube.parsers

import com.amurayada.music.data.model.ArtistsResult
import com.amurayada.music.data.model.Thumbnail
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for artist results from YouTube Music API.
 * Uses strict verification to only return verified YouTube Music artists,
 * not random user channels.
 */
object ArtistResultParser {
    
    /**
     * Parse a single artist item from the API response.
     * 
     * @param item The musicResponsiveListItemRenderer JSON object
     * @return ArtistsResult if valid verified artist, null otherwise
     */
    fun parse(item: JSONObject): ArtistsResult? {
        try {
            // Get browse ID - required
            val navigationEndpoint = item.optJSONObject("navigationEndpoint")
            val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            
            if (browseId.isNullOrEmpty()) return null
            
            // Parse flex columns for name
            val flexColumns = item.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() == 0) return null
            
            // Artist name from first column
            val name = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: return null
            
            // Parse subtitle for verification
            val subtitleRuns = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            
            // STRICT VERIFICATION: Must have "Artist" type OR subscriber count
            if (!isVerifiedArtist(subtitleRuns)) {
                android.util.Log.d("ArtistParser", "Skipping non-verified: $name ($browseId)")
                return null
            }
            
            // Parse thumbnails and upgrade to high resolution
            val thumbnails = parseThumbnails(item)
            
            // Try to get radio/shuffle IDs from menu
            val (radioId, shuffleId) = parseRadioShuffleIds(item)
            
            return ArtistsResult(
                artist = name,
                browseId = browseId,
                radioId = radioId,
                shuffleId = shuffleId,
                thumbnails = thumbnails
            )
            
        } catch (e: Exception) {
            android.util.Log.e("ArtistParser", "Error parsing artist", e)
            return null
        }
    }
    
    /**
     * Verify if this is a legitimate YouTube Music artist.
     * Must explicitly say "Artist" type OR have subscriber count.
     */
    private fun isVerifiedArtist(runs: JSONArray?): Boolean {
        if (runs == null) return false
        
        for (i in 0 until runs.length()) {
            val text = runs.optJSONObject(i)?.optString("text")?.lowercase() ?: continue
            
            // Check for artist type indicator
            if (text == "artist" || text == "artista") return true
            
            // Check for subscriber count (e.g., "1.5M subscribers")
            if (text.contains("subscriber") || text.contains("suscriptor")) return true
        }
        
        return false
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
     * Try to extract radio and shuffle playlist IDs from menu items.
     */
    private fun parseRadioShuffleIds(item: JSONObject): Pair<String, String> {
        var radioId = ""
        var shuffleId = ""
        
        val menu = item.optJSONObject("menu") ?: return Pair(radioId, shuffleId)
        val menuItems = menu.optJSONObject("menuRenderer")?.optJSONArray("items") ?: return Pair(radioId, shuffleId)
        
        for (i in 0 until menuItems.length()) {
            val menuItem = menuItems.optJSONObject(i)?.optJSONObject("menuNavigationItemRenderer") ?: continue
            val endpoint = menuItem.optJSONObject("navigationEndpoint")
            
            val watchPlaylistEndpoint = endpoint?.optJSONObject("watchPlaylistEndpoint")
            val playlistId = watchPlaylistEndpoint?.optString("playlistId") ?: continue
            
            val iconType = menuItem.optJSONObject("icon")?.optString("iconType")
            
            when {
                iconType == "MIX" || playlistId.startsWith("RDAMPL") -> radioId = playlistId
                iconType == "SHUFFLE" -> shuffleId = playlistId
            }
        }
        
        return Pair(radioId, shuffleId)
    }
}
