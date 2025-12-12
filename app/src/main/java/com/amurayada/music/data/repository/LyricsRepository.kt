package com.amurayada.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amurayada.music.data.api.LyricsApiService
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.model.SyncedLyric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Repository for managing lyrics from SimpMusic API and local cache
 */
class LyricsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("lyrics_cache", Context.MODE_PRIVATE)
    
    private val api: LyricsApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        Retrofit.Builder()
            .baseUrl("https://api-lyrics.simpmusic.org/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsApiService::class.java)
    }
    
    /**
     * Get lyrics for a song from local cache only
     */
    suspend fun getLyrics(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // Check local cache
            val cached = getCachedLyrics(song.id)
            if (cached != null) {
                Log.d("LyricsRepository", "Found cached lyrics for: ${song.title}")
                return@withContext LyricsResult.Success(cached)
            }
            
            // No lyrics found in cache
            Log.d("LyricsRepository", "No cached lyrics for: ${song.title}")
            LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Error loading lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Import lyrics from LRC file content
     */
    fun importLrcFile(songId: Long, lrcContent: String): Boolean {
        return try {
            // Validate LRC format
            if (lrcContent.contains("[") && lrcContent.contains("]")) {
                cacheLyrics(songId, lrcContent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Error importing LRC file", e)
            false
        }
    }
    
    /**
     * Save lyrics manually (user edited)
     */
    fun saveLyrics(songId: Long, lyrics: String) {
        cacheLyrics(songId, lyrics)
    }
    
    /**
     * Get cached lyrics from SharedPreferences
     */
    private fun getCachedLyrics(songId: Long): String? {
        return prefs.getString("lyrics_$songId", null)
    }
    
    /**
     * Cache lyrics to SharedPreferences
     */
    private fun cacheLyrics(songId: Long, lyrics: String) {
        prefs.edit().putString("lyrics_$songId", lyrics).apply()
    }
}

/**
 * Result states for lyrics fetching
 */
sealed class LyricsResult {
    data class Success(val lyrics: String) : LyricsResult()
    data object NotFound : LyricsResult()
    data class Error(val message: String) : LyricsResult()
}
