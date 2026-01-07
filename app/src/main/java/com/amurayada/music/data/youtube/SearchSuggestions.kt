package com.amurayada.music.data.youtube

import com.amurayada.music.data.newpipe.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Search suggestions provider for YouTube Music.
 * Supports both Google Suggest API and YouTube Music's native suggestions.
 */
object SearchSuggestions {
    
    private const val GOOGLE_SUGGEST_URL = "https://suggestqueries.google.com/complete/search"
    private const val YTM_SUGGESTIONS_URL = "${YouTubeMusicClient.BASE_URL}music/get_search_suggestions"
    
    /**
     * Get search suggestions from Google Suggest API.
     * Fast and reliable, returns plain text suggestions.
     * 
     * @param query The search query
     * @return List of suggestion strings
     */
    suspend fun getGoogleSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        try {
            val url = "$GOOGLE_SUGGEST_URL?client=firefox&ds=yt&q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .build()
            
            val client = NewPipeDownloader.getClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                response.close()
                return@withContext emptyList()
            }
            
            val responseString = response.body?.string() ?: return@withContext emptyList()
            
            // Response format: ["query", ["suggestion1", "suggestion2", ...]]
            val jsonArray = JSONArray(responseString)
            val suggestions = jsonArray.optJSONArray(1) ?: return@withContext emptyList()
            
            val result = mutableListOf<String>()
            for (i in 0 until suggestions.length()) {
                suggestions.optString(i)?.let { 
                    if (it.isNotBlank()) result.add(it)
                }
            }
            
            result.take(8) // Limit to 8 suggestions
            
        } catch (e: Exception) {
            android.util.Log.e("SearchSuggestions", "Error getting Google suggestions", e)
            emptyList()
        }
    }
    
    /**
     * Get search suggestions from YouTube Music API.
     * Returns both text suggestions and recommended items.
     * 
     * @param query The search query
     * @return SearchSuggestionsResult with queries and optional items
     */
    suspend fun getYTMusicSuggestions(query: String): SearchSuggestionsResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchSuggestionsResult()
        
        try {
            val locale = YouTubeMusicClient.getLocale()
            
            val requestBody = """
                {
                    "context": ${YouTubeMusicClient.buildContext(locale)},
                    "input": "$query"
                }
            """.trimIndent()
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(YTM_SUGGESTIONS_URL)
                .post(body)
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .header("Referer", YouTubeMusicClient.REFERER)
                .header("Origin", "https://music.youtube.com")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", YouTubeMusicClient.INNER_TUBE_NAME.toString())
                .header("X-YouTube-Client-Version", YouTubeMusicClient.CLIENT_VERSION)
                .header("X-Goog-Visitor-Id", YouTubeMusicClient.visitorData)
                .build()
            
            val client = NewPipeDownloader.getClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                response.close()
                return@withContext SearchSuggestionsResult()
            }
            
            val responseString = response.body?.string() ?: return@withContext SearchSuggestionsResult()
            parseYTMSuggestionsResponse(responseString)
            
        } catch (e: Exception) {
            android.util.Log.e("SearchSuggestions", "Error getting YTM suggestions", e)
            SearchSuggestionsResult()
        }
    }
    
    /**
     * Parse YouTube Music suggestions response.
     */
    private fun parseYTMSuggestionsResponse(response: String): SearchSuggestionsResult {
        val queries = mutableListOf<String>()
        
        try {
            val json = JSONObject(response)
            
            // Navigate to suggestions
            val contents = json.optJSONObject("contents")
                ?.optJSONArray("searchSuggestionsSectionListRenderer")
                ?.getJSONObject(0)
                ?.optJSONObject("searchSuggestionsSectionRenderer")
                ?.optJSONArray("contents")
            
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
                    
                    // Text suggestions
                    val suggestionRenderer = item?.optJSONObject("searchSuggestionRenderer")
                    if (suggestionRenderer != null) {
                        val suggestion = suggestionRenderer.optJSONObject("suggestion")
                            ?.optJSONArray("runs")
                        
                        if (suggestion != null) {
                            val sb = StringBuilder()
                            for (j in 0 until suggestion.length()) {
                                sb.append(suggestion.optJSONObject(j)?.optString("text") ?: "")
                            }
                            if (sb.isNotBlank()) queries.add(sb.toString())
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SearchSuggestions", "Error parsing YTM suggestions", e)
        }
        
        return SearchSuggestionsResult(queries = queries.take(8))
    }
    
    /**
     * Combined suggestions - tries YTM first, falls back to Google.
     */
    suspend fun getSuggestions(query: String): List<String> {
        val ytmResult = getYTMusicSuggestions(query)
        if (ytmResult.queries.isNotEmpty()) {
            return ytmResult.queries
        }
        return getGoogleSuggestions(query)
    }
}

/**
 * Result from YouTube Music suggestions API.
 */
data class SearchSuggestionsResult(
    val queries: List<String> = emptyList()
)
