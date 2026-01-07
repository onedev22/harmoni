package com.amurayada.music.data.cache

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Manages caching of stream URLs and intelligent pre-loading
 */
class CacheManager {
    internal data class CacheEntry(
        val url: String,
        val duration: Long = 0, // seconds
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // LRU cache for stream URLs (max 50 entries)
    private val urlCache = LruCache<String, CacheEntry>(50)
    
    // LRU cache for Canva (video) URLs (max 30 entries)
    private val canvaCache = LruCache<String, CacheEntry>(30)
    
    private val cacheMutex = Mutex()
    
    // Cache expiration time (24 hours)
    private val cacheExpirationMs = TimeUnit.HOURS.toMillis(24)
    
    /**
     * Get cached stream URL for a song
     * @param songUrl YouTube watch URL
     * @return Cached stream URL or null if not cached or expired
     */
    suspend fun getCachedStreamUrl(songUrl: String): String? = cacheMutex.withLock {
        val entry = urlCache.get(songUrl) ?: return@withLock null
        
        // Check if cache entry is expired
        if (System.currentTimeMillis() - entry.timestamp > cacheExpirationMs) {
            urlCache.remove(songUrl)
            return@withLock null
        }
        
        entry.url
    }
    
    /**
     * Cache a stream URL
     * @param songUrl YouTube watch URL
     * @param streamUrl Direct stream URL
     */
    internal suspend fun cacheStreamUrl(songUrl: String, streamUrl: String) = cacheMutex.withLock {
        urlCache.put(songUrl, CacheEntry(streamUrl))
    }
    
    /**
     * Get cached Canva (video) URL for a song
     */
    internal suspend fun getCachedCanvaUrl(songUrl: String): Pair<String, Long>? = cacheMutex.withLock {
        val entry = canvaCache.get(songUrl) ?: return@withLock null
        if (System.currentTimeMillis() - entry.timestamp > cacheExpirationMs) {
            canvaCache.remove(songUrl)
            return@withLock null
        }
        Pair(entry.url, entry.duration)
    }
    
    /**
     * Cache a Canva (video) URL with duration
     */
    internal suspend fun cacheCanvaUrl(songUrl: String, videoUrl: String, duration: Long): Unit = cacheMutex.withLock {
        canvaCache.put(songUrl, CacheEntry(videoUrl, duration))
        Unit
    }
    
    /**
     * Check if a URL is cached and valid
     */
    suspend fun isCached(songUrl: String): Boolean = cacheMutex.withLock {
        val entry = urlCache.get(songUrl) ?: return@withLock false
        System.currentTimeMillis() - entry.timestamp <= cacheExpirationMs
    }
    
    /**
     * Clear expired cache entries
     */
    suspend fun clearExpiredEntries() = cacheMutex.withLock {
        val snapshot = urlCache.snapshot()
        val currentTime = System.currentTimeMillis()
        
        snapshot.forEach { (key, entry) ->
            if (currentTime - entry.timestamp > cacheExpirationMs) {
                urlCache.remove(key)
            }
        }
    }
    
    /**
     * Clear all cache entries
     */
    suspend fun clearAll() = cacheMutex.withLock {
        urlCache.evictAll()
        canvaCache.evictAll()
    }
    
    /**
     * Get cache statistics
     */
    internal suspend fun getCacheStats(): CacheStats = cacheMutex.withLock {
        val snapshot = urlCache.snapshot()
        val currentTime = System.currentTimeMillis()
        val validEntries = snapshot.count { (_, entry) ->
            currentTime - entry.timestamp <= cacheExpirationMs
        }
        
        CacheStats(
            totalEntries = snapshot.size,
            validEntries = validEntries,
            expiredEntries = snapshot.size - validEntries,
            maxSize = urlCache.maxSize()
        )
    }
    
    internal data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int,
        val maxSize: Int
    )
    
    companion object {
        @Volatile
        private var instance: CacheManager? = null
        
        fun getInstance(): CacheManager {
            return instance ?: synchronized(this) {
                instance ?: CacheManager().also { instance = it }
            }
        }
    }
}
