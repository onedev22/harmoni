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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.amurayada.music.ui.dialogs.ConfirmDownloadLocationDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.amurayada.music.utils.findMainActivity
import com.amurayada.music.data.model.Song
import com.amurayada.music.service.HeadphoneUsageMonitor
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
import com.amurayada.music.ui.viewmodel.HandsFreeViewModel
import com.amurayada.music.ui.viewmodel.SearchViewModel
import com.amurayada.music.ui.viewmodel.DownloadViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amurayada.music.ui.screens.handsfree.HandsFreeScreen
import androidx.compose.ui.zIndex
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val playlistViewModel: com.amurayada.music.ui.viewmodel.PlaylistViewModel by viewModels()
    private val handsFreeViewModel: HandsFreeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    val downloadViewModel: DownloadViewModel by viewModels()
    
    private var pendingAlbumUpdate: Triple<Long, String, String>? = null
    private var pendingAlbumArt: android.net.Uri? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        android.util.Log.d("MainActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            android.util.Log.d("MainActivity", "Permission granted! pendingAlbumUpdate=$pendingAlbumUpdate")
            pendingAlbumUpdate?.let { (albumId, title, artist) ->
                android.util.Log.d("MainActivity", "Retrying update for album $albumId with title='$title', artist='$artist'")
                lifecycleScope.launch {
                    try {
                        val result = libraryViewModel.performUpdateAlbum(albumId, title, artist, "", pendingAlbumArt)
                        android.util.Log.d("MainActivity", "Update result: $result")
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(this@MainActivity, "Álbum actualizado", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                             val exception = result.exceptionOrNull()
                             android.widget.Toast.makeText(this@MainActivity, "No se pudo actualizar: ${exception?.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error retrying update", e)
                        android.widget.Toast.makeText(this@MainActivity, "Error al reintentar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        pendingAlbumUpdate = null
                        pendingAlbumArt = null
                    }
                }
            }
        }
    }
    
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Aplicar modo inmersivo de pantalla completa
        setupFullScreen()
        
        setContent {
            val themeOverride by settingsViewModel.isDarkThemeOverride.collectAsState()
            val useDynamicColors by settingsViewModel.useDynamicColors.collectAsState()
            val isAmoledMode by settingsViewModel.isAmoledMode.collectAsState()
            val customPrimaryColor by settingsViewModel.customPrimaryColor.collectAsState()
            val customBgUri by settingsViewModel.customBackgroundImageUri.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Si themeOverride es null, usar el tema del sistema
            val isDarkTheme = themeOverride ?: isSystemInDarkTheme()

            // Observar Eventos de UI del ViewModel (Toasts, etc.)
            LaunchedEffect(playbackViewModel) {
                playbackViewModel.uiEvents.collect { event ->
                    when (event) {
                        is PlaybackViewModel.UiEvent.ShowToast -> {
                            android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            MusicTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColors,
                amoledMode = isAmoledMode,
                customPrimaryColor = customPrimaryColor,
                isCustomBackground = customBgUri != null
            ) {
                val permissionsToRequest = remember {
                    buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.READ_MEDIA_AUDIO)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        
                        // Agregar permisos de Bluetooth Connect para Android 12+ (S) para prevenir crashes de WebView
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                }
                
                val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)
                
                if (permissionsState.allPermissionsGranted) {
                    LaunchedEffect(Unit) {
                        val intent = Intent(this@MainActivity, MusicPlaybackService::class.java)

                        try {
                            startService(intent)
                        } catch (e: Exception) {
                            // Fallback por si el inicio en segundo plano falla (raro cuando la Activity es visible)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        }
                        libraryViewModel.loadLibrary()
                    }
                    
                    MusicPlayerApp(
                        libraryViewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        settingsViewModel = settingsViewModel,
                        playlistViewModel = playlistViewModel,
                        handsFreeViewModel = handsFreeViewModel,
                        searchViewModel = searchViewModel,
                        downloadViewModel = downloadViewModel,
                        onRequestPermission = { albumId, title, artist, imageUri, intentSender ->
                            android.util.Log.d("MainActivity", "onRequestPermission: saving pending update for album $albumId")
                            pendingAlbumUpdate = Triple(albumId, title, artist)
                            pendingAlbumArt = imageUri
                            android.util.Log.d("MainActivity", "Launching IntentSender for permission request")
                            startIntentSenderForResult(intentSender, 1001, null, 0, 0, 0, null)
                        }
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
        // Habilitar Edge-to-Edge (Bordes Inmersivos)
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
        // No-op: Edge-to-edge es persistente
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit,
    val isSelected: Boolean = false
)

@Composable
fun MusicPlayerApp(
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    playlistViewModel: com.amurayada.music.ui.viewmodel.PlaylistViewModel,
    handsFreeViewModel: HandsFreeViewModel,
    searchViewModel: SearchViewModel,
    downloadViewModel: DownloadViewModel,
    onRequestPermission: (Long, String, String, android.net.Uri?, android.content.IntentSender) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    // Tracker de Navegación para Depuración
    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            android.util.Log.d("Navigation", "Cambio de Destino: ${destination.route}")
        }
    }
    
    // Lógica de Ubicación de Descargas
    val showDownloadLocationDialog by downloadViewModel.showDownloadLocationDialog.collectAsState()
    
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            downloadViewModel.setDownloadLocation("custom", uri.toString())
        }
    }
    
    if (showDownloadLocationDialog) {
        ConfirmDownloadLocationDialog(
            onDismissRequest = { downloadViewModel.dismissDialog() },
            onUseDefaultClick = {
                downloadViewModel.setDownloadLocation("default", null)
            },
            onSelectCustomClick = {
                directoryPicker.launch(null)
            }
        )
    }
    
    // Estados del Diálogo de Monitor de Auriculares (Salud Auditiva)
    var showWarning60 by remember { mutableStateOf(false) }
    var showWarning75 by remember { mutableStateOf(false) }
    var showWarning90 by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                when (intent.action) {
                    HeadphoneUsageMonitor.ACTION_SHOW_WARNING_60 -> showWarning60 = true
                    HeadphoneUsageMonitor.ACTION_SHOW_WARNING_75 -> showWarning75 = true
                    HeadphoneUsageMonitor.ACTION_SHOW_WARNING_90 -> showWarning90 = true
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(HeadphoneUsageMonitor.ACTION_SHOW_WARNING_60)
            addAction(HeadphoneUsageMonitor.ACTION_SHOW_WARNING_75)
            addAction(HeadphoneUsageMonitor.ACTION_SHOW_WARNING_90)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Dialog 1: 60 Minutes
    if (showWarning60) {
        AlertDialog(
            onDismissRequest = { showWarning60 = false },
            title = { Text("⏰ Hora de descansar los oídos") },
            text = { Text("Llevas 1 hora escuchando con auriculares.\n¿Te tomas un descanso de 5-10 minutos?") },
            confirmButton = {
                TextButton(onClick = { 
                    showWarning60 = false
                    if (playbackViewModel.playbackState is com.amurayada.music.data.model.PlaybackState.Playing) {
                        playbackViewModel.togglePlayPause()
                    }
                }) {
                    Text("✅ Sí, descansar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning60 = false }) {
                    Text("❌ No, seguir")
                }
            }
        )
    }

    // Dialog 2: 75 Minutes
    if (showWarning75) {
        var showPostpone by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(30000) // 30 seconds delay
            showPostpone = true
        }
        
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss without action */ },
            title = { Text("⚠️ Descanso recomendado (en serio)") },
            text = { Text("Ya son 1 hora y 15 minutos continuos.\nTu salud auditiva necesita un respiro.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { 
                        showWarning75 = false
                        if (playbackViewModel.playbackState is com.amurayada.music.data.model.PlaybackState.Playing) {
                            playbackViewModel.togglePlayPause()
                        }
                    }) {
                        Text("🛑 Pausar y descansar")
                    }
                    if (showPostpone) {
                        TextButton(onClick = { showWarning75 = false }) {
                            Text("😮‍💨 Posponer 5 min")
                        }
                    }
                }
            }
        )
    }

    // Diálogo 3: 90 Minutos (Límite Crítico)
    if (showWarning90) {
        var timeLeft by remember { mutableStateOf(10) }
        var dailyCancels by remember { mutableStateOf(0) }
        
        // Cargar cancelaciones diarias
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("headphone_usage_prefs", android.content.Context.MODE_PRIVATE)
            // Lógica simple: leemos valor actual. La lógica de reseteo está en el Service, y confiamos en el valor almacenado.
            dailyCancels = prefs.getInt("daily_cancels", 0)
        }

        LaunchedEffect(timeLeft) {
            if (timeLeft > 0) {
                kotlinx.coroutines.delay(1000)
                timeLeft--
            } else {
                // Time's up -> Pause and close
                if (playbackViewModel.playbackState is com.amurayada.music.data.model.PlaybackState.Playing) {
                    playbackViewModel.togglePlayPause()
                }
                showWarning90 = false
            }
        }

        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text("🚫 Límite de uso superado") },
            text = { Text("¡90 minutos con auriculares!\nPor tu bien, la música se pausará en $timeLeft segundos.") },
            confirmButton = {
                if (dailyCancels < HeadphoneUsageMonitor.MAX_DAILY_CANCELS) {
                    TextButton(onClick = {
                        // Increment cancel count
                        val prefs = context.getSharedPreferences("headphone_usage_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putInt("daily_cancels", dailyCancels + 1).apply()
                        showWarning90 = false
                    }) {
                        Text("🆘 Cancelar pausa (${dailyCancels}/${HeadphoneUsageMonitor.MAX_DAILY_CANCELS})")
                    }
                } else {
                    TextButton(onClick = {}, enabled = false) {
                        Text("🆘 Sin cancelaciones hoy")
                    }
                }
            }
        )
    }
    
    val bottomNavItems = remember(navBackStackEntry) {
        val currentArgs = navBackStackEntry?.arguments
        val fromSource = currentArgs?.getString("from")
        val currentRoute = navBackStackEntry?.destination?.route
        
        listOf(
            BottomNavItem(
                route = Screen.Home.route,
                title = "Inicio",
                selectedIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                isSelected = currentRoute == Screen.Home.route || (fromSource == Screen.Home.route)
            ),
            BottomNavItem(
                route = Screen.Library.route,
                title = "Biblioteca",
                selectedIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.LibraryMusic, contentDescription = null) },
                isSelected = currentRoute == Screen.Library.route || (fromSource == Screen.Library.route)
            ),
            BottomNavItem(
                route = Screen.Search.route,
                title = "Buscar",
                selectedIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                isSelected = currentRoute == Screen.Search.route || (fromSource == Screen.Search.route)
            ),
            BottomNavItem(
                route = Screen.Playlists.route,
                title = "Playlists",
                selectedIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.QueueMusic, contentDescription = null) },
                isSelected = currentRoute == Screen.Playlists.route || (fromSource == Screen.Playlists.route)
            ),
            BottomNavItem(
                route = Screen.Favorites.route,
                title = "Favoritos",
                selectedIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                isSelected = currentRoute == Screen.Favorites.route || (fromSource == Screen.Favorites.route)
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
    
    // Ocultar barra de navegación en pantalla de Reproducción (aunque ahora sea overlay, es útil para otras pantallas)
    val showBottomNav = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Favorites.route,
        Screen.Playlists.route,
        Screen.Search.route,
        Screen.AlbumDetail.route,
        Screen.ArtistDetail.route,
        Screen.PlaylistDetail.route,
        Screen.AlbumDetailOnline.route,
        Screen.ArtistDetailOnline.route
    )

    // Estado del Overlay "Now Playing"
    val isAmoledMode by settingsViewModel.isAmoledMode.collectAsState()
    val customPrimaryColor by settingsViewModel.customPrimaryColor.collectAsState()
    val customBgUri by settingsViewModel.customBackgroundImageUri.collectAsState()
    val playerBgType by settingsViewModel.playerBackgroundType.collectAsState()
    val playerBgColor by settingsViewModel.playerBackgroundColor.collectAsState()
    val playerArtScale by settingsViewModel.playerAlbumArtScale.collectAsState()
    val applyBackgroundToPlayer by settingsViewModel.applyBackgroundToPlayer.collectAsState()

    
    var isPlayerExpanded by remember { mutableStateOf(false) }
    
    // Manejar botón "Atrás" cuando el reproductor está expandido
    androidx.activity.compose.BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }
    
    // Expandir reproductor al hacer click en mini-player o recibir evento
    LaunchedEffect(Unit) {
        playbackViewModel.expandPlayerEvent.collect {
            isPlayerExpanded = true
        }
    }
    
    // Estado Modo Manos Libres
    val isHandsFreeMode by handsFreeViewModel.isHandsFreeMode.collectAsState()
    
    // Mantener Pantalla Encendida
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(isHandsFreeMode) {
        if (isHandsFreeMode) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (customBgUri != null) {
            AsyncImage(
                 model = customBgUri,
                 contentDescription = null,
                 contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                 modifier = Modifier
                    .fillMaxSize()
                    .blur(10.dp) // Reducido de 15dp para Iteración 6 (Nítidez extrema)
            )
            // Capa de Vidrio Esmerilado: Sin tinte blanco para máxima claridad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Transparent)
            )
            // Overlay de Protección para contraste
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (customBgUri != null && !isPlayerExpanded) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
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
                                onExpandClick = { isPlayerExpanded = true },
                                customBgUri = customBgUri?.toString(),
                                isAmoledMode = isAmoledMode
                            )
                        }
                        
                        Box {

                            
                            NavigationBar(
                                containerColor = when {
                                    customBgUri != null -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                                    isAmoledMode -> androidx.compose.ui.graphics.Color.Black
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                tonalElevation = 0.dp,
                                modifier = Modifier.then(
                                    if (customBgUri != null) Modifier.drawWithCache {
                                        onDrawWithContent {
                                            drawContent()
                                            drawLine(
                                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    } else Modifier
                                )
                            ) {
                                bottomNavItems.forEach { item ->
                                    val selected = item.isSelected
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            val currentRouteTemplate = navController.currentBackStackEntry?.destination?.route
                                            
                                            android.util.Log.d("NavigationDebug", "--------------------------------------")
                                            android.util.Log.d("NavigationDebug", "Clicked: ${item.title} (target: ${item.route})")
                                            android.util.Log.d("NavigationDebug", "Current Route: $currentRouteTemplate")
                                            android.util.Log.d("NavigationDebug", "Is Highlighted (selected): $selected")
                                            
                                            if (selected) {
                                                android.util.Log.d("NavigationDebug", "Action: TAP-TO-RESET/POP")
                                                if (currentRouteTemplate != item.route) {
                                                    android.util.Log.d("NavigationDebug", "Popping back to root: ${item.route}")
                                                    navController.popBackStack(item.route, inclusive = false)
                                                } else if (item.route == Screen.Search.route) {
                                                    android.util.Log.d("NavigationDebug", "Clearing search results at root")
                                                    searchViewModel.clearSearch()
                                                }
                                            } else {
                                                android.util.Log.d("NavigationDebug", "Action: CROSS-TAB NAVIGATE to ${item.route}")
                                                
                                                // Manejo específico para Inicio (destino inicial)
                                                if (item.route == Screen.Home.route) {
                                                    navController.navigate(item.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                            inclusive = false
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                } else {
                                                    navController.navigate(item.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        },
                                        icon = { if (selected) item.selectedIcon() else item.unselectedIcon() },
                                        label = { Text(item.title, style = MaterialTheme.typography.labelSmall) },
                                        colors = if (customBgUri != null) {
                                            NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                                selectedTextColor = androidx.compose.ui.graphics.Color.White,
                                                unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                                            )
                                        } else {
                                            NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        }
                                    )
                                }
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
                    .padding(top = paddingValues.calculateTopPadding()),
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
                    val isOnlineMode by settingsViewModel.onlineMode.collectAsState()
                    val onlineHomeSections = searchViewModel.onlineHomeSections
                    
                    LaunchedEffect(isOnlineMode) {
                        if (isOnlineMode) {
                            searchViewModel.loadOnlineHome()
                        }
                    }

                    HomeScreen(
                        songs = songs,
                        albums = albums,
                        recentlyPlayed = playbackViewModel.recentlyPlayed,
                        recentlyAddedSongs = libraryViewModel.recentlyAddedSongs,
                        mostPlayed = playbackViewModel.mostPlayed,
                        timeCapsuleSongs = libraryViewModel.timeCapsuleSongs,
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        },
                        onAlbumClick = { album ->
                            if (album.path.contains("music.youtube.com") || album.path.startsWith("MPRE") || album.path.startsWith("OLAK")) {
                                navController.navigate(Screen.AlbumDetailOnline.createRoute(album.path, from = Screen.Home.route))
                            } else {
                                navController.navigate(Screen.AlbumDetail.createRoute(album.id, from = Screen.Home.route))
                            }
                        },
                        onArtistClick = { artist ->
                            if (artist.path.contains("music.youtube.com") || artist.path.startsWith("UC")) {
                                navController.navigate(Screen.ArtistDetailOnline.createRoute(artist.path, from = Screen.Home.route))
                            } else {
                                navController.navigate(Screen.ArtistDetail.createRoute(artist.id, from = Screen.Home.route))
                            }
                        },
                        onPlaylistClick = { playlist ->
                            if (playlist.remoteId != null) {
                                navController.navigate(Screen.AlbumDetailOnline.createRoute(playlist.remoteId, from = Screen.Home.route))
                            } else {
                                navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id, from = Screen.Home.route))
                            }
                        },
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onHistoryClick = { navController.navigate(Screen.History.route) },

                        onRecapClick = { navController.navigate(Screen.Recap.route) },
                        onTimeCapsuleClick = { navController.navigate(Screen.TimeCapsule.route) },
                        onRefreshClick = { searchViewModel.refreshHome() },
                        onLoadMore = { searchViewModel.loadMoreHome() },
                        isOnlineMode = isOnlineMode,
                        isHomeExhausted = searchViewModel.isHomeExhausted,
                        onlineSections = onlineHomeSections
                    )
                }
                
                composable(Screen.Library.route) {
                    val selectedTab = com.amurayada.music.ui.screens.library.LibraryTab.entries.getOrElse(libraryViewModel.selectedLibraryTab) { com.amurayada.music.ui.screens.library.LibraryTab.SONGS }
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    val downloadedSongs by (context as MainActivity).downloadViewModel.downloadedSongs.collectAsState()
                    
                    LibraryScreen(
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        genres = emptyList(),
                        downloadedSongs = downloadedSongs,
                        selectedTab = selectedTab,
                        onTabSelected = { tab -> libraryViewModel.selectedLibraryTab = tab.ordinal },
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        },
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id, from = Screen.Library.route))
                        },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist.id, from = Screen.Library.route))
                        },
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        currentSong = currentSong,
                        isPlaying = playbackState is com.amurayada.music.data.model.PlaybackState.Playing,
                        playlists = playlistViewModel.playlists.collectAsState().value,
                        onAddToPlaylist = { playlist, song ->
                            playlistViewModel.addSongToPlaylist(playlist.id, song.id)
                        },
                        onCreatePlaylist = {
                            navController.navigate(Screen.Playlists.route)
                        },
                        onDeleteSong = { song ->
                            scope.launch {
                                try {
                                    libraryViewModel.performDeleteSong(song.id)
                                    android.widget.Toast.makeText(context, "Canción eliminada", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: android.app.RecoverableSecurityException) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        try {
                                            val intentSender = e.userAction.actionIntent.intentSender
                                            (context as android.app.Activity).startIntentSenderForResult(
                                                intentSender, 
                                                1003,
                                                null, 
                                                0, 
                                                0, 
                                                0, 
                                                null
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error al eliminar", android.widget.Toast.LENGTH_SHORT).show()
                                    e.printStackTrace()
                                }
                            }
                        },
                        onDeleteDownload = { song ->
                            scope.launch {
                                (context as MainActivity).downloadViewModel.deleteDownload(song.id)
                                android.widget.Toast.makeText(context, "Descarga eliminada", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },

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
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.Playlists.route) {
                    val playlists by playlistViewModel.playlists.collectAsState()
                    com.amurayada.music.ui.screens.playlist.PlaylistScreen(
                        playlists = playlists,
                        userPlaylists = libraryViewModel.userPlaylists, // Pass Online Playlists
                        onCreatePlaylist = playlistViewModel::createPlaylist,
                        onOnlinePlaylistClick = { playlist ->
                            if (playlist.remoteId != null) {
                                navController.navigate(Screen.AlbumDetailOnline.createRoute(playlist.remoteId, from = Screen.Playlists.route))
                            }
                        },
                        onPlaylistClick = { playlist ->
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id, from = Screen.Playlists.route))
                        },
                        onDeletePlaylist = playlistViewModel::deletePlaylist,
                        onRenamePlaylist = playlistViewModel::renamePlaylist
                    )
                }

                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("playlistId") {
                            type = androidx.navigation.NavType.LongType
                        },
                        androidx.navigation.navArgument("from") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    val playlists by playlistViewModel.playlists.collectAsState()
                    val playlist = playlists.find { it.id == playlistId }
                    val playlistSongs = playlist?.songIds?.mapNotNull { songId ->
                        songs.find { it.id == songId }
                    } ?: emptyList()

                    com.amurayada.music.ui.screens.playlist.PlaylistDetailScreen(
                        playlist = playlist,
                        songs = playlistSongs,
                        allSongs = songs,
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { song ->
                            playbackViewModel.playSong(song, playlistSongs)
                        },
                        onRemoveSong = { songId ->
                            playlistViewModel.removeSongFromPlaylist(playlistId, songId)
                        },
                        onAddSong = { song ->
                            playlistViewModel.addSongToPlaylist(playlistId, song.id)
                        },
                        onPlayAll = {
                            if (playlistSongs.isNotEmpty()) {
                                playbackViewModel.playSong(playlistSongs.first(), playlistSongs)
                            }
                        },
                        onShuffle = {
                            if (playlistSongs.isNotEmpty()) {
                                val shuffled = playlistSongs.shuffled()
                                playbackViewModel.playSong(shuffled.first(), shuffled)
                            }
                        }
                    )
                }
                
                composable(Screen.Search.route) {
                    val searchResults = searchViewModel.searchResults
                    val searchQuery = searchViewModel.searchQuery
                    
                    SearchScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query -> 
                            searchViewModel.onSearchQueryChange(query)
                            // Escritura: Debounce (Retraso intencional para evitar spam de API)
                            if (query.length > 2) {
                                searchViewModel.search(query, debounce = true)
                            }
                        },
                        onSearch = { query ->
                            searchViewModel.onSearchQueryChange(query)
                            searchViewModel.clearSuggestions()
                            // Tecla Enter: Búsqueda inmediata
                            if (query.length > 2) {
                                searchViewModel.search(query, debounce = false)
                            }
                        },
                        songs = searchResults,
                        albums = searchViewModel.albumResults,
                        artists = searchViewModel.artistResults,
                        playlists = searchViewModel.playlistResults,
                        isLoading = searchViewModel.isLoading,
                        onDownloadClick = { song ->
                            (context as MainActivity).downloadViewModel.downloadSong(song)
                        },
                        historyItems = libraryViewModel.searchHistory,
                        onHistoryItemClick = { term -> 
                            searchViewModel.onSearchQueryChange(term)
                            // Click en historial: Búsqueda inmediata
                            searchViewModel.search(term, debounce = false)
                        },
                        onRemoveHistoryItem = libraryViewModel::removeFromSearchHistory,
                        onClearHistory = libraryViewModel::clearSearchHistory,
                        onSongClick = { song, _ ->
                            libraryViewModel.addToSearchHistory(searchQuery)
                            // Reproducir canción seleccionada y usar resultados como cola
                            playbackViewModel.playSong(song, searchResults)
                        },
                        onAlbumClick = { album ->
                            libraryViewModel.addToSearchHistory(searchQuery)
                            if (album.path.isNotEmpty()) {
                                navController.navigate(Screen.AlbumDetailOnline.createRoute(album.path, from = "search"))
                            } else {
                                navController.navigate(Screen.AlbumDetail.createRoute(album.id, from = "search"))
                            }
                        },
                        onArtistClick = { artist ->
                            libraryViewModel.addToSearchHistory(searchQuery)
                            if (artist.path.isNotEmpty()) {
                                navController.navigate(Screen.ArtistDetailOnline.createRoute(artist.path, from = "search"))
                            }
                        },
                        onPlaylistClick = { playlist ->
                            libraryViewModel.addToSearchHistory(searchQuery)
                            if (!playlist.remoteId.isNullOrEmpty()) {
                                navController.navigate(Screen.AlbumDetailOnline.createRoute("https://music.youtube.com/playlist?list=${playlist.remoteId}", from = "search"))
                            }
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = Screen.ArtistDetailOnline.route,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    val fromSource = backStackEntry.arguments?.getString("from")
                    
                    LaunchedEffect(url) {
                        if (url.isNotEmpty()) {
                            searchViewModel.getArtistDetails(url)
                        }
                    }
                    
                    val artistDetails = searchViewModel.selectedArtistDetails
                    
                    if (artistDetails != null) {
                        com.amurayada.music.ui.screens.artist.ArtistDetailScreen(
                            artist = artistDetails.artist,
                            topSongs = artistDetails.topSongs,
                            albums = artistDetails.albums,
                            singles = artistDetails.singles,
                            onBackClick = { 
                                navController.popBackStack()
                                searchViewModel.clearSelectedArtist()
                            },
                            onSongClick = { song, list ->
                                playbackViewModel.playSong(song, list)
                            },
                            onAlbumClick = { album ->
                                navController.navigate(Screen.AlbumDetailOnline.createRoute(album.path, from = fromSource))
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                composable(
                    route = Screen.AlbumDetailOnline.route,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    
                    LaunchedEffect(url) {
                        if (url.isNotEmpty()) {
                            searchViewModel.getAlbumDetails(url)
                        }
                    }
                    
                    val songs = searchViewModel.selectedAlbumSongs
                    
                    val album = remember(url) {
                        com.amurayada.music.data.model.Album(
                            id = url.hashCode().toLong(),
                            name = "Loading...",
                            artist = "",
                            artworkUri = null,
                            path = url
                        )
                    }
                    
                    val displayAlbum = if (songs.isNotEmpty()) {
                        album.copy(
                            name = songs.first().album,
                            artist = songs.first().artist,
                            artworkUri = songs.first().albumArtUri,
                            songCount = songs.size
                        )
                    } else album
                    
                    val context = androidx.compose.ui.platform.LocalContext.current

                    com.amurayada.music.ui.screens.album.AlbumDetailScreen(
                        album = displayAlbum,
                        songs = songs,
                        allAlbums = emptyList(),
                        onSongClick = { song -> playbackViewModel.playSong(song, songs) },
                        onBackClick = { 
                            navController.popBackStack()
                            searchViewModel.clearSelectedAlbum()
                        },
                        onPlayAll = { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) },
                        onShuffle = { 
                            if (songs.isNotEmpty()) {
                                val shuffled = songs.shuffled()
                                playbackViewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        onAlbumClick = { /* No-op */ },
                        onDownloadSong = { song -> (context as MainActivity).downloadViewModel.downloadSong(song) },
                        onDownloadAlbum = { albumSongs -> (context as MainActivity).downloadViewModel.downloadAlbum(albumSongs) }
                    )
                }
                
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onBackClick = { navController.popBackStack() },
                        onRescanLibrary = { libraryViewModel.loadLibrary() },
                        onLoginClick = { navController.navigate(Screen.YouTubeLogin.route) },
                        onPersonalizationClick = { navController.navigate(Screen.Personalization.route) },

                        onChangeDownloadLocation = { downloadViewModel.requestChangeLocation() }
                    )
                }
                

                
                composable(Screen.YouTubeLogin.route) {
                    val context = LocalContext.current
                    val authManager = remember { com.amurayada.music.data.auth.YouTubeAuthManager.getInstance(context) }
                    
                    com.amurayada.music.ui.screens.login.LoginScreen(
                        onLoginSuccess = {
                            navController.popBackStack()
                            navController.popBackStack()
                            // Forzar actualización de inicio para obtener contenido personalizado
                            searchViewModel.refreshHome()
                        },
                        onLoginFailed = { message ->
                            navController.popBackStack()
                        },
                        onNavigateBack = { navController.popBackStack() },
                        authManager = authManager
                    )
                }
                
                composable(Screen.Personalization.route) {
                    com.amurayada.music.ui.screens.settings.PersonalizationScreen(
                        settingsViewModel = settingsViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Equalizer.route) {
                    val equalizerViewModel: com.amurayada.music.ui.viewmodel.EqualizerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.amurayada.music.ui.screens.equalizer.EqualizerScreen(
                        viewModel = equalizerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }


                
                // Detalle de Álbum - muestra canciones de un álbum local
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("albumId") { 
                            type = androidx.navigation.NavType.LongType 
                        },
                        androidx.navigation.navArgument("from") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val album = albums.find { it.id == albumId }
                    val albumSongs = songs.filter { it.album == album?.name }
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    
                    AlbumDetailScreen(
                        album = album,
                        songs = albumSongs,
                        allAlbums = albums,
                        libraryVersion = libraryViewModel.libraryVersion,
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
                        },
                        onUpdateAlbum = { title, artist, genre, imageUri ->
                            scope.launch {
                                try {
                                    val newId = libraryViewModel.performUpdateAlbum(albumId, title, artist, genre, imageUri)
                                    if (newId != null) {
                                        android.widget.Toast.makeText(context, "Álbum actualizado", android.widget.Toast.LENGTH_SHORT).show()
                                        navController.popBackStack() // Volver a la biblioteca para refrescar
                                    } else {
                                        android.widget.Toast.makeText(context, "No se pudo actualizar", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: com.amurayada.music.data.repository.MediaRepository.RequiresPermissionException) {
                                    onRequestPermission(albumId, title, artist, imageUri, e.intentSender)
                                } catch (e: android.app.RecoverableSecurityException) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        try {
                                            onRequestPermission(albumId, title, artist, imageUri, e.userAction.actionIntent.intentSender)
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                } catch (e: Exception) {
                                    val msg = e.message ?: "Error al actualizar"
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    e.printStackTrace()
                                }
                            }
                        },
                        onDeleteSong = { song ->
                            scope.launch {
                                try {
                                    libraryViewModel.performDeleteSong(song.id)
                                    android.widget.Toast.makeText(context, "Canción eliminada", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: android.app.RecoverableSecurityException) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        try {
                                            val intentSender = e.userAction.actionIntent.intentSender
                                            (context as android.app.Activity).startIntentSenderForResult(
                                                intentSender, 
                                                1002, 
                                                null, 
                                                0, 
                                                0, 
                                                0, 
                                                null
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error al eliminar", android.widget.Toast.LENGTH_SHORT).show()
                                    e.printStackTrace()
                                }
                            }
                        },
                        onDownloadSong = { song -> (context as MainActivity).downloadViewModel.downloadSong(song) },
                        onDownloadAlbum = { albumSongs -> (context as MainActivity).downloadViewModel.downloadAlbum(albumSongs) }
                    )
                }
                
                // Detalle de Artista - muestra canciones de un artista y sus álbumes
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        androidx.navigation.navArgument("artistId") { 
                            type = androidx.navigation.NavType.LongType 
                        },
                        androidx.navigation.navArgument("from") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                    val fromSource = backStackEntry.arguments?.getString("from")
                    
                    val artist = artists.find { it.id == artistId }
                    val artistSongs = songs.filter { it.artist == artist?.name }
                    val artistAlbums = albums.filter { it.artist == artist?.name }
                    
                    if (artist != null) {
                        ArtistDetailScreen(
                            artist = artist,
                            topSongs = artistSongs,
                            albums = artistAlbums,
                            onSongClick = { song, list -> playbackViewModel.playSong(song, list) },
                            onAlbumClick = { album -> 
                                navController.navigate(Screen.AlbumDetail.createRoute(album.id, from = fromSource))
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Artist not found")
                        }
                    }
                }
                
                // Detalle de Género
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
                    
                    // Obtener canciones para este género
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
                
                composable(Screen.Recap.route) {
                    // Fusionar canciones locales con reproducidas recientemente (incluye online)
                    // Esto asegura que las canciones online aparezcan en estadísticas si están en el historial
                    val allHistorySongs = (songs + playbackViewModel.recentlyPlayed)
                        .distinctBy { it.id }

                    com.amurayada.music.ui.screens.recap.RecapScreen(
                        allSongs = allHistorySongs,
                        playCounts = playbackViewModel.playCounts,
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        }
                    )
                }
                
                composable(Screen.TimeCapsule.route) {
                     // Fusión igual que en Recap: locales + historial online
                    val allHistorySongs = (songs + playbackViewModel.recentlyPlayed)
                        .distinctBy { it.id }

                    com.amurayada.music.ui.screens.timecapsule.TimeCapsuleScreen(
                        allSongs = allHistorySongs,
                        playCounts = playbackViewModel.playCounts,
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { song, songList ->
                            playbackViewModel.playSong(song, songList)
                        }
                    )
                }
            }
        }
        
        // Overlay "Now Playing" (Reproductor)
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
                lyricsSource = playbackViewModel.lyricsSource,
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
                onEqualizerClick = { 
                    isPlayerExpanded = false
                    navController.navigate(Screen.Equalizer.route) 
                },
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
                onOpenHandsFree = { handsFreeViewModel.setManualMode(true) },
                
                // Modo Video
                videoState = playbackViewModel.videoState,
                canShowVideo = playbackViewModel.canShowVideo,
                canManualToggleVideo = playbackViewModel.canManualToggleVideo,
                isVideoSizeValid = playbackViewModel.isVideoSizeValid,
                onToggleVideoMode = playbackViewModel::toggleVideoMode,
                youtubeVideoUrl = playbackViewModel.youtubeVideoUrl,
                playerController = playbackViewModel.mediaController,
                settingsViewModel = settingsViewModel,
                
                onDownloadClick = { song -> 
                    // Solo usamos getSongForDownload si estamos clickeando la canción en reproducción
                    // (para recuperar la URL original de YouTube si fue reemplazada)
                    val currentSongId = playbackViewModel.currentSong?.id
                    val songForDownload = if (currentSongId == song.id) {
                        playbackViewModel.getSongForDownload() ?: song
                    } else {
                        song
                    }
                    context.findMainActivity()?.downloadViewModel?.downloadSong(songForDownload)
                },
                isAmoledMode = isAmoledMode,
                playerBackgroundType = playerBgType,
                playerBackgroundColor = playerBgColor,
                playerAlbumArtScale = playerArtScale,

                customPrimaryColor = customPrimaryColor,
                applyBackgroundToPlayer = applyBackgroundToPlayer,
                customBackgroundImageUri = customBgUri,
            )
        }
        
        // Overlay Modo Manos Libres
        AnimatedVisibility(
            visible = isHandsFreeMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            HandsFreeScreen(
                currentSong = currentSong,
                isPlaying = playbackState is com.amurayada.music.data.model.PlaybackState.Playing,
                currentPosition = currentPosition,
                isAmoledMode = isAmoledMode,
                onPlayPause = playbackViewModel::togglePlayPause,
                onNext = playbackViewModel::skipToNext,
                onPrevious = playbackViewModel::skipToPrevious,
                onExit = { handsFreeViewModel.setManualMode(false) }
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