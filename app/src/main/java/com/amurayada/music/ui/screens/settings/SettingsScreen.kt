package com.amurayada.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.amurayada.music.ui.viewmodel.SettingsViewModel

// Predefined colors for the picker
private val PredefinedColors = listOf(
    Color(0xFF6200EE), // Purple
    Color(0xFF3700B3), // Dark Purple
    Color(0xFF03DAC5), // Teal
    Color(0xFFBB86FC), // Light Purple
    Color(0xFFCF6679), // Red
    Color(0xFFE91E63), // Pink
    Color(0xFFFF5722), // Orange
    Color(0xFFFFC107), // Amber
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFF3F51B5), // Indigo
    Color(0xFF000000)  // Black
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onRescanLibrary: () -> Unit,
    onLoginClick: () -> Unit = {},
    onPersonalizationClick: () -> Unit = {},

    onChangeDownloadLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    

    
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val dynamicColorsEnabled by settingsViewModel.useDynamicColors.collectAsState()
    val sleepTimerMinutes by settingsViewModel.sleepTimerMinutes.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRescanDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    
    // Equalizer launcher
    val equalizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Result handled */ }


    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            item {
                SettingsSection(title = "Apariencia", icon = Icons.Rounded.Palette) {
                    SettingsItem(
                        title = "Tema",
                        subtitle = settingsViewModel.getThemeModeDisplay(),
                        icon = Icons.Rounded.DarkMode,
                        onClick = { showThemeDialog = true }
                    )
                    
                    SettingsToggleItem(
                        title = "Colores dinámicos",
                        subtitle = "Adaptar colores según Material You",
                        icon = Icons.Rounded.ColorLens,
                        checked = dynamicColorsEnabled,
                        onCheckedChange = { settingsViewModel.setDynamicColors(it) }
                    )
                    
                    val isAmoledMode by settingsViewModel.isAmoledMode.collectAsState()
                    SettingsToggleItem(
                        title = "Modo AMOLED",
                        subtitle = "Usar fondo negro puro",
                        icon = Icons.Rounded.Brightness2,
                        checked = isAmoledMode,
                        onCheckedChange = { settingsViewModel.setAmoledMode(it) }
                    )
                }
            }
            // Personalization Section - Link to dedicated screen
            item {
                SettingsSection(title = "Personalización", icon = Icons.Rounded.Brush) {
                    SettingsItem(
                        title = "Personalizar apariencia",
                        subtitle = "Colores, fondos y reproductor",
                        icon = Icons.Rounded.Palette,
                        onClick = onPersonalizationClick
                    )
                }
            }
            
            // Audio Section
            item {
                SettingsSection(title = "Audio", icon = Icons.Rounded.Equalizer) {
                    SettingsItem(
                        title = "Ecualizador",
                        subtitle = "Abrir ecualizador del sistema",
                        icon = Icons.Rounded.Tune,
                        onClick = {
                            try {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                }
                                equalizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                // No equalizer app available
                            }
                        }
                    )
                }
            }


            
            // Content Section
            item {
                SettingsSection(title = "Contenido", icon = Icons.Rounded.Web) {
                    val onlineMode by settingsViewModel.onlineMode.collectAsState()
                    SettingsToggleItem(
                        title = "Modo Online (Beta)",
                        subtitle = "Mostrar recomendaciones de YouTube Music en Inicio",
                        icon = Icons.Rounded.Public,
                        checked = onlineMode,
                        onCheckedChange = { settingsViewModel.setOnlineMode(it) }
                    )
                    
                    val isCanvasEnabled by settingsViewModel.isCanvasEnabled.collectAsState()
                    SettingsToggleItem(
                        title = "Canvas",
                        subtitle = "Videos en bucle como fondo (Beta)",
                        icon = Icons.Rounded.Movie,
                        checked = isCanvasEnabled,
                        onCheckedChange = { settingsViewModel.setCanvasEnabled(it) }
                    )

               }
            }
            
            // Storage Section
            item {
                SettingsSection(title = "Almacenamiento", icon = Icons.Rounded.Storage) {
                    SettingsItem(
                        title = "Ubicación de descarga",
                        subtitle = "Cambiar carpeta de guardado",
                        icon = Icons.Rounded.FolderOpen,
                        onClick = onChangeDownloadLocation
                    )
                }
            }
            
            // Account Section
            item {
                val authManager = remember { com.amurayada.music.data.auth.YouTubeAuthManager.getInstance(context) }
                var isLoggedIn by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val loggedIn = authManager.isLoggedIn()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isLoggedIn = loggedIn
                        }
                    }
                }
                
                SettingsSection(title = "Cuenta", icon = Icons.Rounded.AccountCircle) {
                    if (isLoggedIn) {
                        // Show user profile with avatar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Profile Avatar (using first letter for now - can be replaced with actual photo URL)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "YouTube Music",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Sesión iniciada ✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        SettingsItem(
                            title = "Cerrar sesión",
                            subtitle = "Eliminar credenciales guardadas",
                            icon = Icons.Rounded.Logout,
                            onClick = { 
                                authManager.logout()
                                isLoggedIn = false
                            }
                        )
                    } else {
                        SettingsItem(
                            title = "Iniciar sesión en YouTube Music",
                            subtitle = "Desbloquea contenido personalizado",
                            icon = Icons.Rounded.Login,
                            onClick = onLoginClick
                        )
                    }
                    

                }
            }
            
            // Spotify Section REMOVED (Consolidated into Content)

            // Library Section
            item {
                SettingsSection(title = "Biblioteca", icon = Icons.Rounded.LibraryMusic) {
                    SettingsItem(
                        title = "Reescanear biblioteca",
                        subtitle = "Buscar nuevas canciones",
                        icon = Icons.Rounded.Refresh,
                        onClick = { showRescanDialog = true }
                    )
                }
            }
            // Sección de información y actualizaciones
            item {
                val updateStatus by settingsViewModel.updateStatus.collectAsState()
                
                SettingsSection(title = "Acerca de", icon = Icons.Rounded.Info) {
                    SettingsItem(
                        title = "Versión",
                        subtitle = settingsViewModel.CURRENT_VERSION,
                        icon = Icons.Rounded.Numbers,
                        onClick = { }
                    )
                    
                    // Lógica de visualización del estado de actualización
                    when (updateStatus) {
                        is SettingsViewModel.UpdateStatus.Idle -> {
                            SettingsItem(
                                title = "Buscar actualizaciones",
                                subtitle = "Consultar última versión en GitHub",
                                icon = Icons.Rounded.SystemUpdate,
                                onClick = { settingsViewModel.checkForUpdates() }
                            )
                        }
                        is SettingsViewModel.UpdateStatus.Checking -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Text("Buscando actualizaciones...", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        is SettingsViewModel.UpdateStatus.UpdateAvailable -> {
                            val release = (updateStatus as SettingsViewModel.UpdateStatus.UpdateAvailable).release
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("¡Nueva versión disponible: ${release.versionName}!", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Se ha encontrado una nueva versión en GitHub.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Descargar APK")
                                    }
                                }
                            }
                        }
                        is SettingsViewModel.UpdateStatus.UpToDate -> {
                             SettingsItem(
                                title = "App actualizada",
                                subtitle = "Ya tienes la última versión",
                                icon = Icons.Rounded.CheckCircle,
                                onClick = { settingsViewModel.checkForUpdates() }
                            )
                        }
                        is SettingsViewModel.UpdateStatus.Error -> {
                            SettingsItem(
                                title = "Error al buscar",
                                subtitle = (updateStatus as SettingsViewModel.UpdateStatus.Error).message,
                                icon = Icons.Rounded.Error,
                                onClick = { settingsViewModel.checkForUpdates() }
                            )
                        }
                    }
                    
                    SettingsItem(
                        title = "Desarrollador",
                        subtitle = "Onedev22",
                        icon = Icons.Rounded.Code,
                        onClick = { }
                    )
                }
            }
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Seleccionar tema") },
            text = {
                Column {
                    listOf(
                        "system" to "Sistema",
                        "light" to "Claro",
                        "dark" to "Oscuro"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsViewModel.setThemeMode(value)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    settingsViewModel.setThemeMode(value)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { }
        )
    }
    
    // Sleep timer dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Temporizador de sueño") },
            text = {
                Column {
                    listOf(
                        0 to "Desactivado",
                        15 to "15 minutos",
                        30 to "30 minutos",
                        45 to "45 minutos",
                        60 to "1 hora",
                        90 to "1 hora 30 min",
                        120 to "2 horas"
                    ).forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsViewModel.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sleepTimerMinutes == minutes,
                                onClick = {
                                    settingsViewModel.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { }
        )
    }
    
    // Rescan confirmation dialog
    if (showRescanDialog) {
        AlertDialog(
            onDismissRequest = { showRescanDialog = false },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            title = { Text("Reescanear biblioteca") },
            text = { Text("Se buscarán nuevas canciones en tu dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRescanLibrary()
                        showRescanDialog = false
                    }
                ) {
                    Text("Escanear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val isGlassy = MaterialTheme.colorScheme.background == Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlassy) Modifier.border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassy) 
                Color.White.copy(alpha = 0.12f) 
                else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
