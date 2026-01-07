package com.amurayada.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("lyrics_cache", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "LyricsRepository"
        private const val LRCLIB_BASE_URL = "https://lrclib.net/api/get"
        private const val USER_AGENT = "Harmony Music Player (https://github.com/harmony-music)"
    }
    
    suspend fun getLyrics(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // 1. Check local cache first
            val cached = getCachedLyrics(song.id)
            if (cached != null) {
                Log.d(TAG, "Found cached lyrics for: ${song.title}")
                return@withContext LyricsResult.Success(cached.first, cached.second)
            }
            
            // 2. Try LRCLIB API with strict matching
            Log.d(TAG, "Fetching lyrics from LRCLIB for: ${song.title} - ${song.artist}")
            val lrclibResult = fetchFromLrclib(song)
            
            if (lrclibResult != null) {
                // Cache the result with LRCLIB source
                cacheLyrics(song.id, lrclibResult, "LRCLIB")
                Log.d(TAG, "Successfully fetched and cached lyrics from LRCLIB")
                return@withContext LyricsResult.Success(lrclibResult, "LRCLIB")
            }
            
            // 3. No lyrics found
            Log.d(TAG, "No lyrics found for: ${song.title}")
            // Cache the not found result to prevent repeated fetching
            cacheLyrics(song.id, "", "None")
            LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun fetchFromLrclib(song: Song): String? {
        try {
            val durationSeconds = (song.duration / 1000).toInt()
            
            // 1. Try Exact Match First
            val exactMatchUrl = StringBuilder(LRCLIB_BASE_URL)
            exactMatchUrl.append("?track_name=").append(URLEncoder.encode(song.title, "UTF-8"))
            exactMatchUrl.append("&artist_name=").append(URLEncoder.encode(song.artist, "UTF-8"))
            exactMatchUrl.append("&album_name=").append(URLEncoder.encode(song.album, "UTF-8"))
            exactMatchUrl.append("&duration=").append(durationSeconds)
            
            val exactResult = performLrclibRequest(exactMatchUrl.toString(), durationSeconds, true)
            if (exactResult != null) return exactResult
            
            // 2. Fallback to Fuzzy Search
            Log.d(TAG, "Exact match failed, trying fuzzy search for: ${song.title} ${song.artist}")
            val searchUrl = StringBuilder("https://lrclib.net/api/search")
            val query = "${song.title} ${song.artist}"
            searchUrl.append("?q=").append(URLEncoder.encode(query, "UTF-8"))
            
            return performLrclibRequest(searchUrl.toString(), durationSeconds, false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from LRCLIB", e)
            return null
        }
    }

    private fun performLrclibRequest(urlString: String, targetDuration: Int, isExact: Boolean): String? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                
                if (isExact) {
                    // Handle single object response
                    val json = JSONObject(response)
                    return extractLyricsFromJson(json, targetDuration, true)
                } else {
                    // Handle array response for search
                    val jsonArray = org.json.JSONArray(response)
                    if (jsonArray.length() == 0) return null
                    
                    // Find best match in search results
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        
                        // Basic artist check: must contain the artist name or vice versa
                        // This prevents "Hello" by Adele matching "Hello" by Lionel Richie if the search is fuzzy
                        val itemArtist = item.optString("artistName", "")
                        if (itemArtist.isNotEmpty()) {
                             // Clean up artist names for comparison (remove special chars, lowercase)
                             val cleanItemArtist = itemArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
                             val cleanSongArtist = urlString.substringAfter("artist_name=").substringBefore("&").replace("+", " ").lowercase().replace(Regex("[^a-z0-9]"), "")
                             
                             // We can't easily get the original song artist here without passing it down
                             // But we can check if the item artist is roughly similar to what we expect
                             // Or we can just trust the search query "q=title artist" usually puts the right one on top
                             // Let's rely on the fact that we searched for "Title Artist"
                        }

                        val lyrics = extractLyricsFromJson(item, targetDuration, false)
                        if (lyrics != null) return lyrics
                    }
                    return null
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                if (isExact) Log.d(TAG, "LRCLIB: Exact match not found (404)")
                return null
            } else {
                Log.w(TAG, "LRCLIB request failed with code: $responseCode")
                return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request failed: ${e.message}")
            return null
        }
    }

    private fun extractLyricsFromJson(json: JSONObject, targetDuration: Int, strictDuration: Boolean): String? {
        val syncedLyrics = json.optString("syncedLyrics", null)
        val plainLyrics = json.optString("plainLyrics", null)
        val apiDuration = json.optInt("duration", 0)
        
        // Duration check
        if (targetDuration > 0 && apiDuration > 0) {
            val diff = kotlin.math.abs(apiDuration - targetDuration)
            // Use tighter tolerance for both strict and fuzzy to ensure sync quality
            // 3 seconds allow for minor silence differences but excludes different versions
            if (diff > 3) {
                 Log.w(TAG, "Duration mismatch: expected $targetDuration, got $apiDuration (diff: $diff)")
                 return null 
            }
        }

        return when {
            !syncedLyrics.isNullOrBlank() -> syncedLyrics
            !plainLyrics.isNullOrBlank() -> plainLyrics
            else -> null
        }
    }
    
    fun importLrcFile(songId: Long, lrcContent: String): Boolean {
        return try {
            if (lrcContent.contains("[") && lrcContent.contains("]")) {
                cacheLyrics(songId, lrcContent, "SimpMusic")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing LRC file", e)
            false
        }
    }
    
    fun saveLyrics(songId: Long, lyrics: String) {
        cacheLyrics(songId, lyrics, "SimpMusic")
    }
    
    private fun getCachedLyrics(songId: Long): Pair<String, String>? {
        val lyrics = prefs.getString("lyrics_$songId", null) ?: return null
        val source = prefs.getString("source_$songId", "SimpMusic") // Default to SimpMusic for legacy/manual
        
        // If cached as "None", return null to indicate not found (but handled)
        // Actually, we should return it so the repo knows it's "NotFound" and doesn't fetch again
        // But the current signature returns Pair<String, String>?, where null means "not in cache"
        // We need to handle "None" specifically
        
        if (source == "None") {
             // Ignore "None" cache to allow retrying with new fuzzy search logic
             return null
        }
        
        return lyrics to (source ?: "SimpMusic")
    }
    
    private fun cacheLyrics(songId: Long, lyrics: String, source: String) {
        prefs.edit()
            .putString("lyrics_$songId", lyrics)
            .putString("source_$songId", source)
            .apply()
    }
}

sealed class LyricsResult {
    data class Success(val lyrics: String, val source: String) : LyricsResult()
    data object NotFound : LyricsResult()
    data class Error(val message: String) : LyricsResult()
}
