package com.amurayada.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    
    val recentlyPlayedAlbums = remember(recentlyPlayed, albums) {
        recentlyPlayed
            .mapNotNull { song -> albums.find { it.name == song.album && it.artist == song.artist } }
            .distinctBy { it.id }
            .take(10)
    }
    
    val recentlyAddedLimited = remember(recentlyAddedSongs) { recentlyAddedSongs.take(10) }
    val mostPlayedLimited = remember(mostPlayed) { mostPlayed.take(10) }
    val albumsLimited = remember(albums) { albums.take(10) }
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
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header", contentType = "header") {
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
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "¿Qué quieres escuchar?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(onClick = onSearchClick) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                        }
                        FilledIconButton(onClick = onSettingsClick) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                        }
                    }
                }
            }
        }
        
        item(key = "quick_actions", contentType = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    onClick = {
                        if (songs.isNotEmpty()) {
                            val shuffled = songs.shuffled()
                            onSongClick(shuffled.first(), shuffled)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(22.dp), colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Mezclar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                
                Card(
                    onClick = { if (songs.isNotEmpty()) onSongClick(songs.first(), songs) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp), colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Text("Reproducir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        if (recentlyAddedLimited.isNotEmpty()) {
            item(key = "recently_added", contentType = "section") {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Agregadas recientemente", Icons.Rounded.NewReleases, colorScheme.tertiary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentlyAddedLimited.forEach { song ->
                            key(song.id) {
                                SimpleSongCard(song) { onSongClick(song, recentlyAddedSongs) }
                            }
                        }
                    }
                }
            }
        }
        
        if (recentlyPlayedAlbums.isNotEmpty()) {
            item(key = "recent_albums", contentType = "section") {
                Column {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Álbumes recientes", Icons.Rounded.History, colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentlyPlayedAlbums.forEach { album ->
                            key("recent_${album.id}") {
                                SimpleAlbumCard(album) { onAlbumClick(album) }
                            }
                        }
                    }
                }
            }
        }
        
        if (mostPlayedLimited.isNotEmpty()) {
            item(key = "most_played", contentType = "section") {
                Column {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Lo más escuchado", Icons.AutoMirrored.Rounded.TrendingUp, colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        mostPlayedLimited.forEach { song ->
                            key("most_${song.id}") {
                                SimpleSongCard(song) { onSongClick(song, mostPlayed) }
                            }
                        }
                    }
                }
            }
        }
        
        if (albumsLimited.isNotEmpty()) {
            item(key = "albums", contentType = "section") {
                Column {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Tus álbumes", Icons.Rounded.Album, colorScheme.tertiary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        albumsLimited.forEach { album ->
                            key("album_${album.id}") {
                                SimpleAlbumCard(album) { onAlbumClick(album) }
                            }
                        }
                    }
                }
            }
        }
        
        if (songs.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(64.dp), colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("No hay música todavía", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Agrega música a tu dispositivo", color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        item(key = "spacer", contentType = "spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), color)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SimpleSongCard(song: Song, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.width(120.dp).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = remember(song.albumArtUri) {
                ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(false)
                    .size(240)
                    .build()
            },
            contentDescription = null,
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SimpleAlbumCard(album: Album, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.width(120.dp).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = remember(album.artworkUri) {
                ImageRequest.Builder(context)
                    .data(album.artworkUri)
                    .crossfade(false)
                    .size(240)
                    .build()
            },
            contentDescription = null,
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
