package com.amurayada.music.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Song
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    albums: List<Album>,
    recentlyPlayed: List<Song>,
    recentlyAddedSongs: List<Song>,
    mostPlayed: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Buenos días"
            hour < 18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }
    
    val suggestedSongs by remember(songs) {
        derivedStateOf {
            if (songs.isNotEmpty()) songs.shuffled().take(6) else emptyList()
        }
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Modern Header with Dynamic Gradient
        item(key = "header") {
            val colorScheme = MaterialTheme.colorScheme
            val backgroundBrush = remember(colorScheme) {
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = 0.15f),
                        colorScheme.primaryContainer.copy(alpha = 0.08f),
                        colorScheme.background
                    )
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundBrush)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "¿Qué quieres escuchar?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(
                            onClick = onSearchClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                        }
                        FilledIconButton(
                            onClick = onSettingsClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                        }
                    }
                }
            }
        }
        
        // Premium Quick Actions
        item(key = "quick_actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val colorScheme = MaterialTheme.colorScheme
                
                // Shuffle All Button
                ElevatedCard(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            val shuffled = songs.shuffled()
                            onSongClick(shuffled.first(), shuffled)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Mezclar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Play All Button
                ElevatedCard(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            onSongClick(songs.first(), songs)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Reproducir",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
        
        // Recently Added
        if (recentlyAddedSongs.isNotEmpty()) {
            item(key = "recently_added_header") {
                Spacer(modifier = Modifier.height(12.dp))
                ModernSectionHeader(
                    title = "Agregadas recientemente",
                    icon = Icons.Rounded.NewReleases,
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }
            
            item(key = "recently_added_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = recentlyAddedSongs,
                        key = { it.id },
                        contentType = { "song_card" }
                    ) { song ->
                        ModernSongCard(
                            song = song,
                            onClick = { onSongClick(song, recentlyAddedSongs) }
                        )
                    }
                }
            }
        }
        
        // Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recent_header") {
                Spacer(modifier = Modifier.height(24.dp))
                ModernSectionHeader(
                    title = "Escuchado recientemente",
                    icon = Icons.Rounded.History,
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }
            
            item(key = "recent_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = recentlyPlayed.take(10),
                        key = { "recent_${it.id}" },
                        contentType = { "song_card" }
                    ) { song ->
                        ModernSongCard(
                            song = song,
                            onClick = { onSongClick(song, recentlyPlayed) }
                        )
                    }
                }
            }
        }
        
        // Most Played
        if (mostPlayed.isNotEmpty()) {
            item(key = "mostplayed_header") {
                Spacer(modifier = Modifier.height(24.dp))
                ModernSectionHeader(
                    title = "Lo más escuchado",
                    icon = Icons.Rounded.TrendingUp,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            
            item(key = "mostplayed_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = mostPlayed.take(10),
                        key = { "most_${it.id}" },
                        contentType = { "song_card" }
                    ) { song ->
                        ModernSongCard(
                            song = song,
                            onClick = { onSongClick(song, mostPlayed) }
                        )
                    }
                }
            }
        }
        
        // Albums
        if (albums.isNotEmpty()) {
            item(key = "albums_header") {
                Spacer(modifier = Modifier.height(24.dp))
                ModernSectionHeader(
                    title = "Tus álbumes",
                    icon = Icons.Rounded.Album,
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }
            
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = albums.take(10),
                        key = { "album_${it.id}" },
                        contentType = { "album_card" }
                    ) { album ->
                        ModernAlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
            }
        }
        
        // Suggested for you
        if (suggestedSongs.isNotEmpty()) {
            item(key = "suggested_header") {
                Spacer(modifier = Modifier.height(24.dp))
                ModernSectionHeader(
                    title = "Sugerido para ti",
                    icon = Icons.Rounded.AutoAwesome,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            
            items(
                items = suggestedSongs,
                key = { "suggested_${it.id}" }
            ) { song ->
                ModernSuggestedItem(
                    song = song,
                    onClick = { onSongClick(song, suggestedSongs) }
                )
            }
        }
        
        // Empty state
        if (songs.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "No hay música todavía",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Agrega música a tu dispositivo para comenzar",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModernSectionHeader(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ModernSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Box {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModernAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            AsyncImage(
                model = album.artworkUri,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModernSuggestedItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedCard(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
