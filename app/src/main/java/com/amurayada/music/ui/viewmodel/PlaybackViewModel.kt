package com.amurayada.music.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amurayada.music.data.model.PlaybackMode
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.RepeatMode
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.LyricsRepository
import com.amurayada.music.data.repository.LyricsResult

import com.amurayada.music.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel principal encargado de orquestar la lógica de reproducción multimedia.
 * Se comunica con [MusicPlaybackService] mediante un [MediaController] de Media3 para 
 * desacoplar la interfaz de usuario del ciclo de vida del servicio.
 * 
 * Responsabilidades:
 * - Sincronizar el estado del reproductor (pausa, play, posición).
 * - Gestionar la búsqueda y verificación de videos (Canva) para canciones online.
 * - Administrar la cola de reproducción y la persistencia del estado.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    
    /**
     * Estados de la capa de video. Controla la visibilidad del componente PlayerView en la UI.
     */
    enum class VideoState {
        AUDIO_ONLY,  // Solo flujo de audio activo
        CHECKING,    // Validando disponibilidad de video/Canva
        VIDEO_READY, // Stream de video verificado y cargado en el buffer
        DISABLED     // Video no disponible para la pista actual o error de carga
    }
    
    // Implementación del repositorio de streams para resolución de URLs de YouTube/NewPipe
    private val streamRepository = com.amurayada.music.data.repository.StreamRepositoryImpl()
    private val context: Context get() = getApplication<Application>().applicationContext
    
    // Interfaz de control para la sesión de Media3 (MediaSession)
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set
    
    // Persistencia de preferencias de reproducción (modo repetitivo, aleatorio, etc.)
    private val prefs = context.getSharedPreferences("playback_data", Context.MODE_PRIVATE)
    
    // Repositorio encargado de la obtención y parseo de letras LRC/estáticas
    private val lyricsRepository = LyricsRepository(context)

    // Gestor de caché para URLs de video para reducir peticiones a la API de YouTube
    private val cacheManager = com.amurayada.music.data.cache.CacheManager.getInstance()
    
    // Infraestructura de persistencia y lógica para canciones descargadas localmente
    private val downloadDatabase = com.amurayada.music.data.database.DownloadDatabase.getDatabase(application)
    private val downloadRepository = com.amurayada.music.data.repository.DownloadRepositoryImpl(
        application,
        downloadDatabase.downloadDao(),
        streamRepository
    )
    
    // Mapeos temporales para evitar resoluciones repetitivas durante la sesión activa
    private val resolvedUrlCache = mutableMapOf<Long, String>()
    private val originalPathCache = mutableMapOf<Long, String>()
    
    var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Idle)
        private set
    
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
    }

    var currentSong by mutableStateOf<Song?>(null)
        private set



    var youtubeVideoUrl by mutableStateOf<String?>(null)
        private set


    
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    
    var currentPosition by mutableStateOf(0L)
        private set
    
    var playbackMode by mutableStateOf(PlaybackMode())
        private set
    
    var videoState by mutableStateOf(VideoState.AUDIO_ONLY)
        private set

    var canShowVideo by mutableStateOf(false)
        private set
        
    var canManualToggleVideo by mutableStateOf(false)
        private set
        
    var userWantsVideo by mutableStateOf(false)
        private set

    var isVideoSizeValid by mutableStateOf(false)
        private set
        
    private val unsupportedVideoIds = mutableSetOf<String>()
    
    private fun isVideoSupported(song: Song?): Boolean {
        if (song == null) return false
        val isOnline = song.path.startsWith("http")
        if (!isOnline) return false
        
        val isUnsupported = unsupportedVideoIds.contains(song.path)
        return !isUnsupported
    }

    /**
     * Procesa el resultado de la búsqueda de video.
     * Si se encuentra un video válido, se habilitan los controles de conmutación manual.
     */
    private fun handleVideoResult(result: Pair<String, Long>?, song: Song?) {
        if (result != null) {
            val (url, _) = result
            youtubeVideoUrl = url
            canShowVideo = true
            canManualToggleVideo = true 
            Log.d("PlaybackViewModel", "Video localizado y verificado para control manual.")
        } else {
            youtubeVideoUrl = null
            canShowVideo = false
            canManualToggleVideo = false
        }
    }
        
    /**
     * Valida físicamente si ExoPlayer ha seleccionado una pista de video.
     * Previene estados "fantasmas" donde la lógica interna cree que hay video pero el reproductor no lo muestra.
     */
    private fun verifyVideoAvailability(tracks: androidx.media3.common.Tracks) {
        val hasSelectedVideo = tracks.groups.any { group ->
            group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected
        }
        
        if (hasSelectedVideo) {
            if (videoState != VideoState.VIDEO_READY) {
                Log.d("PlaybackViewModel", "Pista de video detectada físicamente. Cambiando UI a modo Video.")
                videoState = VideoState.VIDEO_READY
            }
            return 
        }

        // Si esperábamos video pero el reproductor solo cargó audio, forzamos reversión limpia
        if (videoState == VideoState.CHECKING) {
            val isPlayerReady = mediaController?.playbackState == Player.STATE_READY
            if (isPlayerReady) {
                Log.w("PlaybackViewModel", "Reversión: Se esperaba video pero no hay pistas disponibles. Forzando Audio.")
                forceAudioOnly(revertToAudioStream = false) 
            }
        }
    }
    
    private var isTogglingVideo = false
        
    private var isTransitioningToVideo = false
    
    // Favorites list - persisted
    private val _favorites = mutableStateListOf<Song>()
    val favorites: List<Song> get() = _favorites.toList()
    
    // Recently played (history) - persisted
    private val _recentlyPlayed = mutableStateListOf<Song>()
    val recentlyPlayed: List<Song> get() = _recentlyPlayed.toList()
    
    // Most played tracking - persisted
    private val playCountMap = mutableMapOf<Long, Int>()
    val playCounts: Map<Long, Int> get() = playCountMap.toMap()
    
    val mostPlayed: List<Song>
        get() = _recentlyPlayed
            .distinctBy { it.id }
            .sortedByDescending { playCountMap[it.id] ?: 0 }
            .take(20)
            
    // Event to expand player from other screens (e.g. widget)
    private val _expandPlayerEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val expandPlayerEvent = _expandPlayerEvent.asSharedFlow()
    
    fun expandPlayer() {
        viewModelScope.launch {
            _expandPlayerEvent.emit(Unit)
        }
    }
    
    init {
        loadPersistedData()
        initializeMediaController()
        startPositionUpdater()
    }
    
    private fun loadPersistedData() {
        // Load favorites
        val favoritesJson = prefs.getString("favorites", null)
        if (favoritesJson != null) {
            try {
                val jsonArray = JSONArray(favoritesJson)
                for (i in 0 until jsonArray.length()) {
                    val songJson = jsonArray.getJSONObject(i)
                    _favorites.add(songFromJson(songJson))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        // Load history
        val historyJson = prefs.getString("history", null)
        if (historyJson != null) {
            try {
                val jsonArray = JSONArray(historyJson)
                for (i in 0 until jsonArray.length()) {
                    val songJson = jsonArray.getJSONObject(i)
                    _recentlyPlayed.add(songFromJson(songJson))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        // Load play counts
        val playCountsJson = prefs.getString("play_counts", null)
        if (playCountsJson != null) {
            try {
                val jsonObject = JSONObject(playCountsJson)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    playCountMap[key.toLong()] = jsonObject.getInt(key)
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        // Load video preference
        userWantsVideo = false // Mode is strictly in-memory and volatile
        Log.d("PlaybackViewModel", "Loaded video preference: $userWantsVideo")
    }
    
    private fun saveFavorites() {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                _favorites.take(100).forEach { song -> // Limit to 100
                    jsonArray.put(songToJson(song))
                }
                prefs.edit().putString("favorites", jsonArray.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }
    
    private fun saveHistory() {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                _recentlyPlayed.take(50).forEach { song -> // Limit to 50
                    jsonArray.put(songToJson(song))
                }
                prefs.edit().putString("history", jsonArray.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }
    
    private fun savePlayCounts() {
        viewModelScope.launch {
            try {
                val jsonObject = JSONObject()
                playCountMap.entries.take(100).forEach { (id, count) ->
                    jsonObject.put(id.toString(), count)
                }
                prefs.edit().putString("play_counts", jsonObject.toString()).apply()
            } catch (e: Exception) {
                // Ignore save errors
            }
        }
    }

    private fun saveQueue() {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray()
                queue.forEach { song ->
                    jsonArray.put(songToJson(song))
                }
                prefs.edit().apply {
                    putString("current_queue", jsonArray.toString())
                    putInt("current_index", mediaController?.currentMediaItemIndex ?: -1)
                    putLong("current_position", mediaController?.currentPosition ?: 0L)
                    apply()
                }
                Log.d("PlaybackViewModel", "Queue saved: ${queue.size} items")
            } catch (e: Exception) {
                Log.e("PlaybackViewModel", "Error saving queue", e)
            }
        }
    }

    private fun savePlaybackState() {
        mediaController?.let { controller ->
            prefs.edit().apply {
                putInt("current_index", controller.currentMediaItemIndex)
                putLong("current_position", controller.currentPosition)
                apply()
            }
        }
    }
    
    private fun songToJson(song: Song): JSONObject {
        return JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("album", song.album)
            put("duration", song.duration)
            put("path", song.path)
            put("albumArtUri", song.albumArtUri?.toString() ?: "")
            put("dateAdded", song.dateAdded)
            put("albumId", song.albumId)
        }
    }
    
    private fun songFromJson(json: JSONObject): Song {
        val albumArtUriString = json.optString("albumArtUri", "")
        return Song(
            id = json.getLong("id"),
            title = json.getString("title"),
            artist = json.getString("artist"),
            album = json.getString("album"),
            duration = json.getLong("duration"),
            albumArtUri = if (albumArtUriString.isNotEmpty()) Uri.parse(albumArtUriString) else null,
            path = json.getString("path"),
            dateAdded = json.optLong("dateAdded", 0L),
            albumId = json.optLong("albumId", 0L)
        )
    }
    
    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicPlaybackService::class.java)
        )
        
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            setupPlayerListener()
            syncWithController()

        }, MoreExecutors.directExecutor())
    }


    
    private fun syncWithController() {
        mediaController?.let { controller ->
            val item = controller.currentMediaItem
            if (item != null) {
                val metadata = item.mediaMetadata
                val song = Song(
                    id = item.mediaId.toLongOrNull() ?: 0L,
                    title = metadata.title?.toString() ?: "Unknown",
                    artist = metadata.artist?.toString() ?: "Unknown",
                    album = metadata.albumTitle?.toString() ?: "Unknown",
                    duration = controller.duration.takeIf { it > 0 } ?: 0L,
                    albumArtUri = metadata.artworkUri,
                    path = item.localConfiguration?.uri?.toString() ?: item.requestMetadata.mediaUri?.toString() ?: "",
                    dateAdded = 0L,
                    albumId = 0L
                )
                
                // CRITICAL FIX: Prioritize RequestMetadata URI if available (contains original path)
                // ExoPlayer uses localConfiguration for the ACTUAL playing stream (e.g. googlevideo.com),
                // but we stored the ORIGINAL Song path (YouTube ID/URL) in RequestMetadata.
                val originalPath = item.requestMetadata.mediaUri?.toString()
                val finalSongPath = if (!originalPath.isNullOrEmpty()) originalPath else song.path
                
                val songWithCorrectPath = song.copy(path = finalSongPath)

                // If path is missing, try to find it in history
                val finalizedSong = if (songWithCorrectPath.path.isEmpty()) {
                    _recentlyPlayed.find { it.id == songWithCorrectPath.id } ?: songWithCorrectPath
                } else {
                    songWithCorrectPath
                }
                
                currentSong = finalizedSong
                canShowVideo = isVideoSupported(finalizedSong)
                
                // Track-aware sync: Only reset videoState if video is NOT playing and we are NOT loading
                val tracks = controller.currentTracks
                val isVideoActuallySelected = tracks.groups.any { group ->
                    group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected
                }
                
                if (isVideoActuallySelected) {
                    Log.d("PlaybackViewModel", "Sync: Video track physically SELECTED. Restoring VIDEO_READY state.")
                    videoState = VideoState.VIDEO_READY
                } else {
                    // Only reset if we are NOT in the middle of a transition (CHECKING)
                    if (videoState != VideoState.CHECKING) {
                        videoState = if (finalizedSong.path.startsWith("http")) VideoState.AUDIO_ONLY else VideoState.DISABLED
                    }
                }
                
                // BACKGROUND VERIFICATION: Even on sync, we need to populate youtubeVideoUrl for Canva/Video toggle
                if (finalizedSong.path.startsWith("http") && !unsupportedVideoIds.contains(finalizedSong.path)) {
                    viewModelScope.launch {
                        val result = streamRepository.getVideoStreamUrl(finalizedSong)
                        handleVideoResult(result, finalizedSong)
                        
                        if (canShowVideo) {
                            Log.d("PlaybackViewModel", "Sync verification: Video URL found for ${finalizedSong.title}")
                            
                                // Sticky mode on resume REMOVED: Always start in Audio mode
                                // unless system already has video (handled above)
                        }
                    }
                }
                
                loadLyrics(finalizedSong)

                updatePlaybackState()
            }
        }
    }
    
    /**
     * Configura los listeners de Media3 para reaccionar a cambios en el reproductor.
     * Es crucial para mantener la sincronización entre el servicio de fondo y la UI.
     */
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Al pausar, persistimos el estado local para permitir la reanudación tras el cierre total de la app
                if (!isPlaying) {
                    savePlaybackState()
                }
                updatePlaybackState()
            }
            
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // Se dispara cuando cambian las pistas disponibles (audio, video, subtítulos)
                verifyVideoAvailability(tracks)
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Reacciona a los cambios de estado interno del reproductor (IDLE, BUFFERING, READY, ENDED)
                if (state == Player.STATE_READY) {
                    mediaController?.let { verifyVideoAvailability(it.currentTracks) }
                }
                updatePlaybackState()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // GESTIÓN DE TRANSICIÓN: Detecta cambios de canción, ya sea manual o automático al terminar la pista.
                
                // ESCUDO DE ESTABILIDAD: Ignoramos transiciones gatilladas por nuestro propio cambio manual entre audio/video
                if (isTogglingVideo) {
                    Log.d("PlaybackViewModel", "onMediaItemTransition: Cambio de flujo en curso. Ignorando reset.")
                    return
                }
                
                // Evitamos procesar reemplazos internos de metadatos (evita bucles infinitos)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
                
                mediaController?.let { controller ->
                    // Reiniciamos la posición visual para la nueva pista
                    currentPosition = 0L
                    
                    val currentIndex = controller.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < queue.size) {
                        val newSong = queue[currentIndex]
                        
                        // Solo aplicamos lógica de reset si la IDENTIDAD de la canción es distinta
                        if (currentSong?.id != newSong.id) {
                            currentSong = newSong
                            
                            // POLÍTICA DE AMNESIA: Cada nueva pista comienza forzosamente en modo Audio Only
                            userWantsVideo = false 
                            
                            // LIMPIEZA INMEDIATA: Evitamos que la UI muestre contenido de la canción anterior (efecto ghosting)
                            youtubeVideoUrl = null
                            canShowVideo = false
                            canManualToggleVideo = false
                            isVideoSizeValid = false
                            
                            // Determinamos el estado inicial según el origen de la pista
                            val isOnline = newSong.path.startsWith("http")
                            videoState = if (isOnline) VideoState.AUDIO_ONLY else VideoState.DISABLED
                            
                            // VERIFICACIÓN PROACTIVA: Buscamos streams de video compatibles para canciones online
                            if (isOnline && !unsupportedVideoIds.contains(newSong.path)) {
                                Log.d("PlaybackViewModel", "Canción online detectada. Lanzando verificador de video...")
                                viewModelScope.launch {
                                    // 1. Consulta rápida a la caché de Canva
                                    val cachedResult = cacheManager.getCachedCanvaUrl(newSong.path)
                                    if (cachedResult != null) {
                                        Log.d("PlaybackViewModel", "Canva CACHED para: ${newSong.title}")
                                        handleVideoResult(cachedResult, newSong)
                                        return@launch
                                    }
    
                                    // 2. Si no hay caché, realizamos petición de red para localizar el video
                                    val result = streamRepository.getVideoStreamUrl(newSong)
                                    handleVideoResult(result, newSong)
                                    
                                    if (canShowVideo && youtubeVideoUrl != null) {
                                        Log.d("PlaybackViewModel", "Stream de video VERIFICADO para: ${newSong.title}. Habilitando toggle.")
                                        // 3. Persistimos el resultado (con duración) para optimizar futuros accesos
                                        val duration = result?.second ?: 0L
                                        cacheManager.cacheCanvaUrl(newSong.path, youtubeVideoUrl!!, duration)
                                    } else {
                                        Log.d("PlaybackViewModel", "Video NO encontrado o FILTRADO para: ${newSong.title}.")
                                        unsupportedVideoIds.add(newSong.path)
                                    }
                                }
                            }
                        }
                        
                        updatePlaybackState()
                    } else {
                        // Sincronización de seguridad si el índice se sale del rango de la cola local
                        syncWithController()
                    }
                }
            }
            
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                isVideoSizeValid = videoSize.width > 0 && videoSize.height > 0
                if (isVideoSizeValid && videoState == VideoState.CHECKING) {
                    Log.d("PlaybackViewModel", "Video size valid while CHECKING. Promoting to VIDEO_READY.")
                    videoState = VideoState.VIDEO_READY
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // STABILITY GUARD: Ignore discontinuities triggered by our own manual video switch
                if (isTogglingVideo) {
                    Log.d("PlaybackViewModel", "onPositionDiscontinuity: Switch in progress. Skipping reset.")
                    return
                }
                
                savePlaybackState()
                // Also update on position discontinuity (skip next/prev)
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || 
                    reason == Player.DISCONTINUITY_REASON_SEEK) {
                    mediaController?.let { controller ->
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < queue.size) {
                            val newSong = queue[currentIndex]
                            if (currentSong?.id != newSong.id) {
                                currentSong = newSong
                                
                                // RESET VIDEO PREFERENCE: Every new track starts in Audio Only mode
                                userWantsVideo = false
                                
                                // IMMEDIATE STATE RESET: Clear stale data to prevent sticky UI
                                youtubeVideoUrl = null
                                canShowVideo = false 
                                canManualToggleVideo = false
                                isVideoSizeValid = false
                                
                                // Aggressive visual reset - WITHOUT interrupting playback
                                forceAudioOnly(revertToAudioStream = false)
                                
                                val isOnline = newSong.path.startsWith("http")
                                videoState = if (isOnline) VideoState.AUDIO_ONLY else VideoState.DISABLED
                                
                                if (isOnline && !unsupportedVideoIds.contains(newSong.path)) {
                                    viewModelScope.launch {
                                        val result = streamRepository.getVideoStreamUrl(newSong)
                                        handleVideoResult(result, newSong)
                                        if (!canShowVideo) {
                                            unsupportedVideoIds.add(newSong.path)
                                        }
                                    }
                                }
                                addToHistory(newSong)
                                playCountMap[newSong.id] = (playCountMap[newSong.id] ?: 0) + 1
                                savePlayCounts()
                                loadLyrics(newSong)
                            }
                        } else {
                             syncWithController()
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                android.util.Log.e("PlaybackViewModel", "Player Error: ${error.message}")
                
                // CRITICAL FIX: If error occurs during video playback/loading, revert to "Base State" (Audio)
                if (videoState == VideoState.VIDEO_READY || videoState == VideoState.CHECKING) {
                    android.util.Log.d("PlaybackViewModel", "Error during video playback. Reverting to Audio Base State.")
                    userWantsVideo = false // Reset preference
                    canShowVideo = false // Disable toggle for this song
                    currentSong?.let { unsupportedVideoIds.add(it.path) }
                    
                    forceAudioOnly(revertToAudioStream = true) // Switch back to audio stream
                    
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Video falló. Volviendo a solo audio."))
                    }
                } else {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Error de reproducción: ${error.message}"))
                    }
                }
                updatePlaybackState()
            }
        })
    }
    
    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        var song = currentSong ?: return
        
        // Check if this is truly an online song (not a downloaded file)
        val isOnlineSong = song.path.startsWith("http") && !java.io.File(song.path).exists()
        
        // For online songs with no duration, try to get it from MediaController
        if (isOnlineSong && song.duration == 0L && controller.duration > 0 && controller.duration != Long.MIN_VALUE + 1) {
            song = song.copy(duration = controller.duration)
            currentSong = song
        }
        
        playbackState = when {
            controller.isPlaying -> PlaybackState.Playing(song, controller.currentPosition)
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackState.Loading
            controller.playbackState == Player.STATE_READY -> PlaybackState.Paused(song, controller.currentPosition)
            else -> PlaybackState.Idle
        }
    }
    
    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    mediaController?.let { controller ->
                        if (controller.isPlaying) {
                            val currentMediaId = controller.currentMediaItem?.mediaId
                            if (currentMediaId == currentSong?.id?.toString() && !isTransitioningToVideo) {
                                currentPosition = controller.currentPosition
                                
                                // Update duration for online songs if not set
                                currentSong?.let { song ->
                                    val isOnlineSong = song.path.startsWith("http") && !java.io.File(song.path).exists()
                                    if (isOnlineSong && song.duration == 0L && controller.duration > 0 && controller.duration != Long.MIN_VALUE + 1) {
                                        currentSong = song.copy(duration = controller.duration)
                                    }
                                }
                                
                                // REFRESH STATE: Ensure PlaybackState is always fresh (fix stuck slider/UI)
                                updatePlaybackState()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore exceptions during position update
                }
                delay(500L)
            }
        }
    }
    
    // UI Events
    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    /**
     * Inicia la reproducción de una canción específica y establece la nueva cola.
     * @param song La pista que se reproducirá de inmediato.
     * @param songList La lista completa que conformará la cola de reproducción actual.
     */
    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        viewModelScope.launch {
            currentSong = song
            queue = songList
            
            // RESET DE ESTADO INMEDIATO: Limpiamos metadatos residuales para evitar inconsistencias en la UI
            youtubeVideoUrl = null
            canShowVideo = false
            canManualToggleVideo = false
            isVideoSizeValid = false
            userWantsVideo = false 
            
            // Forzamos modo audio al iniciar; el video se buscará de forma asíncrona si la pista es online
            forceAudioOnly(revertToAudioStream = false)
            
            val isOnline = song.path.startsWith("http")
            videoState = if (isOnline) VideoState.AUDIO_ONLY else VideoState.DISABLED
            
            // VERIFICACIÓN PROACTIVA: Busca disponibilidad de video en segundo plano sin interrumpir la carga del audio
            if (isOnline && !unsupportedVideoIds.contains(song.path)) {
                viewModelScope.launch {
                    val result = streamRepository.getVideoStreamUrl(song)
                    handleVideoResult(result, song)
                    if (!canShowVideo) {
                        unsupportedVideoIds.add(song.path)
                    }
                }
            }
            
            loadLyrics(song)
            playbackState = PlaybackState.Loading
            
            // Cache original YouTube paths for downloads (optional, as paths are now persistent)
            songList.forEach { s ->
                if (s.path.contains("youtube.com") || s.path.contains("youtu.be")) {
                    originalPathCache[s.id] = s.path
                }
            }
            
            // Stop previous playback immediately
            mediaController?.pause()
            
            // Add to history and save (optimistic)
            addToHistory(song)
            
            // Just pass the RAW list to the controller.
            // The ResolvingDataSource in the Service will handle URL resolution on-demand.
            val startIndex = songList.indexOf(song).coerceAtLeast(0)
            val mediaItems = songList.map { createMediaItem(it) }
            
            mediaController?.apply {
                setMediaItems(mediaItems, startIndex, 0)
                prepare()
                play()
            }
            
            saveQueue()
            loadLyrics(song)

            
            // Increment play count
            playCountMap[song.id] = (playCountMap[song.id] ?: 0) + 1
            savePlayCounts()
        }
    }
    
    fun removeFromQueue(song: Song) {
        val index = queue.indexOfFirst { it.id == song.id }
        if (index != -1) {
            val newQueue = queue.toMutableList().apply { removeAt(index) }
            queue = newQueue
            
            // If removed current song, skip to next
            if (currentSong?.id == song.id) {
                if (newQueue.isNotEmpty()) {
                    val nextIndex = index.coerceAtMost(newQueue.lastIndex)
                    playSong(newQueue[nextIndex], newQueue)
                } else {
                    mediaController?.stop()
                }
            } else {
                // Update controller queue without interrupting playback
                // Note: This is a simplified approach. Ideally we'd use MediaController.removeMediaItem
                // But for now we just update the local list and let the user re-select if they want to play from the new queue
                // Or we can try to sync with controller if possible
                mediaController?.removeMediaItem(index)
            }
        }
    }
    
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= queue.size || toIndex >= queue.size) return
        
        val newQueue = queue.toMutableList()
        val item = newQueue.removeAt(fromIndex)
        newQueue.add(toIndex, item)
        queue = newQueue
        
        // Update controller
        mediaController?.moveMediaItem(fromIndex, toIndex)
        saveQueue()
    }

    private fun addToHistory(song: Song) {
        // Remove if already exists (to move it to front)
        _recentlyPlayed.removeAll { it.id == song.id }
        // Add to front
        _recentlyPlayed.add(0, song)
        // Keep only last 50 items
        while (_recentlyPlayed.size > 50) {
            _recentlyPlayed.removeLast()
        }
        // Save to persistence
        saveHistory()
    }
    
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }
    
    fun skipToNext() {
        mediaController?.seekToNext()
        updateCurrentSongFromController()
    }
    
    fun skipToPrevious() {
        mediaController?.seekToPrevious()
        updateCurrentSongFromController()
    }
    
    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        currentPosition = position
    }
    
    fun toggleShuffle() {
        val newMode = playbackMode.copy(isShuffleEnabled = !playbackMode.isShuffleEnabled)
        playbackMode = newMode
        mediaController?.shuffleModeEnabled = newMode.isShuffleEnabled
    }
    
    fun toggleRepeatMode() {
        val newRepeatMode = when (playbackMode.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackMode = playbackMode.copy(repeatMode = newRepeatMode)
        
        mediaController?.repeatMode = when (newRepeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }
    
    // Favorites functions - with persistence
    fun toggleFavorite(song: Song) {
        if (_favorites.any { it.id == song.id }) {
            _favorites.removeAll { it.id == song.id }
        } else {
            _favorites.add(0, song)
        }
        saveFavorites()
    }
    
    fun isFavorite(song: Song): Boolean {
        return _favorites.any { it.id == song.id }
    }
    
    fun clearHistory() {
        _recentlyPlayed.clear()
        saveHistory()
    }
    
    private fun updateCurrentSongFromController() {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex >= 0 && currentIndex < queue.size) {
                val newSong = queue[currentIndex]
                if (currentSong?.id != newSong.id) {
                    currentSong = newSong
                    canShowVideo = isVideoSupported(newSong)
                    if (!canShowVideo) videoState = if (newSong.path.startsWith("http")) VideoState.AUDIO_ONLY else VideoState.DISABLED
                    else if (videoState == VideoState.VIDEO_READY || videoState == VideoState.CHECKING) {
                         // Reset to AUDIO_ONLY on track change to avoid "sticking"
                         videoState = VideoState.AUDIO_ONLY
                    }
                    addToHistory(newSong)
                    playCountMap[newSong.id] = (playCountMap[newSong.id] ?: 0) + 1
                    savePlayCounts()
                    savePlayCounts()
                    loadLyrics(newSong)

                }
            } else {
                 syncWithController()
            }
        }
    }
    
    private fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()
        
        // Store the ORIGINAL path in RequestMetadata.
        // This ensures that even if ExoPlayer is playing a resolved googlevideo.com URL,
        // we can always recover the original YouTube URL/ID from the MediaItem.
        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(Uri.parse(song.path))
            .build()
        
        return MediaItem.Builder()
            .setUri(Uri.parse(song.path))
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)
            .setMediaId(song.id.toString())
            .build()
    }
    
    // Sleep Timer
    var sleepTimerDuration by mutableStateOf<Long?>(null)
        private set
    var isSleepTimerRunning by mutableStateOf(false)
        private set
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var initialVolume = 1f

    // Lyrics
    var lyrics by mutableStateOf<String?>(null)
        private set
        
    var lyricsSource by mutableStateOf<String?>(null)
        private set
    
    var lyricsLoadingState by mutableStateOf<LyricsLoadingState>(LyricsLoadingState.Idle)
        private set

    /**
     * Fuerza agresivamente el reproductor al modo de solo audio.
     * Útil para reversiones rápidas cuando un video falla o al cambiar de canción.
     * @param revertToAudioStream Si es true, reemplaza físicamente el MediaItem por la versión de audio.
     */
    private fun forceAudioOnly(revertToAudioStream: Boolean = false) {
        Log.d("PlaybackViewModel", "Forzando agresivamente modo Solo Audio (revertStream=$revertToAudioStream)")
        
        // 1. Sincronización inmediata del estado lógico
        videoState = if (currentSong?.path?.startsWith("http") == true) VideoState.AUDIO_ONLY else VideoState.DISABLED
        
        // 2. Destrucción del pipeline de video en Media3
        mediaController?.let { controller ->
            try {
                // Eliminamos la superficie de renderizado para detener la salida de video
                controller.setVideoSurface(null)
                
                // Si estamos en un stream de video, conmutamos físicamente al stream de audio original
                if (revertToAudioStream) {
                    val song = currentSong
                    if (song != null) {
                        val currentPos = controller.currentPosition
                        val isPlaying = controller.isPlaying
                        
                        val audioItem = createMediaItem(song)
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex != -1) {
                            controller.replaceMediaItem(currentIndex, audioItem)
                            controller.seekTo(currentIndex, currentPos)
                            controller.prepare()
                            if (isPlaying) controller.play()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackViewModel", "Error al forzar audio-only", e)
            }
        }
    }
    
    /**
     * Alterna entre el modo de audio y el modo de video (Canva).
     * @param forceVideo Si es true, intenta activar el video ignorando verificaciones previas.
     * @param forceAudio Si es true, fuerza la reversión inmediata al flujo de audio.
     * @param isAutoLoad Indica si la carga es automática (ej. preferencia persistente).
     */
    fun toggleVideoMode(
        forceVideo: Boolean = false, 
        forceAudio: Boolean = false,
        isAutoLoad: Boolean = false
    ) {
        // ESCUDO DE ESTABILIDAD: Evitamos peticiones concurrentes que puedan corromper el estado del reproductor
        if (isTogglingVideo && !forceAudio) {
            Log.d("PlaybackViewModel", "Cambio de modo ya en curso. Ignorando petición.")
            return
        }
        
        Log.d("PlaybackViewModel", "toggleVideoMode: forceVideo=$forceVideo, forceAudio=$forceAudio, isAutoLoad=$isAutoLoad, estado=$videoState, canShowVideo=$canShowVideo")
        if (currentSong == null) return
        
        // Verificamos si la pista actual permite el cambio manual de modo
        if (!canManualToggleVideo && !forceAudio && !forceVideo && !isAutoLoad) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Video no disponible"))
            }
            return
        }
        
        // Lógica de FORZAR AUDIO: Reversión inmediata y limpieza de estados
        if (forceAudio) {
            userWantsVideo = false
            videoState = if (currentSong?.path?.startsWith("http") == true) VideoState.AUDIO_ONLY else VideoState.DISABLED
            forceAudioOnly(revertToAudioStream = true)
            return
        }
        
        if (videoState == VideoState.CHECKING && !forceVideo) return
        
        // Pre-verificación rápida de disponibilidad
        if (!canShowVideo && !forceVideo) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast("Video no disponible"))
            }
            return
        }
        
        // Lógica para DESACTIVAR el modo video
        if ((videoState == VideoState.VIDEO_READY || videoState == VideoState.CHECKING) && !forceVideo) {
            userWantsVideo = false
            forceAudioOnly(revertToAudioStream = true)
            return
        }
        
        // Lógica para ACTIVAR el modo video (Fase de Verificación/CHECKING)
        viewModelScope.launch {
            try {
                isTransitioningToVideo = true
                isTogglingVideo = true
                isVideoSizeValid = false
                val song = currentSong!!
                
                // 1. Resolución de URL de Video: Optimizamos usando la URL pre-focussed si existe
                if (youtubeVideoUrl == null) {
                    Log.d("PlaybackViewModel", "Obteniendo URL de video (sin pre-carga disponible)...")
                    val result = streamRepository.getVideoStreamUrl(song)
                    handleVideoResult(result, song)
                }
                
                val videoUrl = youtubeVideoUrl
                
                if (videoUrl == null) {
                    Log.d("PlaybackViewModel", "No se encontró URL de video para esta canción.")
                    canShowVideo = false 
                    unsupportedVideoIds.add(song.path) 
                    
                    userWantsVideo = false 
                    videoState = VideoState.AUDIO_ONLY
                    forceAudioOnly(revertToAudioStream = false) 
                    _uiEvents.emit(UiEvent.ShowToast("Video no disponible"))
                    isTransitioningToVideo = false
                    return@launch
                }
                
                if (!isAutoLoad) {
                    userWantsVideo = true // El usuario solicitó explícitamente el video
                }
                videoState = VideoState.CHECKING

                if (!userWantsVideo) return@launch
                
                   Log.d("PlaybackViewModel", "URL de video localizada. Reemplazando MediaItem en Media3.")
                   
                   // 2. Cambio de Stream de forma Transparente
                   mediaController?.let { controller ->
                       val currentPos = controller.currentPosition
                       val isPlaying = controller.isPlaying
                       
                       val metadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                        
                       val videoItem = MediaItem.Builder()
                            .setUri(Uri.parse(videoUrl))
                            .setMediaMetadata(metadata)
                            .setMediaId(song.id.toString())
                            // IMPORTANTE: Preservamos el mediaUri ORIGINAL en los metadatos de petición
                            // Esto permite al servicio de fondo seguir identificando la canción original
                            .setRequestMetadata(
                                MediaItem.RequestMetadata.Builder()
                                    .setMediaUri(Uri.parse(song.path))
                                    .build()
                            )
                            .build()

                       val currentIndex = controller.currentMediaItemIndex
                       controller.replaceMediaItem(currentIndex, videoItem)
                       
                       // Restauramos la posición exacta de reproducción para evitar saltos temporales
                       controller.seekTo(currentIndex, currentPos)
                       controller.prepare()
                        if (isPlaying) controller.play()
                        
                        // El estado se mantiene en CHECKING hasta que se confirme renderizado físico
                      }
            } catch (e: Exception) {
                Log.e("PlaybackViewModel", "Error crítico en toggleVideoMode", e)
                videoState = VideoState.AUDIO_ONLY
                userWantsVideo = false 
            } finally {
                isTransitioningToVideo = false
                isTogglingVideo = false
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        
        if (minutes <= 0) return

        val durationMillis = minutes * 60 * 1000L
        sleepTimerDuration = durationMillis
        isSleepTimerRunning = true
        
        sleepTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + durationMillis
            
            // Fade out starts 30 seconds before end
            val fadeDuration = 30_000L
            var isFading = false
            
            // Store initial volume to restore later if needed (though usually we stop)
            // Note: getting volume might be async or property, we try to get current
            var startVolume = mediaController?.volume ?: 1f
            
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val remaining = endTime - currentTime
                sleepTimerDuration = remaining
                
                if (remaining <= 0) {
                    break
                }
                
                // Fade out logic
                if (remaining <= fadeDuration) {
                    if (!isFading) {
                        isFading = true
                        startVolume = mediaController?.volume ?: 1f
                    }
                    
                    // Calculate volume based on remaining time
                    // remaining: 30000 -> 1.0 * startVolume
                    // remaining: 0     -> 0.0
                    val progress = remaining.toFloat() / fadeDuration
                    val newVolume = progress * startVolume
                    
                    try {
                        mediaController?.volume = newVolume
                    } catch (e: Exception) {
                        // Ignore if volume control not supported
                    }
                }
                
                delay(1000)
            }
            
            mediaController?.pause()
            // Restore volume for next time (optional, but good practice if user resumes manually)
            try {
                mediaController?.volume = startVolume
            } catch (e: Exception) { }
            
            cancelSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        isSleepTimerRunning = false
        sleepTimerDuration = null
    }

    // Lyrics Management
    fun saveLyrics(songId: Long, content: String) {
        viewModelScope.launch {
            lyricsRepository.saveLyrics(songId, content)
            if (currentSong?.id == songId) {
                lyrics = content
                lyricsSource = "SimpMusic"
                lyricsLoadingState = LyricsLoadingState.Success
            }
        }
    }
    
    fun importLrcFile(songId: Long, lrcContent: String) {
        viewModelScope.launch {
            val success = lyricsRepository.importLrcFile(songId, lrcContent)
            if (success && currentSong?.id == songId) {
                lyrics = lrcContent
                lyricsSource = "SimpMusic"
                lyricsLoadingState = LyricsLoadingState.Success
            }
        }
    }
    
    fun openLyricsSearch(context: android.content.Context, song: Song) {
        val searchQuery = "${song.title} ${song.artist}".replace(" ", "%20")
        val url = "https://lyrics.simpmusic.org/search?q=$searchQuery"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    }
    
    fun retryLoadLyrics() {
        currentSong?.let { song ->
            loadLyrics(song)
        }
    }


    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            // Clear old lyrics immediately to avoid showing stale content
            lyrics = null
            lyricsSource = null
            lyricsLoadingState = LyricsLoadingState.Loading
            
            lyricsLoadingState = LyricsLoadingState.Loading
            
            // Always parse and clean title, even for local songs
            // This fixes lyrics for downloaded songs that retain the YouTube video title
            val (parsedArtist, parsedTitle) = com.amurayada.music.utils.YouTubeMusicParser.parseTitle(song.title)
            val cleanTitle = com.amurayada.music.utils.YouTubeMusicParser.cleanTitleForLyrics(parsedTitle)
            
            android.util.Log.d("PlaybackViewModel", "Parsed title: '${song.title}' -> Artist: '$parsedArtist', Title: '$cleanTitle'")
            
            val finalArtist = if (parsedArtist != "Unknown") {
                parsedArtist
            } else if (song.artist != "Unknown") {
                song.artist
            } else {
                "Unknown"
            }
            
            val searchSong = song.copy(
                title = cleanTitle,
                artist = finalArtist
            )
            
            when (val result = lyricsRepository.getLyrics(searchSong)) {
                is LyricsResult.Success -> {
                    if (result.source == "None") {
                        lyrics = null
                        lyricsSource = null
                        lyricsLoadingState = LyricsLoadingState.Idle // Or NotFound state if you have one
                    } else {
                        lyrics = result.lyrics
                        lyricsSource = result.source
                        lyricsLoadingState = LyricsLoadingState.Success
                    }
                }
                is LyricsResult.NotFound -> {
                    lyrics = null
                    lyricsSource = null
                    lyricsLoadingState = LyricsLoadingState.Idle
                }
                is LyricsResult.Error -> {
                    lyrics = null
                    lyricsSource = null
                    lyricsLoadingState = LyricsLoadingState.Error(result.message)
                }
            }
            
            // Prefetch next song lyrics in background
            prefetchNextSongLyrics()
        }
    }
    
    private fun prefetchNextSongLyrics() {
        viewModelScope.launch {
            try {
                val currentIndex = mediaController?.currentMediaItemIndex ?: return@launch
                val nextIndex = currentIndex + 1
                
                if (nextIndex < queue.size) {
                    val nextSong = queue[nextIndex]
                    // Only prefetch if not already cached (getLyrics will check cache first)
                    lyricsRepository.getLyrics(nextSong)
                    android.util.Log.d("PlaybackViewModel", "Prefetched lyrics for: ${nextSong.title}")
                }
            } catch (e: Exception) {
                // Silently ignore prefetch errors
            }
        }
    }
    
    private fun loadLyrics(songId: Long) {
        // Legacy method - find song and call new method
        currentSong?.let { song ->
            if (song.id == songId) {
                loadLyrics(song)
            }
        }
    }

    /**
     * Get the current song with its original YouTube URL (for downloads).
     * This is needed because during playback, the path might be replaced with 
     * a temporary googlevideo URL that won't work for downloads.
     */
    fun getSongForDownload(): Song? {
        val song = currentSong ?: return null
        // Prioritize cached original path
        val originalPath = originalPathCache[song.id]
        return if (originalPath != null && originalPath.contains("youtube")) {
            song.copy(path = originalPath)
        } else if (song.path.isEmpty() || song.path.contains("googlevideo")) {
            // Try to find in queue
            queue.find { it.id == song.id }?.let { queueSong ->
                if (queueSong.path.contains("youtube")) {
                    queueSong
                } else null
            } ?: song // Return as-is if nothing better found
        } else {
            song
        }
    }





    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        cancelSleepTimer()
    }
}

/**
 * Loading states for lyrics fetching
 */
sealed class LyricsLoadingState {
    data object Idle : LyricsLoadingState()
    data object Loading : LyricsLoadingState()
    data object Success : LyricsLoadingState()
    data object NotFound : LyricsLoadingState()
    data class Error(val message: String) : LyricsLoadingState()
}
