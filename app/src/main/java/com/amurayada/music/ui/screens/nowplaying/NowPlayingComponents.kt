package com.amurayada.music.ui.screens.nowplaying

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.viewmodel.LyricsLoadingState
import com.amurayada.music.ui.viewmodel.PlaybackViewModel
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.PlaybackMode

@Composable
fun NowPlayingBackground(
    currentSong: Song,
    youtubeVideoUrl: String?,
    isInVideoMode: Boolean,
    isCanvasEnabled: Boolean = false,
    applyBackgroundToPlayer: Boolean,
    customBackgroundImageUri: String?,
    isAmoledMode: Boolean,
    playerBackgroundType: String,
    playerBackgroundColor: Int?,
    effectivePrimaryColor: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // --- Layer 1: Static Background (Always present as fallback) ---
        if (applyBackgroundToPlayer && customBackgroundImageUri != null) {
            // Custom background image with overlay
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(customBackgroundImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        } else if (playerBackgroundType == "custom" && playerBackgroundColor != null) {
            // Custom solid color background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(playerBackgroundColor))
            )
        } else {
            // Default: Simple gradient (color + black)
            // First layer: Solid black to prevent any transparency
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
            
            // Second layer: Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                effectivePrimaryColor,
                                effectivePrimaryColor.copy(alpha = 0.7f),
                                effectivePrimaryColor.copy(alpha = 0.4f),
                                Color.Black,
                                Color.Black
                            )
                        )
                    )
            )
            
            // Only show blurred album art in standard themes (NOT in AMOLED mode)
            // AND ONLY in standard Audio Mode when Canvas is NOT active.
            // "Si no hay video, no hay Canva" - Avoid immersive background that looks like a static video.
            if (!isAmoledMode && !isInVideoMode && !isCanvasEnabled) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSong.albumArtUri)
                        .crossfade(300)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .scale(1.5f),
                    contentScale = ContentScale.Crop,
                    alpha = 1f
                )
                
                // Dark overlay for better text legibility (only in standard themes)
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val overlayAlpha = if (isDarkTheme) 0.3f else 0.2f
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }
        }

        // --- Layer 2: Canvas Video (Overlays Layer 1 when available) ---
        // We keep it always mounted if enabled to prevent flickering/re-initialization
        if (isCanvasEnabled && !isInVideoMode) {
            com.amurayada.music.ui.components.CanvasPlayer(
                uri = youtubeVideoUrl,
                modifier = Modifier.fillMaxSize(),
                onError = { /* Logged in CanvasPlayer */ }
            )
        }
    }
}

@Composable
fun NowPlayingTopBar(
    videoState: PlaybackViewModel.VideoState,
    canShowVideo: Boolean,
    canManualToggleVideo: Boolean = false,
    onBackClick: () -> Unit,
    onToggleVideoMode: () -> Unit,
    showMenu: Boolean,
    onShowMenu: (Boolean) -> Unit,
    // Menú Callbacks
    song: Song? = null,
    onGoToAlbum: (Long) -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onDownloadClick: (Song) -> Unit = {},
    onShowSongInfoDialog: () -> Unit = {},
    onShowShareDialog: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onImportLrc: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Cerrar",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        SongVideoToggle(
            isVideoMode = videoState != PlaybackViewModel.VideoState.AUDIO_ONLY && videoState != PlaybackViewModel.VideoState.DISABLED,
            isVideoLoading = videoState == PlaybackViewModel.VideoState.CHECKING,
            canShowVideo = canManualToggleVideo && videoState != PlaybackViewModel.VideoState.DISABLED,
            onToggle = { if (canManualToggleVideo) onToggleVideoMode() },
            modifier = Modifier.align(Alignment.Center).zIndex(10f)
        )
        
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(
                onClick = { onShowMenu(true) }
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Más opciones",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenu(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (song != null && song.albumId != -1L) {
                    DropdownMenuItem(
                        text = { Text("Ir al álbum") },
                        onClick = {
                            onShowMenu(false)
                            onGoToAlbum(song.albumId)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Album, null) }
                    )
                }
                
                DropdownMenuItem(
                    text = { Text("Ecualizador") },
                    onClick = {
                        onShowMenu(false)
                        onEqualizerClick()
                    },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) }
                )
                
                DropdownMenuItem(
                    text = { Text("Información de la canción") },
                    onClick = {
                        onShowMenu(false)
                        onShowSongInfoDialog()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Info, null) }
                )
                
                if (song != null) {
                    DropdownMenuItem(
                        text = { Text("Descargar") },
                        onClick = {
                            onShowMenu(false)
                            onDownloadClick(song)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) }
                    )
                }
                
                DropdownMenuItem(
                    text = { Text("Compartir") },
                    onClick = {
                        onShowMenu(false)
                        onShowShareDialog()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Share, null) }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                DropdownMenuItem(
                    text = { Text("Buscar letras online") },
                    onClick = {
                        onShowMenu(false)
                        onSearchLyrics()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }
                )

                DropdownMenuItem(
                    text = { Text("Importar letras (.lrc)") },
                    onClick = {
                        onShowMenu(false)
                        onImportLrc()
                    },
                    leadingIcon = { Icon(Icons.Rounded.FileOpen, null) }
                )

                DropdownMenuItem(
                    text = { Text("Editar letras") },
                    onClick = {
                        onShowMenu(false)
                        onEditLyrics()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                )
            }
        }
    }
}

