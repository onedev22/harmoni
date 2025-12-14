package com.amurayada.music.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyItems: List<Song>,
    onSongClick: (Song) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Historial",
                        fontWeight = FontWeight.Bold
                    )
                    if (historyItems.isNotEmpty()) {
                        Text(
                            "${historyItems.size} canciones escuchadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                if (historyItems.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Borrar historial")
                    }
                }
            }
        )
        
        if (historyItems.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Historial vacío",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Las canciones que escuches aparecerán aquí",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = historyItems,
                    key = { index, song -> "${song.id}_$index" },
                    contentType = { _, _ -> "song_item" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        index = historyItems.size - index
                    )
                }
            }
        }
    }
    
    // Clear history dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("¿Borrar historial?") },
            text = { Text("Se eliminará todo tu historial de reproducción. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
