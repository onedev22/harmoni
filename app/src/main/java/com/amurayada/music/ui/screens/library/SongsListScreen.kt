package com.amurayada.music.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SongsListScreen(
    songs: List<Song>,
    playlists: List<com.amurayada.music.data.model.Playlist>,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: (com.amurayada.music.data.model.Playlist, Song) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeleteSong: (Song) -> Unit = {},
    modifier: Modifier = Modifier,
    currentSong: Song? = null,
    isPlaying: Boolean = false
) {
    var showAddToPlaylistDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Song?>(null) }

    if (songs.isEmpty()) {
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
                    text = "No hay canciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Agrega música a tu dispositivo para comenzar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Quick actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reproducir")
                    }
                    
                    FilledTonalButton(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aleatorio")
                    }
                }
            }
            
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song_item" }
            ) { index, song ->
                var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    index = index + 1,
                    isCurrentSong = currentSong?.id == song.id,
                    isPlaying = isPlaying && currentSong?.id == song.id,
                    trailingContent = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Agregar a Playlist") },
                                    onClick = {
                                        showMenu = false
                                        showAddToPlaylistDialog = song
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar") },
                                    onClick = {
                                        showMenu = false
                                        onDeleteSong(song)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddToPlaylistDialog != null) {
        com.amurayada.music.ui.components.AddToPlaylistSheet(
            playlists = playlists,
            onPlaylistSelected = { playlist ->
                showAddToPlaylistDialog?.let { song: Song ->
                    onAddToPlaylist(playlist, song)
                }
                showAddToPlaylistDialog = null
            },
            onCreateNewPlaylist = {
                onCreatePlaylist()
                showAddToPlaylistDialog = null
            },
            onDismiss = { showAddToPlaylistDialog = null }
        )
    }
}
