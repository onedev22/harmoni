package com.amurayada.music.ui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Playlist
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    allSongs: List<Song> = emptyList(),
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onAddSong: (Song) -> Unit = {},
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Playlist no encontrada")
        }
        return
    }

    var showAddSongDialog by remember { mutableStateOf(false) }

    val isGlassy = MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Transparent

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { 
                    Text(
                        text = playlist.name
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSongDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Songs")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = if (isGlassy) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (isGlassy) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header with stats and actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${songs.size} canciones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reproducir")
                    }
                    
                    OutlinedButton(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aleatorio")
                    }
                }
            }
            
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Esta playlist está vacía",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showAddSongDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agregar canciones")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 220.dp)
                ) {
                    items(songs) { song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(song) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveSong(song.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove from playlist",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (showAddSongDialog) {
            com.amurayada.music.ui.components.SongSelectionSheet(
                allSongs = allSongs,
                currentPlaylistSongIds = playlist.songIds,
                onDismiss = { showAddSongDialog = false },
                onSongSelected = { song ->
                    onAddSong(song)
                }
            )
        }
    }
}
