package com.amurayada.music.data.model

import com.google.gson.annotations.SerializedName

/**
 * API response wrapper from SimpMusic Lyrics API
 */
data class ApiResult<T>(
    @SerializedName("data")
    val data: T? = null,
    
    @SerializedName("success")
    val success: Boolean = false,
    
    @SerializedName("error")
    val error: LyricsError? = null
)

/**
 * Error response from API
 */
data class LyricsError(
    @SerializedName("error")
    val isError: Boolean = true,
    
    @SerializedName("code")
    val code: Int = 0,
    
    @SerializedName("reason")
    val reason: String = ""
)

/**
 * Main lyrics response DTO
 */
data class LyricResponseDTO(
    @SerializedName("videoId")
    val videoId: String = "",
    
    @SerializedName("title")
    val title: String = "",
    
    @SerializedName("artist")
    val artist: String = "",
    
    @SerializedName("album")
    val album: String? = null,
    
    @SerializedName("duration")
    val duration: Long = 0,
    
    @SerializedName("syncedLyrics")
    val syncedLyrics: List<SyncedLyric>? = null,
    
    @SerializedName("plainLyrics")
    val plainLyrics: String? = null,
    
    @SerializedName("source")
    val source: String? = null,
    
    @SerializedName("upvotes")
    val upvotes: Int = 0,
    
    @SerializedName("downvotes")
    val downvotes: Int = 0
)

/**
 * Individual synced lyric line
 */
data class SyncedLyric(
    @SerializedName("time")
    val time: Long = 0, // Time in milliseconds
    
    @SerializedName("text")
    val text: String = ""
)

/**
 * Search result wrapper
 */
data class LyricsSearchResult(
    val lyrics: List<LyricResponseDTO> = emptyList()
)
