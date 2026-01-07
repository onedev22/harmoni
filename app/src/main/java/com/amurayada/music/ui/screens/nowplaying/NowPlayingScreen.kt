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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.ui.viewinterop.AndroidView
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
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.viewmodel.PlaybackViewModel
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
    lyricsSource: String? = null,
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
    onOpenHandsFree: () -> Unit = {},
    onDownloadClick: (Song) -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    videoState: PlaybackViewModel.VideoState = PlaybackViewModel.VideoState.AUDIO_ONLY,
    canShowVideo: Boolean = false,
    canManualToggleVideo: Boolean = false,
    isVideoSizeValid: Boolean = false,
    onToggleVideoMode: () -> Unit = {},
    playerController: androidx.media3.session.MediaController? = null,
    youtubeVideoUrl: String? = null,
    
    settingsViewModel: com.amurayada.music.ui.viewmodel.SettingsViewModel, // INJECTED
    
    isAmoledMode: Boolean = false,
    playerBackgroundType: String = "auto",
    playerBackgroundColor: Int? = null,
    playerAlbumArtScale: Float = 1.0f,

    customPrimaryColor: Int? = null,
    applyBackgroundToPlayer: Boolean = false,
    customBackgroundImageUri: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCanvasEnabled by settingsViewModel.isCanvasEnabled.collectAsState()
    
    val isInVideoMode = videoState == PlaybackViewModel.VideoState.VIDEO_READY
    // We don't check isVideoSizeValid here anymore because we want the player to attach
    // so it can REPORT the size. We will handle visibility in the layout.
    val isCheckingVideo = videoState == PlaybackViewModel.VideoState.CHECKING
    
    // UI state for transition animations
    val contentPadding = if (isInVideoMode && isVideoSizeValid) 0.dp else 40.dp
    
    // IMMERSIVE MODE LOGIC
    var isUserInteracting by remember { mutableStateOf(true) }
    // REQUIRE canShowVideo to be true to avoid "Static Image Canva" look for Topic/Audio videos
    val isCanvaPlaying = youtubeVideoUrl != null && canShowVideo && isCanvasEnabled && !isInVideoMode
    
    LaunchedEffect(isUserInteracting, isCanvaPlaying) {
        if (isUserInteracting && isCanvaPlaying) {
            kotlinx.coroutines.delay(5000)
            isUserInteracting = false
        }
    }
    
    val immersiveAlpha by animateFloatAsState(
        targetValue = if (!isUserInteracting && isCanvaPlaying) 0f else 1f,
        animationSpec = tween(1000),
        label = "immersiveAlpha"
    )
    
    // Song Info vertical position animation - move to the absolute bottom in Zen mode
    val songInfoOffset by animateDpAsState(
        targetValue = if (!isUserInteracting && isCanvaPlaying && !isInVideoMode) 60.dp else 0.dp,
        animationSpec = tween(1000),
        label = "songInfoOffset"
    )
    
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
    var gradientColors by remember { mutableStateOf(Color.Black to Color.Black) }
    val imageLoader = remember(context) { ImageLoader(context) }
    
    LaunchedEffect(currentSong.albumArtUri) {
        try {
            val request = ImageRequest.Builder(context)
                .data(currentSong.albumArtUri)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let {
                Palette.from(it).generate { palette ->
                    gradientColors = com.amurayada.music.ui.utils.extractGradientColors(palette)
                }
            }
        } catch (e: Exception) {
            gradientColors = Color.Black to Color.Black
        }
    }
    
    val effectivePrimaryColor = customPrimaryColor?.let { Color(it) } ?: gradientColors.first
    
    // Dialog states
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    if (showShareDialog) {
        com.amurayada.music.ui.components.ShareDialog(
            song = currentSong,
            dominantColor = effectivePrimaryColor,
            onDismiss = { showShareDialog = false }
        )
    }
    
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
                onDragStart = { isUserInteracting = true },
                onDragEnd = {
                    isUserInteracting = true
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
        .pointerInput(isCanvaPlaying) {
            if (isCanvaPlaying) {
                detectTapGestures(
                    onTap = { isUserInteracting = true }
                )
            }
        }
    ) {
        // Background Logic Priority:
        // 1. Custom App Background (Specific override)
        // 2. AMOLED Mode (Theme preference)
        // 4. Default Gradient/Blur (Default)

        // Background rendering continues below - video is now rendered inline in album art area
        
        // Canvas Layer (Background) - Only show if NOT in Video Mode (exclusive)
        // 1. Background Layer
        NowPlayingBackground(
            currentSong = currentSong,
            youtubeVideoUrl = youtubeVideoUrl,
            isInVideoMode = isInVideoMode,
            isCanvasEnabled = isCanvasEnabled,
            applyBackgroundToPlayer = applyBackgroundToPlayer,
            customBackgroundImageUri = customBackgroundImageUri,
            isAmoledMode = isAmoledMode,
            playerBackgroundType = playerBackgroundType,
            playerBackgroundColor = playerBackgroundColor,
            effectivePrimaryColor = effectivePrimaryColor
        )
        
        // Layout Content
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                // Left Side: Media Block (Album Art / Lyrics / Video)
                NowPlayingContent(
                    showSyncedLyrics = showSyncedLyrics,
                    lyrics = lyrics,
                    lyricsLoadingState = lyricsLoadingState,
                    currentPosition = currentPosition,
                    isInVideoMode = isInVideoMode,
                    videoState = videoState,
                    playerController = playerController,
                    isVideoSizeValid = isVideoSizeValid,
                    playerAlbumArtScale = playerAlbumArtScale,
                    scale = scale,
                    currentSong = currentSong,
                    onRetryLoadLyrics = onRetryLoadLyrics,
                    onSearchLyrics = onSearchLyrics,
                    onShowFullLyrics = { showFullLyrics = true },
                    modifier = Modifier.weight(1.2f).fillMaxHeight()
                )
                
                Spacer(modifier = Modifier.width(32.dp))
                
                // Right Side: Controls
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    NowPlayingTopBar(
                        videoState = videoState,
                        canShowVideo = canShowVideo,
                        canManualToggleVideo = canManualToggleVideo,
                        onBackClick = onBackClick,
                        onToggleVideoMode = onToggleVideoMode,
                        showMenu = showMenu,
                        onShowMenu = { showMenu = it },
                        song = currentSong,
                        onGoToAlbum = onGoToAlbum,
                        onEqualizerClick = onEqualizerClick,
                        onDownloadClick = onDownloadClick,
                        onShowSongInfoDialog = { showSongInfoDialog = true },
                        onShowShareDialog = { showShareDialog = true },
                        onSearchLyrics = onSearchLyrics,
                        onImportLrc = { lrcPicker.launch("*/*") },
                        onEditLyrics = { showLyricsDialog = true },
                        modifier = Modifier.alpha(immersiveAlpha)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NowPlayingSongInfo(
                            currentSong = currentSong,
                            isFavorite = isFavorite,
                            onFavoriteClick = onFavoriteClick
                        )
                        
                        NowPlayingControls(
                            currentPosition = currentPosition,
                            duration = currentSong.duration,
                            playbackState = playbackState,
                            playbackMode = playbackMode,
                            effectivePrimaryColor = effectivePrimaryColor,
                            onSeek = onSeek,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipNextClick = onSkipNextClick,
                            onSkipPreviousClick = onSkipPreviousClick,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                            modifier = Modifier.alpha(immersiveAlpha)
                        )
                        
                        NowPlayingBottomOptions(
                            showSyncedLyrics = showSyncedLyrics,
                            isSleepTimerRunning = isSleepTimerRunning,
                            effectivePrimaryColor = effectivePrimaryColor,
                            onToggleLyrics = { showSyncedLyrics = !showSyncedLyrics },
                            onShowSleepTimer = { showSleepTimerDialog = true },
                            onShowShare = { showShareDialog = true },
                            onShowQueue = { showQueueSheet = true },
                            onOpenHandsFree = onOpenHandsFree,
                            modifier = Modifier.alpha(immersiveAlpha)
                        )
                    }
                }
            }
        }
         else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                NowPlayingTopBar(
                    videoState = videoState,
                    canShowVideo = canShowVideo,
                    canManualToggleVideo = canManualToggleVideo,
                    onBackClick = onBackClick,
                    onToggleVideoMode = onToggleVideoMode,
                    showMenu = showMenu,
                    onShowMenu = { showMenu = it },
                    song = currentSong,
                    onGoToAlbum = onGoToAlbum,
                    onEqualizerClick = onEqualizerClick,
                    onDownloadClick = onDownloadClick,
                    onShowSongInfoDialog = { showSongInfoDialog = true },
                    onShowShareDialog = { showShareDialog = true },
                    onSearchLyrics = onSearchLyrics,
                    onImportLrc = { lrcPicker.launch("*/*") },
                    onEditLyrics = { showLyricsDialog = true },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                
                Spacer(modifier = Modifier.weight(0.15f))
                
                NowPlayingContent(
                    showSyncedLyrics = showSyncedLyrics,
                    lyrics = lyrics,
                    lyricsLoadingState = lyricsLoadingState,
                    currentPosition = currentPosition,
                    isInVideoMode = isInVideoMode,
                    videoState = videoState,
                    playerController = playerController,
                    isVideoSizeValid = isVideoSizeValid,
                    playerAlbumArtScale = playerAlbumArtScale,
                    scale = scale,
                    currentSong = currentSong,
                    isCanvaPlaying = isCanvaPlaying, // Only hide art if Canva is ACTIVE/PLAYING
                    onRetryLoadLyrics = onRetryLoadLyrics,
                    onSearchLyrics = onSearchLyrics,
                    onShowFullLyrics = { showFullLyrics = true },
                    modifier = Modifier.weight(1f).alpha(immersiveAlpha)
                )
                
                val songInfoWeight by animateFloatAsState(
                    targetValue = when {
                        !isUserInteracting && isCanvaPlaying -> 0.3f
                        else -> 1f
                    },
                    animationSpec = tween(1000),
                    label = "songInfoWeight"
                )
                
                // Bottom Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(songInfoWeight)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    
                    NowPlayingSongInfo(
                        currentSong = currentSong,
                        isFavorite = isFavorite,
                        onFavoriteClick = onFavoriteClick,
                        immersiveAlpha = 1f, // Always keep title fully visible
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .offset(y = songInfoOffset)
                    )
                    
                    // Group elements that fade out
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(immersiveAlpha)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        NowPlayingControls(
                            currentPosition = currentPosition,
                            duration = currentSong.duration,
                            playbackState = playbackState,
                            playbackMode = playbackMode,
                            effectivePrimaryColor = effectivePrimaryColor,
                            onSeek = onSeek,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipNextClick = onSkipNextClick,
                            onSkipPreviousClick = onSkipPreviousClick,
                            onShuffleClick = onShuffleClick,
                            onRepeatClick = onRepeatClick,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        NowPlayingBottomOptions(
                            showSyncedLyrics = showSyncedLyrics,
                            isSleepTimerRunning = isSleepTimerRunning,
                            effectivePrimaryColor = effectivePrimaryColor,
                            onToggleLyrics = { showSyncedLyrics = !showSyncedLyrics },
                            onShowSleepTimer = { showSleepTimerDialog = true },
                            onShowShare = { showShareDialog = true },
                            onShowQueue = { showQueueSheet = true },
                            onOpenHandsFree = onOpenHandsFree,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
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
            lyricsSource = lyricsSource,
            currentPosition = currentPosition,
            gradientColor = effectivePrimaryColor,
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
        SleepTimerSheet(
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
        QueueSheet(
            queue = queue,
            currentSong = currentSong,
            onDismissRequest = { showQueueSheet = false },
            onQueueItemClick = onQueueItemClick,
            onRemoveFromQueue = onRemoveFromQueue,
            onReorderQueue = onReorderQueue
        )
    }
}
