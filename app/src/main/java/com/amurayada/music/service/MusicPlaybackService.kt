package com.amurayada.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.amurayada.music.R
import com.amurayada.music.MainActivity
import com.amurayada.music.widget.MusicWidget
import com.amurayada.music.widget.MusicWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@UnstableApi
class MusicPlaybackService : MediaSessionService() {
    
    // Sesión principal de Media3 que expone los controles a sistema y otras apps
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    
    // Monitor de desconexión de auriculares para pausar automáticamente
    private var headphoneMonitor: HeadphoneUsageMonitor? = null
    
    // Bloqueos de energía y WiFi para garantizar reproducción en segundo plano sin interrupciones (Doze mode)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    
    private val NOTIFICATION_ID = 1
    // Mantenemos la notificación visible incluso en pausa para evitar que el sistema mate el servicio.
    // Esto se logra no llamando a stopForeground en Pause, solo en Stop explícito.
    private val CHANNEL_ID = "music_playback_channel"
    
    // Variables de gestión manual de AudioFocus (Legacy/Fallback)
    private lateinit var audioManager: android.media.AudioManager
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false // Bandera de estado previo
    
    // GUARDIA DE TIMEOUT DE REPRODUCCIÓN: Previene bucles infinitos de "Buffering"
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var playbackTimeoutJob: Job? = null
    private var currentFailedMediaId: String? = null
    private var idleRetryCount = 0
    private val MAX_IDLE_RETRIES = 3
    private val PLAYBACK_TIMEOUT_MS = 15_000L // 15 segundos para iniciar reproducción antes de forzar salto

