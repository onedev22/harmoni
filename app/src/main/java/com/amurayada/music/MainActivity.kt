package com.amurayada.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amurayada.music.service.MusicPlaybackService
import com.amurayada.music.ui.components.MiniPlayer
import com.amurayada.music.ui.navigation.Screen
import com.amurayada.music.ui.screens.album.AlbumDetailScreen
import com.amurayada.music.ui.screens.artist.ArtistDetailScreen
import com.amurayada.music.ui.screens.favorites.FavoritesScreen
import com.amurayada.music.ui.screens.history.HistoryScreen
import com.amurayada.music.ui.screens.home.HomeScreen
import com.amurayada.music.ui.screens.library.LibraryScreen
import com.amurayada.music.ui.screens.nowplaying.NowPlayingScreen
import com.amurayada.music.ui.screens.search.SearchScreen
import com.amurayada.music.ui.screens.settings.SettingsScreen
import com.amurayada.music.ui.theme.MusicTheme
import com.amurayada.music.ui.viewmodel.LibraryViewModel
import com.amurayada.music.ui.viewmodel.PlaybackViewModel
import com.amurayada.music.ui.viewmodel.SettingsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply fullscreen mode
        setupFullScreen()
        
        setContent {
            val themeOverride by settingsViewModel.isDarkThemeOverride.collectAsState()
            val useDynamicColors by settingsViewModel.useDynamicColors.collectAsState()
            val isAmoledMode by settingsViewModel.isAmoledMode.collectAsState()
            
            // If themeOverride is null, use system theme
            val isDarkTheme = themeOverride ?: isSystemInDarkTheme()
            
            MusicTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColors,
                amoledMode = isAmoledMode
            ) {
                val permissionsToRequest = remember {
                    buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.READ_MEDIA_AUDIO)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }
                
                val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)
                
                if (permissionsState.allPermissionsGranted) {
                    LaunchedEffect(Unit) {
                        val intent = Intent(this@MainActivity, MusicPlaybackService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        libraryViewModel.loadLibrary()
                    }
                    
                    MusicPlayerApp(
                        libraryViewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        settingsViewModel = settingsViewModel
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }
            }
        }
    }
    
    private fun setupFullScreen() {
        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    
    override fun onResume() {
        super.onResume()
        restoreFullScreenUI()
    }
    
    private fun restoreFullScreenUI() {
        // No-op: Edge-to-edge is persistent
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit
)

@Composable
fun MusicPlayerApp(
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    
    val bottomNavItems = remember {
        listOf(
            BottomNavItem(
                route = Screen.Home.route,
                title = "Inicio",
                selectedIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.Home, contentDescription = null) }
            ),
            BottomNavItem(
                route = Screen.Library.route,
                title = "Biblioteca",
                selectedIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.LibraryMusic, contentDescription = null) }
            ),
            BottomNavItem(
                route = Screen.Favorites.route,
                title = "Favoritos",
                selectedIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) }
            ),
            BottomNavItem(
                route = Screen.History.route,
                title = "Historial",
                selectedIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.History, contentDescription = null) }
            )
        )
    }
    
    // States
    val songs = libraryViewModel.songs
    val albums = libraryViewModel.albums
    val artists = libraryViewModel.artists
    val searchQuery = libraryViewModel.searchQuery
    val filteredSongs = libraryViewModel.filteredSongs
    val filteredAlbums = libraryViewModel.filteredAlbums
    val filteredArtists = libraryViewModel.filteredArtists
    
    val currentSong = playbackViewModel.currentSong
    val playbackState = playbackViewModel.playbackState
    val currentPosition = playbackViewModel.currentPosition
    val playbackMode = playbackViewModel.playbackMode
    
    // Hide bottom nav on NowPlaying screen (not needed anymore as it's an overlay, but good for other screens if any)
    val showBottomNav = currentDestination?.route in listOf(
        Screen.Home.route, Screen.Library.route, Screen.Favorites.route, Screen.History.route
    )

    // Now Playing Overlay State
    val isAmoledMode by settingsViewModel.isAmoledMode.collectAsState()
    var isPlayerExpanded by remember { mutableStateOf(false) }
    
    // Handle Back Press when player is expanded
    androidx.activity.compose.BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }
    
    // Expand player when mini player is clicked or event received
    LaunchedEffect(Unit) {
        playbackViewModel.expandPlayerEvent.collect {
            isPlayerExpanded = true
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomNav) {
                    Column {
                        AnimatedVisibility(
                            visible = currentSong != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            MiniPlayer(
                                currentSong = currentSong,
                                playbackState = playbackState,
                                currentPosition = currentPosition,
                                duration = currentSong?.duration ?: 0L,
                                onPlayPauseClick = playbackViewModel::togglePlayPause,
                                onNextClick = playbackViewModel::skipToNext,
                                onExpandClick = { isPlayerExpanded = true }
                            )
                        }
                        
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { if (selected) item.selectedIcon() else item.unselectedIcon() },
                                    label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(200)
                    ) + fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(200)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        songs = songs,
                        albums = albums,
                        recentlyPlayed = playbackViewModel.recentlyPlayed,
                        recentlyAddedSongs = libraryViewModel.recentlyAddedSongs,
                        mostPlayed = playbackViewModel.mostPlayed,
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        },
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) }
                    )
                }
                
                composable(Screen.Library.route) {
                    val selectedTab = com.amurayada.music.ui.screens.library.LibraryTab.values().getOrElse(libraryViewModel.selectedLibraryTab) { com.amurayada.music.ui.screens.library.LibraryTab.SONGS }
                    
                    LibraryScreen(
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        genres = libraryViewModel.filteredGenres,
                        selectedTab = selectedTab,
                        onTabSelected = { tab -> libraryViewModel.selectedLibraryTab = tab.ordinal },
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        },
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist.id))
                        },
                        onGenreClick = { genre ->
                            navController.navigate(Screen.GenreDetail.createRoute(genre.id))
                        },
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        currentSong = currentSong,
                        isPlaying = playbackState is com.amurayada.music.data.model.PlaybackState.Playing
                    )
                }
                
                composable(Screen.Favorites.route) {
                    FavoritesScreen(
                        favoriteSongs = playbackViewModel.favorites,
                        onSongClick = { song ->
                            playbackViewModel.playSong(song, playbackViewModel.favorites)
                        },
                        onRemoveFavorite = { song ->
                            playbackViewModel.toggleFavorite(song)
                        }
                    )
                }
                
                composable(Screen.History.route) {
                    HistoryScreen(
                        historyItems = playbackViewModel.recentlyPlayed,
                        onSongClick = { song ->
                            playbackViewModel.playSong(song, playbackViewModel.recentlyPlayed)
                        },
                        onClearHistory = {
                            playbackViewModel.clearHistory()
                        }
                    )
                }
                
                composable(Screen.Search.route) {
                    SearchScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = libraryViewModel::updateSearchQuery,
                        songs = filteredSongs,
                        albums = filteredAlbums,
                        artists = filteredArtists,
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        },
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onBackClick = { navController.popBackStack() },
                        onRescanLibrary = { libraryViewModel.loadLibrary() }
                    )
                }
                
                // Album Detail - shows songs from an album
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("albumId") { 
                            type = androidx.navigation.NavType.LongType 
                        }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val album = albums.find { it.id == albumId }
                    val albumSongs = songs.filter { it.album == album?.name }
                    
                    AlbumDetailScreen(
                        album = album,
                        songs = albumSongs,
                        allAlbums = albums,
                        onSongClick = { song -> playbackViewModel.playSong(song, albumSongs) },
                        onBackClick = { navController.popBackStack() },
                        onPlayAll = {
                            if (albumSongs.isNotEmpty()) {
                                playbackViewModel.playSong(albumSongs.first(), albumSongs)
                            }
                        },
                        onShuffle = {
                            if (albumSongs.isNotEmpty()) {
                                val shuffled = albumSongs.shuffled()
                                playbackViewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        onAlbumClick = { selectedAlbum ->
                            navController.navigate("album/${selectedAlbum.id}")
                        }
                    )
                }
                
                // Artist Detail - shows songs from an artist
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("artistId") { 
                            type = androidx.navigation.NavType.LongType 
                        }
                    )
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                    val artist = artists.find { it.id == artistId }
                    val artistSongs = songs.filter { it.artist == artist?.name }
                    val artistAlbums = albums.filter { it.artist == artist?.name }
                    
                    ArtistDetailScreen(
                        artist = artist,
                        songs = artistSongs,
                        albums = artistAlbums,
                        onSongClick = { song -> playbackViewModel.playSong(song, artistSongs) },
                        onAlbumClick = { album -> 
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id))
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                // Genre Detail
                composable(
                    route = Screen.GenreDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("genreId") { 
                            type = androidx.navigation.NavType.LongType 
                        }
                    )
                ) { backStackEntry ->
                    val genreId = backStackEntry.arguments?.getLong("genreId") ?: 0L
                    val genre = libraryViewModel.genres.find { it.id == genreId }
                    
                    // Fetch songs for this genre
                    var genreSongs by remember { mutableStateOf<List<com.amurayada.music.data.model.Song>>(emptyList()) }
                    
                    LaunchedEffect(genreId) {
                        genreSongs = libraryViewModel.getSongsByGenre(genreId)
                    }
                    
                    com.amurayada.music.ui.screens.genre.GenreDetailScreen(
                        genre = genre,
                        songs = genreSongs,
                        onSongClick = { song -> playbackViewModel.playSong(song, genreSongs) },
                        onBackClick = { navController.popBackStack() },
                        onPlayAll = {
                            if (genreSongs.isNotEmpty()) {
                                playbackViewModel.playSong(genreSongs.first(), genreSongs)
                            }
                        },
                        onShuffle = {
                            if (genreSongs.isNotEmpty()) {
                                val shuffled = genreSongs.shuffled()
                                playbackViewModel.playSong(shuffled.first(), shuffled)
                            }
                        }
                    )
                }
            }
        }
        
        // Now Playing Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            NowPlayingScreen(
                currentSong = currentSong,
                playbackState = playbackState,
                currentPosition = currentPosition,
                playbackMode = playbackMode,
                isFavorite = currentSong?.let { playbackViewModel.isFavorite(it) } ?: false,
                queue = playbackViewModel.queue,
                lyrics = playbackViewModel.lyrics,
                lyricsLoadingState = playbackViewModel.lyricsLoadingState,
                sleepTimerDuration = playbackViewModel.sleepTimerDuration,
                isSleepTimerRunning = playbackViewModel.isSleepTimerRunning,
                onPlayPauseClick = playbackViewModel::togglePlayPause,
                onSkipNextClick = playbackViewModel::skipToNext,
                onSkipPreviousClick = playbackViewModel::skipToPrevious,
                onSeek = playbackViewModel::seekTo,
                onShuffleClick = playbackViewModel::toggleShuffle,
                onRepeatClick = playbackViewModel::toggleRepeatMode,
                onFavoriteClick = { currentSong?.let { playbackViewModel.toggleFavorite(it) } },
                onBackClick = { isPlayerExpanded = false },
                onQueueItemClick = { song -> playbackViewModel.playSong(song, playbackViewModel.queue) },
                onSaveLyrics = { content -> currentSong?.let { playbackViewModel.saveLyrics(it.id, content) } },
                onRetryLoadLyrics = playbackViewModel::retryLoadLyrics,
                onSearchLyrics = { currentSong?.let { playbackViewModel.openLyricsSearch(context, it) } },
                onImportLrc = { content -> currentSong?.let { playbackViewModel.importLrcFile(it.id, content) } },
                onStartSleepTimer = playbackViewModel::startSleepTimer,
                onCancelSleepTimer = playbackViewModel::cancelSleepTimer,
                onGoToAlbum = { albumId ->
                    isPlayerExpanded = false
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onRemoveFromQueue = playbackViewModel::removeFromQueue,
                onReorderQueue = playbackViewModel::reorderQueue,
                isAmoledMode = isAmoledMode
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Permisos necesarios",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Para reproducir tu música, necesitamos acceso a los archivos de audio de tu dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Conceder permisos",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}