package com.amurayada.music.data.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider

import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object AudioCache {
    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_SIZE: Long = 200 * 1024 * 1024 // 200MB

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }
}
