package com.amurayada.music.ui.screens.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.amurayada.music.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onRescanLibrary: () -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                }
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
                    
                    SettingsItem(
                        title = "Temporizador de sueño",
                        subtitle = if (sleepTimerMinutes > 0) "$sleepTimerMinutes minutos" else "Desactivado",
                        icon = Icons.Rounded.Bedtime,
                        onClick = { showSleepTimerDialog = true }
                    )
                }
            }
            
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
            
            // About Section
            item {
                SettingsSection(title = "Acerca de", icon = Icons.Rounded.Info) {
                    SettingsItem(
                        title = "Versión",
                        subtitle = "1.0.0",
                        icon = Icons.Rounded.Numbers,
                        onClick = { }
                    )
                    
                    SettingsItem(
                        title = "Desarrollador",
                        subtitle = "Amurayada",
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
