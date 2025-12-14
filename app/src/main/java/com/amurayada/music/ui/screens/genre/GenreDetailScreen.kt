package com.amurayada.music.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Genre
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: Genre?,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (genre == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Género no encontrado")
        }
        return
    }
    
    // Static gradient for now, or could be random/based on first song art
    val gradientColor = MaterialTheme.colorScheme.primary
    
    Box(modifier = modifier.fillMaxSize()) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to gradientColor.copy(alpha = 0.6f),
                        0.7f to Color.Black
                    )
                )
        )
        
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Genre header
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        
                        Text(
                            text = "${songs.size} canciones",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Action buttons
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reproducir")
                        }
                        
                        FilledTonalButton(
                            onClick = onShuffle,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aleatorio")
                        }
                    }
                }
                
                // Songs list
                itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "song_item" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        index = index + 1
                    )
                }
            }
        }
    }
}
