package com.amurayada.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSelectionSheet(
    allSongs: List<Song>,
    currentPlaylistSongIds: List<Long>,
    onDismiss: () -> Unit,
    onSongSelected: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.artist.contains(searchQuery, ignoreCase = true) 
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Text(
                text = "Agregar canciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar canción...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredSongs) { song ->
                    val isAlreadyInPlaylist = currentPlaylistSongIds.contains(song.id)
                    
                    ListItem(
                        headlineContent = { Text(song.title) },
                        supportingContent = { Text(song.artist) },
                        trailingContent = {
                            if (isAlreadyInPlaylist) {
                                Icon(
                                    Icons.Default.Check, 
                                    contentDescription = "Agregada",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(onClick = { onSongSelected(song) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                                }
                            }
                        },
                        modifier = Modifier
                            .clickable(enabled = !isAlreadyInPlaylist) {
                                if (!isAlreadyInPlaylist) {
                                    onSongSelected(song)
                                }
                            }
                            .padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}
