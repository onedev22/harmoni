package com.amurayada.music.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoriteSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onRemoveFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Favoritos",
                        fontWeight = FontWeight.Bold
                    )
                    if (favoriteSongs.isNotEmpty()) {
                        Text(
                            "${favoriteSongs.size} canciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                if (favoriteSongs.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = {
                            if (favoriteSongs.isNotEmpty()) {
                                val shuffled = favoriteSongs.shuffled()
                                onSongClick(shuffled.first())
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Mezclar")
                    }
                }
            }
        )
        
        if (favoriteSongs.isEmpty()) {
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
                        Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Sin favoritos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Las canciones que marques como favoritas aparecerán aquí",
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
                // Play all button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (favoriteSongs.isNotEmpty()) {
                                    onSongClick(favoriteSongs.first())
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reproducir todo")
                        }
                    }
                }
                
                items(favoriteSongs, key = { it.id }) { song ->
                    FavoriteSongItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemoveFavorite(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteSongItem(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SongListItem(
                song = song,
                onClick = onClick
            )
        }
        
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "Quitar de favoritos",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