    /**
     * Listener para cambios de foco de audio.
     * Gestiona interrupciones por llamadas, otras apps de música o asistentes de voz.
     */
    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                // Recuperamos el foco total: Restauramos volumen y replay si corresponde
                if (wasPlayingBeforeFocusLoss && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                    player.play()
                }
                player.volume = 1.0f
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                 // Pérdida temporal (ej. notificación o llamada corta): Pausamos pero MANTENEMOS el servicio vivo
                if (player.isPlaying) {
                     wasPlayingBeforeFocusLoss = true
                     player.pause()
                } else {
                     wasPlayingBeforeFocusLoss = false
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.volume = 0.2f // Ducking: Bajamos volumen sin pausar (ej. indicaciones GPS)
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // Pérdida permanente (otra app de música inició):
                // PAUSAMOS por cortesía, pero NO DETENEMOS EL SERVICIO (Requisito de usuario)
                if (player.isPlaying) {
                     wasPlayingBeforeFocusLoss = false // Generalmente no reanudamos auto tras pérdida permanente
                     player.pause()
                }
                // No abandonamos el foco explícitamente aquí para mantener viva la sesión
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize ExoPlayer
        player = buildNewPlayer()
        
        // Initialize Equalizer
        com.amurayada.music.utils.AudioEffectsManager.init(this)
        com.amurayada.music.utils.AudioEffectsManager.audioSessionId = player.audioSessionId
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                com.amurayada.music.utils.AudioEffectsManager.audioSessionId = audioSessionId
            }
        })
        






        // Restauración de Cola y Estado Completo (playlist, canción actual, posición)
        val prefs = getSharedPreferences("playback_data", Context.MODE_PRIVATE)
        val queueJson = prefs.getString("current_queue", null)
        
        if (queueJson != null) {
            try {
                val jsonArray = org.json.JSONArray(queueJson)
                val mediaItems = mutableListOf<androidx.media3.common.MediaItem>()
                
                for (i in 0 until jsonArray.length()) {
                    val songJson = jsonArray.getJSONObject(i)
                    val id = songJson.getLong("id")
                    val title = songJson.getString("title")
                    val artist = songJson.getString("artist")
                    val album = songJson.getString("album")
                    val duration = songJson.getLong("duration")
                    val albumArtUri = if (songJson.optString("albumArtUri", "").isNotEmpty()) android.net.Uri.parse(songJson.getString("albumArtUri")) else null
                    val path = songJson.getString("path")
                    val dateAdded = songJson.optLong("dateAdded", 0L)
                    val albumId = songJson.optLong("albumId", 0L)

                    val song = com.amurayada.music.data.model.Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        albumArtUri = albumArtUri,
                        path = path,
                        dateAdded = dateAdded,
                        albumId = albumId
                    )
                    
                    mediaItems.add(createMediaItemFromSong(song))
                }
                
                if (mediaItems.isNotEmpty()) {
                    val currentIndex = prefs.getInt("current_index", 0)
                    val currentPos = prefs.getLong("current_position", 0L)
                    
                    val safeIndex = currentIndex.coerceIn(0, mediaItems.size - 1)
                    player.setMediaItems(mediaItems, safeIndex, currentPos)
                    player.prepare()
                    android.util.Log.d("MusicPlaybackService", "Restored queue: ${mediaItems.size} items, index: $safeIndex, pos: $currentPos")
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlaybackService", "Error restoring queue", e)
            }
        } else {
            // Fallback: Si no hay cola guardada, intentamos restaurar desde el historial (Legacy o primer inicio tras actualización)
            val historyJson = prefs.getString("history", null)
            if (historyJson != null) {
                try {
                    val jsonArray = JSONArray(historyJson)
                    if (jsonArray.length() > 0) {
                        val lastSongJson = jsonArray.getJSONObject(0)
                        val lastSong = com.amurayada.music.data.model.Song(
                            id = lastSongJson.getLong("id"),
                            title = lastSongJson.getString("title"),
                            artist = lastSongJson.getString("artist"),
                            album = lastSongJson.getString("album"),
                            duration = lastSongJson.getLong("duration"),
                            albumArtUri = if (lastSongJson.optString("albumArtUri", "").isNotEmpty()) android.net.Uri.parse(lastSongJson.getString("albumArtUri")) else null,
                            path = lastSongJson.getString("path"),
                            dateAdded = lastSongJson.optLong("dateAdded", 0L),
                            albumId = lastSongJson.optLong("albumId", 0L)
                        )
                        player.setMediaItem(createMediaItemFromSong(lastSong))
                        player.prepare()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // Initialize Headphone Monitor
        headphoneMonitor = HeadphoneUsageMonitor(this) {
            // Callback para pausar reproducción al desconectar
            if (player.isPlaying) {
                player.pause()
            }
        }
        
        
        // Agregamos listeners para actualizaciones de UI (Widget/Notificación)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // REFUERZO DE CICLO DE VIDA: Iniciamos el servicio explícitamente de nuevo para renovar el periodo de gracia.
                // Esto previene que el sistema mate la app si considera que el "comando previo" terminó.
                val restartIntent = Intent(applicationContext, MusicPlaybackService::class.java)
                startService(restartIntent)
                updateWidget()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) isLoading = false // Reset de estado de carga
                headphoneMonitor?.onIsPlayingChanged(isPlaying)
                // Mantenemos el WakeLock si está reproduciendo O bufferizando
                val isBuffering = player.playbackState == Player.STATE_BUFFERING
                manageWakeLock(isPlaying || isBuffering)
                updateWidget()
            }


            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MusicPlaybackService", "❌ ERROR FATAL DEL REPRODUCTOR (Principal): ${error.message}")
                android.util.Log.e("MusicPlaybackService", "   - Nombre Error: ${error.errorCodeName}")
                android.util.Log.e("MusicPlaybackService", "   - Causa: ${error.cause?.message}")
                android.util.Log.e("MusicPlaybackService", "   - ID Media: ${player.currentMediaItem?.mediaId}")
                android.util.Log.e("MusicPlaybackService", "   - URI: ${player.currentMediaItem?.localConfiguration?.uri}")
                
                 android.os.Handler(android.os.Looper.getMainLooper()).post {
                     recreatePlayerPreservingSession()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isPlaying = player.isPlaying
                val isBuffering = playbackState == Player.STATE_BUFFERING
                manageWakeLock(isPlaying || isBuffering || playbackState == Player.STATE_IDLE)
                updateWidget()
                
                // Focus Manual: Solicitamos en Play O Buffering (NUNCA soltamos proactivamente)
                if (isPlaying || isBuffering) {
                    requestAudioFocus()
                }
                
                when (playbackState) {
                    Player.STATE_READY -> {
                         // ¡ÉXITO! La reproducción inició, reseteamos todos los guardias
                        cancelPlaybackTimeout()
                        resetIdleRetryCounter()
                        android.util.Log.d("MusicPlaybackService", "✅ Player READY - reproducción iniciada correctamente")
                    }
                    
                    Player.STATE_BUFFERING -> {
                        // Iniciamos cuenta regresiva de timeout al comenzar el buffering
                        if (player.playWhenReady) {
                            startPlaybackTimeout()
                            android.util.Log.d("MusicPlaybackService", "⏳ Buffering... guardia de timeout activo")
                        }
                    }
                    
                    Player.STATE_IDLE -> {
                        if (player.playWhenReady && player.currentMediaItem != null) {
                            val currentMediaId = player.currentMediaItem?.mediaId
                            
                            // Rastreamos si es la misma canción la que falla
                            if (currentMediaId != currentFailedMediaId) {
                                idleRetryCount = 0
                                currentFailedMediaId = currentMediaId
                            }
                            
                            idleRetryCount++
                            
                            if (idleRetryCount <= MAX_IDLE_RETRIES) {
                                android.util.Log.w("MusicPlaybackService", "🔄 WATCHDOG: Reintento IDLE $idleRetryCount/$MAX_IDLE_RETRIES - llamando prepare()")
                                startPlaybackTimeout()
                                player.prepare()
                            } else {
                                android.util.Log.e("MusicPlaybackService", "❌ WATCHDOG: Límites de reintentos excedidos para esta canción!")
                                forceSkipToNext("Máximos reintentos excedidos")
                            }
                        }
                    }
                    
                    Player.STATE_ENDED -> {
                        cancelPlaybackTimeout()
                        // Forzamos reproducción si la cola tiene más items (autoplay manual)
                        if (player.mediaItemCount > 0 && player.hasNextMediaItem()) {
                            android.util.Log.i("MusicPlaybackService", "⏭️ Canción terminada, reproduciendo siguiente en cola")
                            resetIdleRetryCounter()
                            player.seekToNextMediaItem()
                            player.play()
                            startPlaybackTimeout()
                        }
                    }
                }
            }
        })
        
        // "El Truco SimpMusic" (Estado de Reproducción Falso - ULTIMATE)
        // Envolvemos el player real para MENTIR a la MediaSession sobre estados de buffering/idle.
        // Esto evita que el sistema vea una aplicación "pausada/estancada" y cierre el servicio.
        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            private val notificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            private var lastArtworkUri: String? = null
            private var artworkData: ByteArray? = null

            override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
                val metadata = super.getMediaMetadata()
                val artworkUri = metadata.artworkUri?.toString()
                
                // Mantiene el artwork actualizado en la notificación
                if (artworkUri != null && artworkUri.startsWith("http")) {
                    if (artworkUri != lastArtworkUri) {
                        lastArtworkUri = artworkUri
                        notificationScope.launch {
                            try {
                                val bitmap = downloadBitmap(artworkUri)
                                if (bitmap != null) {
                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                    artworkData = stream.toByteArray()
                                    
                                    android.util.Log.d("MusicPlaybackService", "Artwork capturado como ByteArray para notificación: ${artworkData?.size} bytes")
                                    
                                    // Forzamos actualización de metadatos para que Media3 refresque la notificación
                                    mediaSession?.setCustomLayout(emptyList())
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MusicPlaybackService", "Fallo al capturar datos de artwork", e)
                            }
                        }
                    }
                    
                    if (artworkData != null) {
                        return metadata.buildUpon()
                            .setArtworkData(artworkData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                    }
                }
                return metadata
            }

            override fun getPlaybackState(): Int {
                val realState = super.getPlaybackState()
                val wantsToPlay = super.getPlayWhenReady()
                // MENTIRA PIADOSA: Si QUEREMOS reproducir pero estamos pegados en BUFFERING o IDLE, decimos que estamos READY.
                if (wantsToPlay && (realState == Player.STATE_BUFFERING || realState == Player.STATE_IDLE)) {
                    return Player.STATE_READY
                }
                return realState
            }
            
            // CRÍTICO: Sobrescribimos isPlaying() para retornar TRUE cuando intentamos reproducir.
            // Esto evita que Android piense que hemos pausado durante un buffering largo.
            override fun isPlaying(): Boolean {
                val wantsToPlay = super.getPlayWhenReady()
                val realState = super.getPlaybackState()
                // Si queremos reproducir y estamos en cualquier estado "de trabajo", mentimos diciendo que suena.
                if (wantsToPlay && (realState == Player.STATE_READY || realState == Player.STATE_BUFFERING)) {
                    return true
                }
                return super.isPlaying()
            }
        }
        
        // Create MediaSession with the Forwarding Player
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
        
        // Provider de Notificaciones Media3 con configuración para persistencia (Ongoing)
        val notificationProvider = object : androidx.media3.session.DefaultMediaNotificationProvider(this) {
            override fun addNotificationActions(
                mediaSession: MediaSession,
                mediaButtons: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                builder: androidx.core.app.NotificationCompat.Builder,
                actionFactory: androidx.media3.session.MediaNotification.ActionFactory
            ): IntArray {
                // Forzar Ongoing = true para evitar el descarte por swipe
                builder.setOngoing(true)
                builder.setAutoCancel(false)
                
                // Forzar canal correcto
                builder.setChannelId(CHANNEL_ID)
                
                // Delegar botones a la implementación base
                return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
            }
        }.apply { 
             setSmallIcon(R.drawable.ic_notification_custom)
        }
        
        // 🎯 FIX: Custom Notification Builder para interceptar y arreglar URIs de Artwork
        // SystemUI a menudo falla al leer URIs content:// si son externos o transitorios.
        // Proveeremos un builder personalizado si es necesario, pero el provider superior es un buen punto de partida.
        
        setMediaNotificationProvider(notificationProvider)

        // Create notification channel (ensure it exists)
        createNotificationChannel()
        
        // Set intent to open app when clicking notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession?.setSessionActivity(pendingIntent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.notification_channel_name)
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT // Increased importance
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    // ============ GUARDIA DE TIMEOUT ============
    // Evita cargas infinitas forzando un salto de canción tras exceder el tiempo límite
    
    private fun startPlaybackTimeout() {
        playbackTimeoutJob?.cancel()
        playbackTimeoutJob = serviceScope.launch {
            delay(PLAYBACK_TIMEOUT_MS)
            // Si el timeout expira y el reproductor sigue sin arrancar, forzamos la acción
            if (!player.isPlaying && player.playWhenReady) {
                android.util.Log.e("MusicPlaybackService", "⏱️ TIMEOUT: La reproducción falló en iniciar tras ${PLAYBACK_TIMEOUT_MS/1000}s. Saltando...")
                forceSkipToNext("Timeout de Buffering agotado")
            }
        }
    }
    
    private fun cancelPlaybackTimeout() {
        playbackTimeoutJob?.cancel()
        playbackTimeoutJob = null
    }
    
    private fun forceSkipToNext(reason: String) {
        android.util.Log.e("MusicPlaybackService", "🚨 RECUPERACIÓN DE ERROR: $reason - Saltando pista problemática")
        
        // Limpiamos contadores de error
        idleRetryCount = 0
        currentFailedMediaId = null
        
        // Detener reproducción actual (fundamental para limpiar buffer corrupto)
        player.stop()
        
        // Intentar reproducción del siguiente item
        if (player.hasNextMediaItem()) {
            android.util.Log.i("MusicPlaybackService", "⏭️ Intentando reproducir siguiente pista...")
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
            startPlaybackTimeout() // Reiniciamos guardia para la nueva pista
        } else {
            android.util.Log.w("MusicPlaybackService", "🛑 Fin de la cola alcanzado tras error. Deteniendo servicio.")
            player.playWhenReady = false
        }
    }
    
    private fun resetIdleRetryCounter() {
        idleRetryCount = 0
        currentFailedMediaId = null
    }

    
    private var isLoading = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY_PAUSE" -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            "ACTION_TOGGLE_FAVORITE" -> toggleFavorite()
            "ACTION_LOADING" -> {
                isLoading = true
                manageWakeLock(player.isPlaying)
            }
            "ACTION_CANCEL_LOADING" -> {
                isLoading = false
                manageWakeLock(player.isPlaying)
            }
            "ACTION_STOP" -> {
                // User explicitly wants to stop everything
                player.stop()
                abandonAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        // REINFORCEMENT: If just paused, try to keep foreground
        // (Media3 handles this mostly, but we ensure we don't call stopSelf here)
        
        // MediaSessionService handles foreground internally with the provider
        return android.app.Service.START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    
    // ...

    private fun manageWakeLock(isPlaying: Boolean) {
        if (isPlaying || isLoading) {
            // CPU WakeLock
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MusicApp::PlaybackWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
            
            // Wifi Lock (Keep radio awake for online streaming)
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                wifiLock = wifiManager.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF 
                    else 
                        android.net.wifi.WifiManager.WIFI_MODE_FULL, 
                    "MusicApp::WifiLock"
                )
                wifiLock?.setReferenceCounted(false)
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } else {
            // Release CPU Lock
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            // Release Wifi Lock
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
    }

    private fun toggleFavorite() {
        val mediaItem = player.currentMediaItem ?: return
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        
        // Move to IO thread to prevent ANR
        CoroutineScope(Dispatchers.IO).launch {
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
                    put("path", "") 
                    put("albumArtUri", metadata.artworkUri?.toString() ?: "")
                    put("dateAdded", System.currentTimeMillis())
                    put("albumId", 0L)
                }
                favoritesList.add(0, songJson)
            }

            // Save back
            val newJsonArray = JSONArray()
            favoritesList.take(100).forEach { newJsonArray.put(it) }
            prefs.edit().putString("favorites", newJsonArray.toString()).apply()
            
            // Update widget UI
            updateWidget()
        }
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
        if (songId == -1L) {
             android.util.Log.d("WidgetUpdate", "Skipping update: Invalid songId (-1)")
             // Quick exit if no valid song
             return 
        }
        android.util.Log.d("WidgetUpdate", "Updating widget for song: $title ($songId) | Playing: $isPlaying")

        CoroutineScope(Dispatchers.IO).launch {
            // Move Heavy Calculation (JSON Parsing) to IO Thread
            val isFavorite = isSongFavorite(songId)
            
            // Handle online artwork
            var finalArtworkUri = artworkUri
            if (artworkUri != null && (artworkUri.startsWith("http") || artworkUri.startsWith("https"))) {
                try {
                    val bitmap = downloadBitmap(artworkUri)
                    if (bitmap != null) {
                        // 🗑️ Widget Cache Busting Cleanup
                        cacheDir.listFiles { _, name -> name.startsWith("widget_cover_") }?.forEach { it.delete() }
                        
                        val fileName = "widget_cover_${System.currentTimeMillis()}.png"
                        val file = java.io.File(cacheDir, fileName)
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        finalArtworkUri = android.net.Uri.fromFile(file).toString()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val manager = GlanceAppWidgetManager(applicationContext)
            val widget = MusicWidget()
            val glanceIds = manager.getGlanceIds(MusicWidget::class.java)
            
            android.util.Log.d("WidgetUpdate", "Found ${glanceIds.size} widgets to update")
            
            glanceIds.forEach { glanceId ->
                try {
                    updateAppWidgetState(applicationContext, glanceId) { prefs ->
                        prefs[MusicWidgetState.titleKey] = title
                        prefs[MusicWidgetState.artistKey] = artist
                        prefs[MusicWidgetState.isPlayingKey] = isPlaying
                        if (finalArtworkUri != null) {
                            prefs[MusicWidgetState.coverUriKey] = finalArtworkUri
                        } else {
                            prefs.remove(MusicWidgetState.coverUriKey)
                        }
                        prefs[MusicWidgetState.isFavoriteKey] = isFavorite
                    }
                    widget.update(applicationContext, glanceId)
                    android.util.Log.d("WidgetUpdate", "Widget updated successfully: $glanceId")
                } catch (e: Exception) {
                    android.util.Log.e("WidgetUpdate", "Failed to update widget $glanceId", e)
                }
            }
        }
    }

    private fun downloadBitmap(url: String): android.graphics.Bitmap? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            android.graphics.BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun requestAudioFocus() {
        // Now handled automatically by ExoPlayer (setAudioAttributes(..., true))
        // We keep this as a no-op or removed if no other dependencies exist.
    }

    private fun abandonAudioFocus() {
        // Now handled automatically by ExoPlayer
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
        
        // Only abandon on FULL DESTROY
        abandonAudioFocus()
        
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        headphoneMonitor?.release()
        com.amurayada.music.utils.AudioEffectsManager.release()
        super.onDestroy()
    }
    private fun buildNewPlayer(): ExoPlayer {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, 
                50000, 
                1500, 
                3000   
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
            
        val cache = com.amurayada.music.data.cache.AudioCache.getInstance(this)
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val cacheHttpDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Create Resolving DataSource Factory
        val streamRepository = com.amurayada.music.data.repository.StreamRepositoryImpl()
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, cacheHttpDataSourceFactory)
        val resolvingDataSourceFactory = com.amurayada.music.data.source.ResolvingDataSource.Factory(defaultDataSourceFactory, streamRepository)
        
        // Use the RESOLVING factory for ExoPlayer
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(resolvingDataSourceFactory)

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // TRUE enables automatic focus handling
            .setLoadControl(loadControl)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private var lastRecreationTime = 0L
    private val RECREATION_THROTTLE_MS = 10_000L // 10 segundos
    
    /**
     * PROTOCOLO ZOMBIE: Reconstruye un reproductor "muerto" preservando la sesión.
     * Crítico para recuperarse de errores fatales de decodificación o crashes nativos de ExoPlayer.
     */
    private fun recreatePlayerPreservingSession() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRecreationTime < RECREATION_THROTTLE_MS) {
            android.util.Log.e("MusicPlaybackService", "ZOMBIE THROTTLE: Recreación demasiado rápida. Abortando para prevenir bucles.")
            return
        }
        lastRecreationTime = currentTime
        
        android.util.Log.e("MusicPlaybackService", "PROTOCOLO ZOMBIE: Recreando Reproductor Muerto...")
        
        // UX: Mostrar estado de "Cargando"
        isLoading = true
        updateWidget()
        
        // 1. Capturar Estado Actual
        val currentItem = player.currentMediaItem
        val currentPos = player.currentPosition
        val oldPlayer = player

        // 2. Ejecutar al Zombi
        oldPlayer.release()
        
        // 3. Engendrar Nuevo Reproductor
        player = buildNewPlayer()
        
        // 4. Re-aplicar Listeners & Wrapper
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                com.amurayada.music.utils.AudioEffectsManager.audioSessionId = audioSessionId
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val restartIntent = Intent(applicationContext, MusicPlaybackService::class.java)
                startService(restartIntent)
                updateWidget()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) isLoading = false 
                headphoneMonitor?.onIsPlayingChanged(isPlaying)
                val isBuffering = player.playbackState == Player.STATE_BUFFERING
                manageWakeLock(isPlaying || isBuffering)
                updateWidget()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MusicPlaybackService", "❌ ERROR FATAL ZOMBIE: ${error.message}")
                android.util.Log.e("MusicPlaybackService", "   - Nombre Error: ${error.errorCodeName}")
                android.util.Log.e("MusicPlaybackService", "   - URI: ${player.currentMediaItem?.localConfiguration?.uri}")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                     recreatePlayerPreservingSession()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isPlaying = player.isPlaying
                val isBuffering = playbackState == Player.STATE_BUFFERING
                manageWakeLock(isPlaying || isBuffering || playbackState == Player.STATE_IDLE)
                updateWidget()
                if (isPlaying || isBuffering) requestAudioFocus()
                
                // Misma lógica de guardia de timeout que el reproductor principal
                when (playbackState) {
                    Player.STATE_READY -> {
                        cancelPlaybackTimeout()
                        resetIdleRetryCounter()
                    }
                    Player.STATE_BUFFERING -> {
                        if (player.playWhenReady) startPlaybackTimeout()
                    }
                    Player.STATE_IDLE -> {
                        if (player.playWhenReady && player.currentMediaItem != null) {
                            val currentMediaId = player.currentMediaItem?.mediaId
                            if (currentMediaId != currentFailedMediaId) {
                                idleRetryCount = 0
                                currentFailedMediaId = currentMediaId
                            }
                            idleRetryCount++
                            if (idleRetryCount <= MAX_IDLE_RETRIES) {
                                android.util.Log.w("MusicPlaybackService", "🔄 WATCHDOG (Resurrección): IDLE retry $idleRetryCount/$MAX_IDLE_RETRIES")
                                startPlaybackTimeout()
                                player.prepare()
                            } else {
                                forceSkipToNext("Máximos reintentos excedidos (Resurrección)")
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        cancelPlaybackTimeout()
                        if (player.hasNextMediaItem()) {
                            resetIdleRetryCounter()
                            player.seekToNextMediaItem()
                            player.play()
                            startPlaybackTimeout()
                        }
                    }
                }
            }
        })

        // 5. Re-envolver con "Truco SimpMusic" (Versión Base)
        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            private val notificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            private var lastArtworkUri: String? = null
            private var localArtworkFile: java.io.File? = null

            override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
                val metadata = super.getMediaMetadata()
                val artworkUri = metadata.artworkUri?.toString()
                
                if (artworkUri != null && artworkUri.startsWith("http")) {
                    if (artworkUri != lastArtworkUri) {
                        lastArtworkUri = artworkUri
                        notificationScope.launch {
                            try {
                                val bitmap = downloadBitmap(artworkUri)
                                if (bitmap != null) {
                                    // 🗑️ Limpieza Segura (Resurrección): Solo borra archivos mayores a 600s
                                    val currentTime = System.currentTimeMillis()
                                    cacheDir.listFiles { _, name -> name.startsWith("notif_cover_") }?.forEach { file ->
                                        try {
                                            val timestamp = file.name.substringAfter("notif_cover_").substringBefore(".").toLongOrNull()
                                            if (timestamp != null && (currentTime - timestamp) > 600_000) {
                                                file.delete()
                                                android.util.Log.d("MusicPlaybackService", "Borrada portada vieja (Ventana Segura): ${file.name}")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.w("MusicPlaybackService", "Fallo al parsear/borrar ${file.name} (Resurrección)", e)
                                        }
                                    }
                                    
                                    val fileName = "notif_cover_${System.currentTimeMillis()}.png"
                                    val file = java.io.File(cacheDir, fileName)
                                    java.io.FileOutputStream(file).use { out ->
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    localArtworkFile = file
                                    android.util.Log.d("MusicPlaybackService", "Artwork cacheado (Bust/Resurrección): $fileName")
                                    
                                    mediaSession?.setCustomLayout(emptyList()) 
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MusicPlaybackService", "Fallo al cachear artwork", e)
                            }
                        }
                    }
                    
                    if (localArtworkFile?.exists() == true) {
                        return metadata.buildUpon()
                            .setArtworkUri(android.net.Uri.fromFile(localArtworkFile))
                            .build()
                    }
                }
                return metadata
            }

            override fun getPlaybackState(): Int {
                val realState = super.getPlaybackState()
                val wantsToPlay = super.getPlayWhenReady()
                if (wantsToPlay && (realState == Player.STATE_BUFFERING || realState == Player.STATE_IDLE)) {
                    return Player.STATE_READY
                }
                return realState
            }
            
            override fun isPlaying(): Boolean {
                val wantsToPlay = super.getPlayWhenReady()
                val realState = super.getPlaybackState()
                if (wantsToPlay && (realState == Player.STATE_READY || realState == Player.STATE_BUFFERING)) {
                    return true
                }
                return super.isPlaying()
            }
        }
        
        // 6. Actualizar Sesión
        mediaSession?.player = forwardingPlayer
        
        // 7. Restaurar Contenido
        serviceScope.launch {
            val repairedItem = repairMediaItemIfBroken(currentItem)
            if (repairedItem != null) {
                player.setMediaItem(repairedItem)
                player.seekTo(currentPos)
                player.prepare()
                player.play()
            } else {
                // Fallback a silencio infinito si no sonaba nada
                val silenceSource = androidx.media3.exoplayer.source.SilenceMediaSource(3600_000_000L)
                player.setMediaSource(silenceSource)
                player.prepare()
                player.play()
            }
        }
    }

    private suspend fun repairMediaItemIfBroken(mediaItem: MediaItem?): MediaItem? {
        if (mediaItem == null) return null
        val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val path = uri.toString()
        
        // Si es una URI SAF o path absoluto que podría estar roto (común para descargas personalizadas tras reiniciar)
        val isSaf = path.startsWith("content://com.android.externalstorage.documents")
        val isAbsolute = !path.startsWith("content://")
        
        if (isSaf || isAbsolute) {
            val title = mediaItem.mediaMetadata.title?.toString() ?: ""
            val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
            
            // Para URIs SAF, a menudo podemos extraer el nombre de archivo del URI del documento
            var filename: String? = null
            if (isSaf) {
                try {
                    val decodedPath = Uri.decode(path)
                    filename = decodedPath.substringAfterLast("/")
                    if (filename.isEmpty()) filename = null
                } catch (e: Exception) {
                    android.util.Log.w("MusicPlaybackService", "Fallo al extraer nombre de archivo de URI SAF: $path")
                }
            }
            
            if (title.isNotEmpty() || filename != null) {
                try {
                    android.util.Log.d("MusicPlaybackService", "🛠️ Intentando reparar URI rota (Título: $title, Archivo: $filename)")
                    val mediaRepository = com.amurayada.music.data.repository.MediaRepository(this@MusicPlaybackService)
                    
                    var newUri: Uri? = null
                    
                    // Estrategia A: Búsqueda por Nombre de Archivo (Alta prioridad para descargas)
                    if (filename != null) {
                        newUri = mediaRepository.findSongUriInMediaStoreByFilename(filename)
                        if (newUri != null) android.util.Log.i("MusicPlaybackService", "✅ REPARADO vía Nombre Archivo: $filename")
                    }
                    
                    // Estrategia B: Búsqueda por Título + Artista / Solo Título
                    if (newUri == null && title.isNotEmpty()) {
                        newUri = mediaRepository.findSongUriInMediaStore(title, artist)
                        if (newUri != null) android.util.Log.i("MusicPlaybackService", "✅ REPARADO vía metadatos: $title")
                    }
                    
                    if (newUri != null && newUri != uri) {
                        return mediaItem.buildUpon()
                            .setUri(newUri)
                            .build()
                    } else if (newUri == null) {
                        android.util.Log.w("MusicPlaybackService", "⚠️ Reparación fallida para '$title' ($filename)")
                        
                        // Si es una descarga y falta permanentemente, borrar de DB para evitar bucles de salto
                        if (isSaf || isAbsolute) {
                            try {
                                val songIdString = mediaItem.mediaId
                                if (songIdString.isNotEmpty() && songIdString.all { it.isDigit() }) {
                                    val songId = songIdString.toLong()
                                    android.util.Log.i("MusicPlaybackService", "🗑️ Auto-limpiando descarga fantasma: $title (ID: $songId)")
                                    val db = com.amurayada.music.data.database.DownloadDatabase.getDatabase(this@MusicPlaybackService)
                                    db.downloadDao().deleteDownloadById(songId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MusicPlaybackService", "Fallo al auto-limpiar descarga fantasma", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicPlaybackService", "❌ Error durante reparación de URI", e)
                }
            }
        }
        return mediaItem
    }

    private fun createMediaItemFromSong(song: com.amurayada.music.data.model.Song): androidx.media3.common.MediaItem {
        android.util.Log.d("MusicPlaybackService", "Creating MediaItem for: ${song.title} (ID: ${song.id})")
        android.util.Log.d("MusicPlaybackService", "   - Path: ${song.path}")
        
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()
        
        val requestMetadata = androidx.media3.common.MediaItem.RequestMetadata.Builder()
            .setMediaUri(android.net.Uri.parse(song.path))
            .build()
        
        return androidx.media3.common.MediaItem.Builder()
            .setUri(android.net.Uri.parse(song.path))
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)
            .setMediaId(song.id.toString())
            .build()
    }
}
