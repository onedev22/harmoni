package com.amurayada.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.amurayada.music.R
import com.amurayada.music.widget.MusicWidget
import com.amurayada.music.widget.MusicWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MusicPlaybackService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        
        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        
        // Add listener for widget updates
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidget()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
            }
        })
        
        // Create MediaSession
        mediaSession = MediaSession.Builder(this, player).build()

        // Fix ANR: Start foreground immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "music_playback_channel"
            val channelName = "Music Playback"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Music Player")
                .setContentText("Ready to play")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            try {
                startForeground(1, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_PLAY_PAUSE") {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } else if (intent?.action == "ACTION_TOGGLE_FAVORITE") {
            toggleFavorite()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun toggleFavorite() {
        val mediaItem = player.currentMediaItem ?: return
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        val prefs = getSharedPreferences("playback_data", Context.MODE_PRIVATE)
        val favoritesJson = prefs.getString("favorites", null)
        val favoritesList = mutableListOf<JSONObject>()
        
        if (favoritesJson != null) {
            try {
                val jsonArray = JSONArray(favoritesJson)
                for (i in 0 until jsonArray.length()) {
                    favoritesList.add(jsonArray.getJSONObject(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val existingIndex = favoritesList.indexOfFirst { it.optLong("id") == songId }
        if (existingIndex != -1) {
            // Remove
            favoritesList.removeAt(existingIndex)
        } else {
            // Add
            val metadata = mediaItem.mediaMetadata
            val songJson = JSONObject().apply {
                put("id", songId)
                put("title", metadata.title?.toString() ?: "Unknown")
                put("artist", metadata.artist?.toString() ?: "Unknown")
                put("album", metadata.albumTitle?.toString() ?: "Unknown")
                put("duration", player.duration)
                put("path", "") // Path not available here, but seemingly not critical for UI
                put("albumArtUri", metadata.artworkUri?.toString() ?: "")
                put("dateAdded", System.currentTimeMillis())
                put("albumId", 0L) // Not easily available here
            }
            favoritesList.add(0, songJson)
        }

        // Save back
        val newJsonArray = JSONArray()
        favoritesList.take(100).forEach { newJsonArray.put(it) }
        prefs.edit().putString("favorites", newJsonArray.toString()).apply()
        
        updateWidget()
    }

    private fun isSongFavorite(songId: Long): Boolean {
        val prefs = getSharedPreferences("playback_data", Context.MODE_PRIVATE)
        val favoritesJson = prefs.getString("favorites", null) ?: return false
        try {
            val jsonArray = JSONArray(favoritesJson)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (item.optLong("id") == songId) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun updateWidget() {
        // Capture state on Main Thread
        val mediaItem = player.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString() ?: MusicWidgetState.DEFAULT_TITLE
        val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: MusicWidgetState.DEFAULT_ARTIST
        val isPlaying = player.isPlaying
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
        val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
        val isFavorite = if (songId != -1L) isSongFavorite(songId) else false

        CoroutineScope(Dispatchers.IO).launch {
            val manager = GlanceAppWidgetManager(applicationContext)
            val widget = MusicWidget()
            val glanceIds = manager.getGlanceIds(MusicWidget::class.java)
            
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(applicationContext, glanceId) { prefs ->
                    prefs[MusicWidgetState.titleKey] = title
                    prefs[MusicWidgetState.artistKey] = artist
                    prefs[MusicWidgetState.isPlayingKey] = isPlaying
                    if (artworkUri != null) {
                        prefs[MusicWidgetState.coverUriKey] = artworkUri
                    } else {
                        prefs.remove(MusicWidgetState.coverUriKey)
                    }
                    prefs[MusicWidgetState.isFavoriteKey] = isFavorite
                }
                widget.update(applicationContext, glanceId)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