@Composable
fun NowPlayingSongInfo(
    currentSong: Song,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    immersiveAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = immersiveAlpha }
        ) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (immersiveAlpha > 0.5f) {
                Text(
                    text = currentSong.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                tint = if (isFavorite) Color.Red else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun NowPlayingControls(
    currentPosition: Long,
    duration: Long,
    playbackState: PlaybackState,
    playbackMode: PlaybackMode,
    effectivePrimaryColor: Color,
    onSeek: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableStateOf(0f) }
        
        Slider(
            value = if (isDragging) dragPosition else currentPosition.toFloat(),
            onValueChange = { newValue -> 
                isDragging = true
                dragPosition = newValue 
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toLong())
                isDragging = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = effectivePrimaryColor,
                activeTrackColor = effectivePrimaryColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        com.amurayada.music.ui.components.PlaybackControls(
            playbackState = playbackState,
            playbackMode = playbackMode,
            onPlayPauseClick = onPlayPauseClick,
            onSkipNextClick = onSkipNextClick,
            onSkipPreviousClick = onSkipPreviousClick,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
            accentColor = effectivePrimaryColor
        )
    }
}

@Composable
fun NowPlayingBottomOptions(
    showSyncedLyrics: Boolean,
    isSleepTimerRunning: Boolean,
    effectivePrimaryColor: Color,
    onToggleLyrics: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowShare: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenHandsFree: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleLyrics) {
            Icon(
                Icons.Rounded.Lyrics,
                contentDescription = "Letra",
                tint = if (showSyncedLyrics) effectivePrimaryColor else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(onClick = onShowSleepTimer) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = "Temporizador",
                tint = if (isSleepTimerRunning) effectivePrimaryColor else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(onClick = onShowShare) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = "Compartir",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(onClick = onShowQueue) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Cola",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(onClick = onOpenHandsFree) {
            Icon(
                Icons.Rounded.DirectionsCar,
                contentDescription = "Manos Libres",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun LyricsEmptyState(
    loadingState: LyricsLoadingState,
    onRetry: () -> Unit,
    onSearchLyrics: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        when (loadingState) {
            LyricsLoadingState.Loading -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Buscando letras...", color = Color.White)
            }
            is LyricsLoadingState.Error -> {
                Icon(
                    Icons.Rounded.Lyrics, 
                    contentDescription = null, 
                    tint = Color.White.copy(alpha = 0.15f), 
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No se pudieron cargar las letras", 
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Premium Action Chip Style Button
                Surface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Refresh, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Reintentar", 
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LyricsLoadingState.NotFound -> {
                Icon(
                    Icons.Rounded.Lyrics, 
                    contentDescription = null, 
                    tint = Color.White.copy(alpha = 0.15f), 
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No hay letras disponibles", 
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Premium Action Chip Style Button
                Surface(
                    onClick = onSearchLyrics,
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Buscar en línea", 
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            else -> {
                Icon(
                    Icons.Rounded.Lyrics, 
                    contentDescription = null, 
                    tint = Color.White.copy(alpha = 0.15f), 
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No hay letras disponibles", 
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Premium Action Chip Style Button
                Surface(
                    onClick = onSearchLyrics,
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Buscar en línea", 
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun NowPlayingContent(
    showSyncedLyrics: Boolean,
    lyrics: String?,
    lyricsLoadingState: LyricsLoadingState,
    currentPosition: Long,
    isInVideoMode: Boolean,
    videoState: PlaybackViewModel.VideoState,
    playerController: androidx.media3.common.Player?,
    isVideoSizeValid: Boolean,
    playerAlbumArtScale: Float,
    scale: Float,
    currentSong: Song,
    isCanvaPlaying: Boolean = false, // RENAME THIS
    onRetryLoadLyrics: () -> Unit,
    onSearchLyrics: () -> Unit,
    onShowFullLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Priority 1: Synced Lyrics
        if (showSyncedLyrics) {
            val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (parsedLyrics.isEmpty()) {
                    LyricsEmptyState(
                        loadingState = lyricsLoadingState,
                        onRetry = onRetryLoadLyrics,
                        onSearchLyrics = onSearchLyrics
                    )
                } else {
                    val currentIndex = parsedLyrics.indexOfLast { it.timestamp <= currentPosition }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Previous line
                        if (currentIndex > 0) {
                            Text(
                                text = parsedLyrics[currentIndex - 1].text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .scale(0.85f)
                            )
                        }
                        
                        // Current line
                        if (currentIndex >= 0 && currentIndex < parsedLyrics.size) {
                            Text(
                                text = parsedLyrics[currentIndex].text,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .scale(1.1f)
                                    .animateContentSize()
                            )
                        } else if (parsedLyrics.isNotEmpty()) {
                            Text(
                                text = parsedLyrics[0].text,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .animateContentSize()
                            )
                        }
                        
                        // Next line
                        if (currentIndex >= 0 && currentIndex + 1 < parsedLyrics.size) {
                            Text(
                                text = parsedLyrics[currentIndex + 1].text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .scale(0.85f)
                            )
                        }
                    }
                }
                
                TextButton(
                    onClick = onShowFullLyrics,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        "Ver letra completa",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.OpenInFull,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else {
            // Priority 2: Media Block
            val isVideoActive = isInVideoMode || videoState == PlaybackViewModel.VideoState.CHECKING
            
            when {
                isVideoActive && playerController != null -> {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    androidx.media3.ui.PlayerView(ctx).apply {
                                        player = playerController
                                        useController = false
                                        useArtwork = false
                                        defaultArtwork = null
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }
                                },
                                update = { view ->
                                    if (view.player != playerController) {
                                        view.player = playerController
                                    }
                                },
                                modifier = Modifier.fillMaxSize().background(Color.Transparent)
                            )
                        }
                        
                        // Show art if video size is invalid AND we are NOT expecting a Canva playing
                        if (!isVideoSizeValid && !isCanvaPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(horizontal = 40.dp)
                                    .zIndex(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .aspectRatio(1f)
                                        .scale(scale * playerAlbumArtScale),
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                                ) {
                                    AsyncImage(
                                        model = currentSong.albumArtUri,
                                        contentDescription = "Portada del álbum",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Standard audio-only mode: Show art if not expecting Canva playing
                    if (!isCanvaPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(horizontal = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .scale(scale * playerAlbumArtScale),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                            ) {
                                AsyncImage(
                                    model = currentSong.albumArtUri,
                                    contentDescription = "Portada del álbum",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        // If Canva is enabled but not in video mode, we show nothing (video is below in Layer 2)
                        Spacer(modifier = Modifier.fillMaxHeight())
                    }
                }
            }
        }
    }
}
