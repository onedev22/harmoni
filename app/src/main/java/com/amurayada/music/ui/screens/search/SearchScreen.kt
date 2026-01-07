package com.amurayada.music.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Playlist
import com.amurayada.music.ui.components.SongListItem
import com.amurayada.music.ui.components.AlbumCard
import com.amurayada.music.ui.components.ArtistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist> = emptyList(), // Default for backward compat until MainActivity updated
    isLoading: Boolean,
    onDownloadClick: (Song) -> Unit = {},
    historyItems: List<String>,
    onHistoryItemClick: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {}, // Default for backward compat
    onBackClick: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val isGlassy = true // Force Glassy look as requested by user
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearch,
                    active = false, // Keep it as a bar, not expanding full screen automatically
                    onActiveChange = {},
                    placeholder = { Text("Search songs, artists, albums...") },
                    leadingIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {}
                
                
                // Filter Chips - Use simple Row for reliable click handling
                if (searchQuery.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SearchFilter.values().forEach { filter ->
                            // Custom "Chip" for better touch response and glass look
                            Surface(
                                onClick = { selectedFilter = filter },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.name.lowercase().capitalize(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selectedFilter == filter) MaterialTheme.colorScheme.onPrimary else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Only show full screen loading if we have absolutely nothing to show

            // Only show full screen loading if we have absolutely nothing to show
            if (isLoading && songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && searchQuery.isNotEmpty()) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         CircularProgressIndicator()
                         Spacer(modifier = Modifier.height(8.dp))
                         Text("Buscando...", style = MaterialTheme.typography.bodyLarge)
                     }
                 }
            } else if (searchQuery.isNotEmpty()) {
                // Show content even if loading (progressive)
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (selectedFilter) {
                        SearchFilter.ALL -> {
                            // Top Result (Best Match)
                            // Priority: Artist -> Song -> Album
                            val topResult = when {
                                artists.isNotEmpty() -> "Artist" to artists.first()
                                songs.isNotEmpty() -> "Song" to songs.first()
                                albums.isNotEmpty() -> "Album" to albums.first()
                                artists.isNotEmpty() -> "Artist" to artists.first()
                                songs.isNotEmpty() -> "Song" to songs.first()
                                albums.isNotEmpty() -> "Album" to albums.first()
                                playlists.isNotEmpty() -> "Playlist" to playlists.first()
                                else -> null
                            }

                            if (topResult != null) {
                                item {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Text(
                                            "Mejor resultado",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        
                                        // Top Result Card
                                        Surface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            color = if (isGlassy) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            border = if (isGlassy) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null,
                                            onClick = {
                                                when(topResult.first) {
                                                    "Artist" -> onArtistClick(topResult.second as Artist)
                                                    "Song" -> onSongClick(topResult.second as Song, songs)
                                                    "Artist" -> onArtistClick(topResult.second as Artist)
                                                    "Song" -> onSongClick(topResult.second as Song, songs)
                                                    "Album" -> onAlbumClick(topResult.second as Album)
                                                    "Playlist" -> onPlaylistClick(topResult.second as Playlist)
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Image
                                                AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(when(topResult.first) {
                                                            "Artist" -> (topResult.second as Artist).imageUrl
                                                            "Song" -> (topResult.second as Song).albumArtUri
                                                            "Album" -> (topResult.second as Album).artworkUri
                                                            "Artist" -> (topResult.second as Artist).imageUrl
                                                            "Song" -> (topResult.second as Song).albumArtUri
                                                            "Album" -> (topResult.second as Album).artworkUri
                                                            "Playlist" -> (topResult.second as Playlist).artworkUri
                                                            else -> null
                                                        })
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(if (topResult.first == "Artist") androidx.compose.foundation.shape.CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                
                                                Spacer(modifier = Modifier.width(16.dp))
                                                
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = when(topResult.first) {
                                                            "Artist" -> (topResult.second as Artist).name
                                                            "Song" -> (topResult.second as Song).title
                                                            "Artist" -> (topResult.second as Artist).name
                                                            "Song" -> (topResult.second as Song).title
                                                            "Album" -> (topResult.second as Album).name
                                                            "Playlist" -> (topResult.second as Playlist).name
                                                            else -> ""
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                        maxLines = 1,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = when(topResult.first) {
                                                            "Artist" -> "Artista"
                                                            "Song" -> "Canción • ${(topResult.second as Song).artist}"
                                                            "Artist" -> "Artista"
                                                            "Song" -> "Canción • ${(topResult.second as Song).artist}"
                                                            "Album" -> "Álbum • ${(topResult.second as Album).artist}"
                                                            "Playlist" -> "Playlist • ${(topResult.second as Playlist).songCount} canciones"
                                                            else -> ""
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Songs Section (Vertical List)
                            if (songs.isNotEmpty()) {
                                item(key = "songs_header") {
                                    Text(
                                        "Canciones",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(
                                    items = songs,
                                    key = { "song_${it.id}" }
                                ) { song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { onSongClick(song, songs) },
                                        onDownloadClick = { onDownloadClick(song) }
                                    )
                                }
                            }
                            


                            // Albums Section (Vertical List)
                            if (albums.isNotEmpty()) {
                                item {
                                    Text(
                                        "Álbumes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(
                                    items = albums,
                                    key = { "album_${it.id}" }
                                ) { album ->
                                    ListItem(
                                        headlineContent = { Text(album.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                        supportingContent = { Text("Álbum • ${album.artist}") },
                                        leadingContent = {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(album.artworkUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        },
                                        modifier = Modifier.clickable { onAlbumClick(album) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }

                            
                            // Playlists Section (Vertical List)
                            if (playlists.isNotEmpty()) {
                                item {
                                    Text(
                                        "Playlists",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(
                                    items = playlists,
                                    key = { "playlist_${it.id}" }
                                ) { playlist ->
                                    ListItem(
                                        headlineContent = { Text(playlist.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                        supportingContent = { Text("Playlist • ${playlist.songCount} canciones") },
                                        leadingContent = {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(playlist.artworkUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        },
                                        modifier = Modifier.clickable { onPlaylistClick(playlist) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                        
                        SearchFilter.SONGS -> {
                            if (songs.isNotEmpty()) {
                                item {
                                    Text(
                                        "Canciones",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                items(
                                    items = songs,
                                    key = { it.id }
                                ) { song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { onSongClick(song, songs) },
                                        onDownloadClick = { onDownloadClick(song) }
                                    )
                                }
                            }
                        }
                        
                        SearchFilter.ARTISTS -> {
                            if (artists.isNotEmpty()) {
                                item {
                                    Text(
                                        "Artistas",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                items(
                                    items = artists,
                                    key = { it.id }
                                ) { artist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onArtistClick(artist) }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                         AsyncImage(
                                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                .data(artist.imageUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        
                        SearchFilter.ALBUMS -> {
                            if (albums.isNotEmpty()) {
                                item {
                                    Text(
                                        "Álbumes",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                items(
                                    items = albums,
                                    key = { it.id }
                                ) { album ->
                                    ListItem(
                                        headlineContent = { Text(album.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                        supportingContent = { Text("Álbum • ${album.artist}") },
                                        leadingContent = {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(album.artworkUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        },
                                        modifier = Modifier.clickable { onAlbumClick(album) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }

                        
                        SearchFilter.PLAYLISTS -> {
                            if (playlists.isNotEmpty()) {
                                item {
                                    Text(
                                        "Playlists",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                items(
                                    items = playlists,
                                    key = { it.id }
                                ) { playlist ->
                                    ListItem(
                                        headlineContent = { Text(playlist.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                                        supportingContent = { Text("Playlist • ${playlist.songCount} canciones") },
                                        leadingContent = {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(playlist.artworkUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        },
                                        modifier = Modifier.clickable { onPlaylistClick(playlist) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // History
                LazyColumn {
                    item {
                        Text(
                            "Búsquedas recientes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(historyItems) { historyItem ->
                        ListItem(
                            headlineContent = { Text(historyItem) },
                            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                            modifier = Modifier.clickable { onHistoryItemClick(historyItem) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isGlassy) Color.Transparent else MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                }
            }
        }
    }
}

enum class SearchFilter {

    ALL, SONGS, ARTISTS, ALBUMS, PLAYLISTS
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
