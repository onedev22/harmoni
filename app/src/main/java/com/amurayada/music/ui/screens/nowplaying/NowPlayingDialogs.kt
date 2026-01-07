package com.amurayada.music.ui.screens.nowplaying

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Song

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
                modifier = Modifier.verticalScroll(rememberScrollState())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    isRunning: Boolean,
    remainingTime: Long?,
    onDismiss: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(30f) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Temporizador de sueño",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isRunning) {
                    // Calculate minutes remaining, rounding up
                    val remainingMillis = remainingTime ?: 0L
                    val minutes = if (remainingMillis > 0) (remainingMillis + 59000) / 60000 else 0
                    
                    Text(
                        text = if (minutes > 0) "Se detendrá en $minutes min" else "Finalizando...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isRunning) {
                Button(
                    onClick = onCancelTimer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detener temporizador")
                }
            } else {
                // Slider Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duración",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${sliderValue.toInt()} min",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 5f..120f,
                        steps = 22, // (120-5)/5 - 1 = 22 steps of 5 min
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("120m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Quick Presets
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Rápido",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            SuggestionChip(
                                onClick = { onStartTimer(mins) },
                                label = { Text("$mins min") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Start Button
                Button(
                    onClick = { onStartTimer(sliderValue.toInt()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Iniciar")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
