package com.amurayada.music.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.amurayada.music.data.model.PlaybackMode
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.RepeatMode
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.viewmodel.LyricsLoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    playbackState: PlaybackState,
    currentPosition: Long,
    playbackMode: PlaybackMode,
    isFavorite: Boolean,
    queue: List<Song> = emptyList(),
    lyrics: String? = null,
    lyricsLoadingState: LyricsLoadingState = LyricsLoadingState.Idle,
    sleepTimerDuration: Long? = null,
    isSleepTimerRunning: Boolean = false,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onBackClick: () -> Unit,
    onQueueItemClick: (Song) -> Unit = {},
    onSaveLyrics: (String) -> Unit = {},
    onRetryLoadLyrics: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onImportLrc: (String) -> Unit = {},
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onGoToAlbum: (Long) -> Unit = {},
    onRemoveFromQueue: (Song) -> Unit = {},
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    isAmoledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    if (currentSong == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "No hay música reproduciéndose",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    // Animated scale for album art
    val infiniteTransition = rememberInfiniteTransition(label = "playing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (playbackState is PlaybackState.Playing) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Dynamic colors from album art
    var gradientColor by remember { mutableStateOf(Color.Black) }
    
    LaunchedEffect(currentSong.albumArtUri) {
        try {
            val request = ImageRequest.Builder(context)
                .data(currentSong.albumArtUri)
                .allowHardware(false)
                .build()
            val result = ImageLoader(context).execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let {
                Palette.from(it).generate { palette ->
                    gradientColor = getColorFromPalette(palette)
                }
            }
        } catch (e: Exception) {
            gradientColor = Color.Black
        }
    }
    
    // Dialog states
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    
    val lrcPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val content = java.io.BufferedReader(java.io.InputStreamReader(inputStream)).readText()
                    onImportLrc(content)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    var showSyncedLyrics by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }

    // Animation State
    val dismissOffsetY = remember { Animatable(0f) }
    val dismissScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    
    // Gesture State for horizontal swipes
    var horizontalDragOffset by remember { mutableStateOf(0f) }
    
    Box(modifier = modifier
        .fillMaxSize()
        .offset { IntOffset(0, dismissOffsetY.value.roundToInt()) }
        .scale(dismissScale.value)
        .pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    scope.launch {
                        // Vertical Dismiss Logic
                        if (dismissOffsetY.value > 300f) {
                            // Dismiss
                            launch { dismissOffsetY.animateTo(targetValue = 2000f, animationSpec = tween(300)) }
                            onBackClick()
                        } else {
                            // Bounce back
                            launch { dismissOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            launch { dismissScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        }
                        
                        // Horizontal Swipe Logic
                        if (kotlin.math.abs(horizontalDragOffset) > 100) {
                            if (horizontalDragOffset > 0) onSkipPreviousClick() else onSkipNextClick()
                        }
                        horizontalDragOffset = 0f
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    
                    // Vertical Drag (Dismiss)
                    if (dragAmount.y > 0 || dismissOffsetY.value > 0) {
                        scope.launch {
                            val newOffset = (dismissOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                            dismissOffsetY.snapTo(newOffset)
                            // Scale down slightly as we drag down
                            val progress = (newOffset / 1000f).coerceIn(0f, 1f)
                            dismissScale.snapTo(1f - (progress * 0.1f))
                        }
                    }
                    
                    // Horizontal Drag (Skip) - Only if not dragging vertically significantly
                    if (dismissOffsetY.value < 50f) {
                        horizontalDragOffset += dragAmount.x
                    }
                }
            )
        }
    ) {
        if (!isAmoledMode) {
            // Gradient background to prevent black flash during image transitions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to gradientColor,
                            0.8f to Color.Black
                        )
                    )
            )
            // Blur Effect Background
            AsyncImage(
                model = androidx.compose.ui.platform.LocalContext.current.let { context ->
                    coil.request.ImageRequest.Builder(context)
                        .data(currentSong.albumArtUri)
                        .crossfade(300) // Smooth crossfade
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
                    .scale(1.5f), // Scale up to hide blur edges
                contentScale = ContentScale.Crop,
                alpha = 1f // Fully opaque
            )
            // Overlay to ensure text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        } else {
            // AMOLED Mode: Always show gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to gradientColor,
                            0.8f to Color.Black
                        )
                    )
            )
        }
        
        // Layout Content
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Album Art / Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showSyncedLyrics) {
                        // Show Album Art Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .scale(scale),
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
                    } else {
                        // Show Lyrics with same container size as album art
                        val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (parsedLyrics.isEmpty()) {
                                LyricsEmptyState(
                                    loadingState = lyricsLoadingState,
                                    onRetry = onRetryLoadLyrics,
                                    onSearchLyrics = onSearchLyrics
                                )
                            }
                            else {
                                // Show current and surrounding lines with animation
                                val currentIndex = parsedLyrics.indexOfLast { it.timestamp <= currentPosition }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Previous line (if exists)
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
                                    
                                    // Current line - highlighted with animation
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
                                        // Show first line when song hasn't started
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
                                    
                                    // Next line (if exists)
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
                            
                            // "Ver letra completa" button at bottom of container
                            TextButton(
                                onClick = { showFullLyrics = true },
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
                    }
                }
                
                // Right Side: Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Editar letra") },
                                    onClick = { 
                                        showMenu = false
                                        showLyricsDialog = true 
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Buscar letras en línea") },
                                    onClick = { 
                                        showMenu = false
                                        onSearchLyrics()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Importar archivo LRC") },
                                    onClick = { 
                                        showMenu = false
                                        lrcPicker.launch("*/*")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ir al álbum") },
                                    onClick = { 
                                        showMenu = false
                                        onGoToAlbum(currentSong.albumId)
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Información") },
                                    onClick = { 
                                        showMenu = false
                                        showSongInfoDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Temporizador") },
                                    onClick = { 
                                        showMenu = false
                                        showSleepTimerDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Controls
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Song Info with Favorite button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                Text(
                                    text = currentSong.album,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                        
                        // Progress Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { onSeek(it.toLong()) },
                                valueRange = 0f..currentSong.duration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = gradientColor,
                                    activeTrackColor = gradientColor,
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
                                    text = formatDuration(currentSong.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // Playback Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onShuffleClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = if (playbackMode.isShuffleEnabled) gradientColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onSkipPreviousClick,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            FilledIconButton(
                                onClick = onPlayPauseClick,
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = gradientColor
                                )
                            ) {
                                Icon(
                                    imageVector = if (playbackState is PlaybackState.Playing) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = if (playbackState is PlaybackState.Playing) "Pausar" else "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onSkipNextClick,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onRepeatClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = when (playbackMode.repeatMode) {
                                        com.amurayada.music.data.model.RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                        else -> Icons.Rounded.Repeat
                                    },
                                    contentDescription = "Repetir",
                                    tint = if (playbackMode.repeatMode != com.amurayada.music.data.model.RepeatMode.OFF) gradientColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        // Bottom Options Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showSyncedLyrics = !showSyncedLyrics }) {
                                Icon(
                                    Icons.Rounded.Lyrics,
                                    contentDescription = "Letra",
                                    tint = if (showSyncedLyrics) gradientColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            IconButton(onClick = { showSleepTimerDialog = true }) {
                                Icon(
                                    Icons.Rounded.Bedtime,
                                    contentDescription = "Temporizador",
                                    tint = if (isSleepTimerRunning) gradientColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            IconButton(onClick = { 
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Escuchando ${currentSong.title} de ${currentSong.artist}")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir"))
                            }) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = "Compartir",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            IconButton(onClick = { showQueueSheet = true }) {
                                Icon(
                                    Icons.Rounded.QueueMusic,
                                    contentDescription = "Cola",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Más opciones",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar letra") },
                                onClick = { 
                                    showMenu = false
                                    showLyricsDialog = true 
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Buscar letras en línea") },
                                onClick = { 
                                    showMenu = false
                                    onSearchLyrics()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar archivo LRC") },
                                onClick = { 
                                    showMenu = false
                                    lrcPicker.launch("*/*")
                                },
                                leadingIcon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Ir al álbum") },
                                onClick = { 
                                    showMenu = false
                                    onGoToAlbum(currentSong.albumId)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Información") },
                                onClick = { 
                                    showMenu = false
                                    showSongInfoDialog = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Temporizador") },
                                onClick = { 
                                    showMenu = false
                                    showSleepTimerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.15f))
                
                // Album Art OR Lyrics (mutually exclusive)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showSyncedLyrics) {
                        // Show Album Art Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .scale(scale),
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
                    } else {
                        // Show Lyrics with same container size as album art
                        val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (parsedLyrics.isEmpty()) {
                                LyricsEmptyState(
                                    loadingState = lyricsLoadingState,
                                    onRetry = onRetryLoadLyrics,
                                    onSearchLyrics = onSearchLyrics
                                )
                            } else {
                                // Show current and surrounding lines with animation
                                val currentIndex = parsedLyrics.indexOfLast { it.timestamp <= currentPosition }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Previous line (if exists)
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
                                    
                                    // Current line - highlighted with animation
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
                                        // Show first line when song hasn't started
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
                                    
                                    // Next line (if exists)
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
                            
                            // "Ver letra completa" button at bottom of container
                            TextButton(
                                onClick = { showFullLyrics = true },
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
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.2f))
                
                // Song Info with Favorite button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                        Text(
                            text = currentSong.album,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..currentSong.duration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = gradientColor,
                            activeTrackColor = gradientColor,
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
                            text = formatDuration(currentSong.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShuffleClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (playbackMode.isShuffleEnabled) gradientColor else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onSkipPreviousClick,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    FilledIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = gradientColor
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState is PlaybackState.Playing) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (playbackState is PlaybackState.Playing) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onSkipNextClick,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onRepeatClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = when (playbackMode.repeatMode) {
                                com.amurayada.music.data.model.RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                else -> Icons.Rounded.Repeat
                            },
                            contentDescription = "Repetir",
                            tint = if (playbackMode.repeatMode != com.amurayada.music.data.model.RepeatMode.OFF) gradientColor else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(0.15f))
                    
                    // Bottom Options Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSyncedLyrics = !showSyncedLyrics }) {
                            Icon(
                                Icons.Rounded.Lyrics,
                                contentDescription = "Letra",
                                tint = if (showSyncedLyrics) gradientColor else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                Icons.Rounded.Bedtime,
                                contentDescription = "Temporizador",
                                tint = if (isSleepTimerRunning) gradientColor else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = { 
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Escuchando ${currentSong.title} de ${currentSong.artist}")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir"))
                        }) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = "Compartir",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = "Cola",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    // Full Screen Lyrics View
    if (showFullLyrics) {
        SyncedLyricsView(
            lyrics = lyrics,
            currentPosition = currentPosition,
            gradientColor = gradientColor,
            onClose = { showFullLyrics = false },
            onSaveLyrics = { onSaveLyrics(it) },
            onSkipPrevious = onSkipPreviousClick,
            onSkipNext = onSkipNextClick
        )
    }

    // Dialogs
    if (showLyricsDialog) {
        EditLyricsDialog(
            initialLyrics = lyrics ?: "",
            onDismiss = { showLyricsDialog = false },
            onSave = { 
                onSaveLyrics(it)
                showLyricsDialog = false
            }
        )
    }
    
    if (showSongInfoDialog) {
        SongInfoDialog(
            song = currentSong,
            onDismiss = { showSongInfoDialog = false }
        )
    }
    
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isRunning = isSleepTimerRunning,
            remainingTime = sleepTimerDuration,
            onDismiss = { showSleepTimerDialog = false },
            onStartTimer = { 
                onStartSleepTimer(it)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            }
        )
    }

    // Queue bottom sheet - FUNCTIONAL
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cola de reproducción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${queue.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                HorizontalDivider()
                
                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "La cola está vacía",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val density = LocalDensity.current
                    val itemHeight = 72.dp // Estimated height of a queue item
                    val itemHeightPx = with(density) { itemHeight.toPx() }
                    
                    // State for reordering
                    var draggingIndex by remember { mutableStateOf(-1) }
                    var dragOffset by remember { mutableStateOf(0f) }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "queue_item" }
                        ) { index, song ->
                            val isPlaying = song.id == currentSong.id
                            val isDragging = index == draggingIndex
                            
                            // Swipe to Dismiss State
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        onRemoveFromQueue(song)
                                        true
                                    } else false
                                }
                            )
                            
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) 
                                        MaterialTheme.colorScheme.errorContainer 
                                    else Color.Transparent
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationY = dragOffset
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            shadowElevation = 8.dp.toPx()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { 
                                            onQueueItemClick(song)
                                            showQueueSheet = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Album art
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song.albumArtUri)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .size(144)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isPlaying) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    if (isPlaying) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Reproduciendo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        // Reorder Handle (Only for non-playing songs)
                                        Icon(
                                            Icons.Rounded.DragHandle,
                                            contentDescription = "Reordenar",
                                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .pointerInput(Unit) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { 
                                                            draggingIndex = index
                                                            dragOffset = 0f
                                                        },
                                                        onDragEnd = { 
                                                            draggingIndex = -1
                                                            dragOffset = 0f
                                                        },
                                                        onDragCancel = { 
                                                            draggingIndex = -1
                                                            dragOffset = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffset += dragAmount.y
                                                            
                                                            // Calculate swap
                                                            val itemsToMove = (dragOffset / itemHeightPx).toInt()
                                                            if (itemsToMove != 0) {
                                                                val targetIndex = (draggingIndex + itemsToMove).coerceIn(0, queue.lastIndex)
                                                                // Prevent swapping with current song if we want to enforce it strictly, 
                                                                // but for now just preventing dragging OF current song is enough.
                                                                // Actually, if we swap with current song, current song moves. 
                                                                // User said "current song should not move". 
                                                                // If I drag another song, can I swap it with current song? 
                                                                // Probably yes, just can't drag current song itself.
                                                                
                                                                if (targetIndex != draggingIndex) {
                                                                    onReorderQueue(draggingIndex, targetIndex)
                                                                    draggingIndex = targetIndex
                                                                    dragOffset -= itemsToMove * itemHeightPx
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncedLyricsView(
    lyrics: String?,
    currentPosition: Long,
    gradientColor: Color,
    onClose: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedLyrics by remember(lyrics) { mutableStateOf(lyrics ?: "") }
    val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Auto-scroll logic (only when not editing)
    LaunchedEffect(currentPosition, parsedLyrics, isEditing) {
        if (parsedLyrics.isNotEmpty() && !isEditing) {
            val currentIndex = parsedLyrics.indexOfLast { it.timestamp <= currentPosition }
            if (currentIndex >= 0) {
                listState.animateScrollToItem(currentIndex, scrollOffset = -200)
            }
        }
    }

    // Gesture State
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > kotlin.math.abs(offsetY)) {
                            // Horizontal Swipe
                            if (kotlin.math.abs(offsetX) > 100) {
                                if (offsetX > 0) onSkipPrevious() else onSkipNext()
                            }
                        } else {
                            // Vertical Swipe
                            if (offsetY > 100) {
                                onClose()
                            }
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(gradientColor, Color.Black, Color.Black),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { /* Block clicks */ }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with status bar padding
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Text(
                    text = if (isEditing) "Editar Letra" else "Letra",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { 
                    if (isEditing) {
                        onSaveLyrics(editedLyrics)
                        isEditing = false
                    } else {
                        isEditing = true
                    }
                }) {
                    Icon(
                        if (isEditing) Icons.Rounded.Check else Icons.Rounded.Edit, 
                        contentDescription = if (isEditing) "Guardar" else "Editar", 
                        tint = if (isEditing) Color(0xFF4CAF50) else Color.White
                    )
                }
            }
            
            if (isEditing) {
                // Edit mode - show text field
                OutlinedTextField(
                    value = editedLyrics,
                    onValueChange = { editedLyrics = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White
                    ),
                    placeholder = {
                        Text(
                            "Pega la letra aquí...\n\nFormato sincronizado:\n[00:12.34] Primera línea\n[00:15.67] Segunda línea\n\nO letra sin sincronizar:\nPrimera línea\nSegunda línea",
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                )
            } else {
                // View mode
                if (parsedLyrics.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No hay letra disponible",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { isEditing = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar letra", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 100.dp, horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(parsedLyrics) { index, line ->
                            val isCurrent = if (index == parsedLyrics.lastIndex) {
                                currentPosition >= line.timestamp
                            } else {
                                currentPosition >= line.timestamp && currentPosition < parsedLyrics[index + 1].timestamp
                            }
                            
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .scale(if (isCurrent) 1.1f else 1f)
                                    .animateContentSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditLyricsDialog(
    initialLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialLyrics) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Letra") },
        text = {
            Column {
                Text(
                    "Formato: [mm.ss.ms] Texto\nEjemplo: [00.13.15] Hola mundo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    placeholder = { Text("[00.00.00] Intro...") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun SongInfoDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información de la canción") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                // Album Art
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .size(150.dp)
                        .padding(bottom = 8.dp)
                ) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Portada del álbum",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow("Título", song.title)
                    InfoRow("Artista", song.artist)
                    InfoRow("Álbum", song.album)
                    InfoRow("Duración", formatDuration(song.duration))
                    InfoRow("Ruta", song.path)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SleepTimerDialog(
    isRunning: Boolean,
    remainingTime: Long?,
    onDismiss: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var customTime by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador de sueño") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    Text("Temporizador activo")
                    Text(
                        "Se detendrá en aprox. ${(remainingTime ?: 0) / 1000 / 60} min",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancelar Temporizador")
                    }
                } else {
                    if (showCustomInput) {
                        OutlinedTextField(
                            value = customTime,
                            onValueChange = { if (it.all { char -> char.isDigit() }) customTime = it },
                            label = { Text("Minutos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        TimerOption(15, onStartTimer)
                        TimerOption(30, onStartTimer)
                        TimerOption(60, onStartTimer)
                        OutlinedButton(
                            onClick = { showCustomInput = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Personalizado")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRunning && showCustomInput) {
                TextButton(
                    onClick = {
                        val minutes = customTime.toIntOrNull()
                        if (minutes != null && minutes > 0) {
                            onStartTimer(minutes)
                        }
                    }
                ) {
                    Text("Iniciar")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        },
        dismissButton = {
            if (showCustomInput) {
                TextButton(onClick = { showCustomInput = false }) {
                    Text("Atrás")
                }
            }
        }
    )
}

@Composable
fun TimerOption(minutes: Int, onSelect: (Int) -> Unit) {
    OutlinedButton(
        onClick = { onSelect(minutes) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("$minutes minutos")
    }
}

@Composable
private fun LyricsEmptyState(
    loadingState: LyricsLoadingState,
    onRetry: () -> Unit,
    onSearchLyrics: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (loadingState) {
            is LyricsLoadingState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cargando letras...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            is LyricsLoadingState.NotFound -> {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay letras disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Usa el menú para buscar o editar",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onSearchLyrics,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buscar en línea")
                }
            }
            else -> {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay letra disponible",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class LyricLine(val timestamp: Long, val text: String)

private fun parseLyrics(lyrics: String?): List<LyricLine> {
    if (lyrics.isNullOrBlank()) return emptyList()
    
    val regex = Regex("\\[(\\d{2})[.:](\\d{2})[.:](\\d{2,3})](.*)")
    val lines = mutableListOf<LyricLine>()
    
    lyrics.lines().forEach { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, ms, text) = match.destructured
            val minutes = min.toLong()
            val seconds = sec.toLong()
            // Handle 2 or 3 digit milliseconds
            val milliseconds = if (ms.length == 2) ms.toLong() * 10 else ms.toLong()
            
            val totalMillis = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
            lines.add(LyricLine(totalMillis, text.trim()))
        }
    }
    
    return lines.sortedBy { it.timestamp }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}

private fun getColorFromPalette(palette: Palette?): Color {
    if (palette == null) return Color.Black
    
    val defaultColor = 0x000000
    
    val darkVibrant = palette.getDarkVibrantColor(defaultColor)
    if (darkVibrant != defaultColor) return Color(darkVibrant)
    
    val darkMuted = palette.getDarkMutedColor(defaultColor)
    if (darkMuted != defaultColor) return Color(darkMuted)
    
    val vibrant = palette.getVibrantColor(defaultColor)
    if (vibrant != defaultColor) return Color(vibrant)
    
    val muted = palette.getMutedColor(defaultColor)
    if (muted != defaultColor) return Color(muted)
    
    val lightVibrant = palette.getLightVibrantColor(defaultColor)
    if (lightVibrant != defaultColor) return Color(lightVibrant)
    
    val lightMuted = palette.getLightMutedColor(defaultColor)
    if (lightMuted != defaultColor) return Color(lightMuted)
    
    return Color.Black
}
