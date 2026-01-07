package com.amurayada.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.launch

class MusicApplication : Application(), ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Use optimized OkHttp (HTTP/2) for images too - Coil 2.x API
            .okHttpClient {
                com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB Aggressive Cache
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    fun clearImageCache() {
        android.util.Log.d("MusicApplication", "Clearing image cache...")
        val loader = coil.Coil.imageLoader(this)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        android.util.Log.d("MusicApplication", "Image cache cleared!")
    }
    
    companion object {
        private var instance: MusicApplication? = null
        fun getInstance(): MusicApplication? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 0. Initialize YouTube Music Client (SharedPreferences for visitorData persistence)
        com.amurayada.music.data.youtube.YouTubeMusicClient.init(this)
        
        // 0.5 Initialize Auth Manager for authenticated API requests
        val authManager = com.amurayada.music.data.auth.YouTubeAuthManager.getInstance(this)
        com.amurayada.music.data.youtube.YouTubeMusicClient.setAuthManager(authManager)
        
        // 1. Initialize NewPipe with Localization
        initNewPipe()
        
        // 2. Network Warm-up
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // Dummy request to open TCP/TLS connection
                val client = com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
                val request = okhttp3.Request.Builder()
                    .url("https://music.youtube.com")
                    .head() // Lightweight HEAD request
                    .build()
                client.newCall(request).execute().close()
                android.util.Log.d("MusicApp", "Network Warmed Up!")
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // 3. Initialize YoutubeDL
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun initNewPipe() {
        if (org.schabi.newpipe.extractor.NewPipe.getDownloader() == null) {
            org.schabi.newpipe.extractor.NewPipe.init(com.amurayada.music.data.newpipe.NewPipeDownloader())
        }
        
        try {
            // Force Localization to Colombia/Local to fix search relevancy (e.g. Morat)
            // Force Localization to Colombia/Local to fix search relevancy (e.g. Morat)
            // Note: Direct InnerTube implementation in SearchRepository handles "gl":"CO" directly.
            // NewPipe settings are read-only in this version or interface.
            /*
            val service = org.schabi.newpipe.extractor.NewPipe.getService(0)
            service.contentCountry = myCountry
            service.localization = myLanguage
            */
            android.util.Log.d("MusicApp", "NewPipe Init Complete (Localization handled in Direct API)")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
