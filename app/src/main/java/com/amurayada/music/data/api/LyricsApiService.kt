package com.amurayada.music.data.api

import com.amurayada.music.data.model.ApiResult
import com.amurayada.music.data.model.LyricResponseDTO
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for SimpMusic Lyrics API
 * Base URL: https://api-lyrics.simpmusic.org/v1
 */
interface LyricsApiService {
    
    /**
     * Search lyrics by song title
     * @param title Song title to search for
     * @param artist Optional artist name for better matching
     * @param limit Number of results to return (default: 10)
     * @param offset Pagination offset (default: 0)
     */
    @GET("search/title")
    suspend fun searchByTitle(
        @Query("title") title: String,
        @Query("artist") artist: String? = null,
        @Query("limit") limit: Int = 5,
        @Query("offset") offset: Int = 0
    ): ApiResult<List<LyricResponseDTO>>
    
    /**
     * Get lyrics by video ID (if available)
     * @param videoId YouTube video ID
     */
    @GET("{videoId}")
    suspend fun getLyricsByVideoId(
        @Path("videoId") videoId: String
    ): ApiResult<List<LyricResponseDTO>>
    
    /**
     * Full-text search across all lyrics
     * @param query Search query
     * @param limit Number of results to return
     * @param offset Pagination offset
     */
    @GET("search")
    suspend fun fullTextSearch(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
        @Query("offset") offset: Int = 0
    ): ApiResult<List<LyricResponseDTO>>
}
