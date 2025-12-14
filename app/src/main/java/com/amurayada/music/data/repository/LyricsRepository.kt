package com.amurayada.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("lyrics_cache", Context.MODE_PRIVATE)
    
    suspend fun getLyrics(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        try {
            val cached = getCachedLyrics(song.id)
            if (cached != null) {
                Log.d("LyricsRepository", "Found cached lyrics for: ${song.title}")
                return@withContext LyricsResult.Success(cached)
            }
            
            Log.d("LyricsRepository", "No cached lyrics for: ${song.title}")
            LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Error loading lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    fun importLrcFile(songId: Long, lrcContent: String): Boolean {
        return try {
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
    
    fun saveLyrics(songId: Long, lyrics: String) {
        cacheLyrics(songId, lyrics)
    }
    
    private fun getCachedLyrics(songId: Long): String? {
        return prefs.getString("lyrics_$songId", null)
    }
    
    private fun cacheLyrics(songId: Long, lyrics: String) {
        prefs.edit().putString("lyrics_$songId", lyrics).apply()
    }
}

sealed class LyricsResult {
    data class Success(val lyrics: String) : LyricsResult()
    data object NotFound : LyricsResult()
    data class Error(val message: String) : LyricsResult()
}

