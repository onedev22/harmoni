package com.amurayada.music.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.HomeSection
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    albums: List<Album>,
    recentlyPlayed: List<Song>,
    recentlyAddedSongs: List<Song>,
    mostPlayed: List<Song>,
    timeCapsuleSongs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (com.amurayada.music.data.model.Playlist) -> Unit = {},

    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRecapClick: () -> Unit,

    onTimeCapsuleClick: () -> Unit,
    onRefreshClick: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    isOnlineMode: Boolean = false,
    isHomeExhausted: Boolean = false,
    onlineSections: List<HomeSection> = emptyList(),
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
    
    val suggestedSongs = remember(songs) {
        if (songs.isNotEmpty()) songs.shuffled().take(6) else emptyList()
    }
    
    val colorScheme = MaterialTheme.colorScheme
    val listState = androidx.compose.foundation.lazy.rememberLazyListState() // Use fully qualified class name or import it if missing
    
    // Improved Scroll Detection using state
    val shouldLoadMore by remember {
        androidx.compose.runtime.derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // Trigger when reaching the last few items
            // Ensuring we have content to scroll
            totalItems > 3 && lastVisibleItemIndex >= (totalItems - 2)
        }
    }
    
    LaunchedEffect(shouldLoadMore, isHomeExhausted) {
        if (shouldLoadMore && !isHomeExhausted) {
            onLoadMore()
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 200.dp)
    ) {
        // Clean Header
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Buscar",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Más",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Actualizar") },
                                onClick = { showMenu = false; onRefreshClick() },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Historial") },
                                onClick = { showMenu = false; onHistoryClick() },
                                leadingIcon = { Icon(Icons.Rounded.History, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Ajustes") },
                                onClick = { showMenu = false; onSettingsClick() },
                                leadingIcon = { Icon(Icons.Rounded.Settings, null) }
                            )
                        }
                    }
                }
            }
        }
        
        // Quick Actions Chips
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp) // Reduced from 24.dp
            ) {
                item {
                    ActionChip(
                        text = "Aleatorio",
                        icon = Icons.Rounded.Shuffle,
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val shuffled = songs.shuffled()
                                onSongClick(shuffled.first(), shuffled)
                            }
                        }
                    )
                }
                item {
                    ActionChip(
                        text = "Cápsula",
                        icon = Icons.Rounded.HourglassEmpty,
                        onClick = onTimeCapsuleClick
                    )
                }
                item {
                    ActionChip(
                        text = "Recap",
                        icon = Icons.Rounded.AutoGraph,
                        onClick = onRecapClick
                    )
                }
                item {
                    ActionChip(
                        text = "Historial",
                        icon = Icons.Rounded.History,
                        onClick = onHistoryClick
                    )
                }
            }
        }
        
        // Online Mode Content
        if (isOnlineMode) {
             if (onlineSections.isEmpty()) {
                 item(key = "online_loading") {
                     Box(
                         modifier = Modifier.fillMaxWidth().padding(32.dp),
                         contentAlignment = Alignment.Center
                     ) {
                         CircularProgressIndicator()
                     }
                 }
             } else {
                  val suggestions = onlineSections.find { it.title == "Sugerencias para ti" }
                  val others = onlineSections.filter { it.title != "Sugerencias para ti" }

                  // 1. Suggestions Section (Paged Layout: 4 items per page)
                  if (suggestions != null) {
                      item(key = "suggestions_paged") {
                          Column {
                              GoogleSectionHeader(suggestions.title)
                              
                               val chunkedSongs = suggestions.songs.take(16).chunked(4) // 4 items per column (Quick Picks style)
                               
                               LazyRow(
                                   contentPadding = PaddingValues(horizontal = 24.dp),
                                   horizontalArrangement = Arrangement.spacedBy(16.dp),
                                   modifier = Modifier.fillMaxWidth()
                               ) {
                                   items(chunkedSongs) { pageSongs ->
                                       Column(
                                           modifier = Modifier.width(300.dp), // Fixed width for the column
                                           verticalArrangement = Arrangement.spacedBy(12.dp)
                                       ) {
                                           pageSongs.forEach { song ->
                                               GoogleStyleWideCard(
                                                   song = song,
                                                   onClick = { onSongClick(song, suggestions.songs) }
                                               )
                                           }
                                       }
                                   }
                               }
                           }
                       }
                  }

                  // 2. Recently Played (Injected here for Online Mode)
                  if (recentlyPlayed.isNotEmpty()) {
                      item(key = "recent_header_online") {
                          GoogleSectionHeader("Escuchado recientemente")
                      }
                      item(key = "recent_list_online") {
                          LazyRow(
                              contentPadding = PaddingValues(horizontal = 24.dp),
                              horizontalArrangement = Arrangement.spacedBy(16.dp)
                          ) {
                              items(
                                  items = recentlyPlayed.take(10),
                                  key = { "recent_online_${it.id}" }
                              ) { song ->
                                  GoogleStyleCard(song) { onSongClick(song, recentlyPlayed) }
                              }
                          }
                      }
                  }

                  // 3. Other Sections
                  items(
                      items = others,
                      key = { "section_${it.title}" }
                  ) { section ->
                      // Check for content first
                      val hasSongs = section.songs.isNotEmpty()
                      val hasPlaylists = section.playlists.isNotEmpty()
                      val hasAlbums = section.albums.isNotEmpty()
                      
                      if (hasSongs || hasPlaylists || hasAlbums) {
                          GoogleSectionHeader(section.title)
                          
                          // DYNAMIC LAYOUT SELECTION
                          if (hasSongs) {
                                // SONGS -> Use Quick Picks Grid (Mixed 4 items per column)
                                val chunkedSongs = section.songs.take(16).chunked(4)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(chunkedSongs) { pageSongs ->
                                        Column(
                                            modifier = Modifier.width(300.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            pageSongs.forEach { song ->
                                                GoogleStyleWideCard(song = song, onClick = { onSongClick(song, section.songs) })
                                            }
                                        }
                                    }
                                }
                          } else if (hasPlaylists) {
                                // PLAYLISTS
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(section.playlists) { playlist ->
                                        GoogleStylePlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                                    }
                                }
                          } else {
                              // ALBUMS
                              val isImmersive = section.title.lowercase().contains("mix") || 
                                                section.title.lowercase().contains("radio") || 
                                                section.title.lowercase().contains("again") ||
                                                section.title.lowercase().contains("de nuevo") ||
                                                section.title.lowercase().contains("pick") ||
                                                section.title.lowercase().contains("selecciones")
    
                              LazyRow(
                                   contentPadding = PaddingValues(horizontal = 24.dp),
                                   horizontalArrangement = Arrangement.spacedBy(16.dp)
                              ) {
                                   if (isImmersive) {
                                       items(section.albums) { album ->
                                            GoogleStyleImmersiveCard(album = album, onClick = { onAlbumClick(album) })
                                       }
                                   } else {
                                       items(section.albums) { album ->
                                            GoogleStyleAlbumCard(album = album, onClick = { onAlbumClick(album) })
                                       }
                                   }
                              }
                          }
                      } // End if hasContent
                   }
                   }
              
              // Infinite Scroll Detection & Loading Indicator
              if (!isHomeExhausted && onlineSections.isNotEmpty()) {
                  item(key = "load_more_indicator") {
                       Box(
                       modifier = Modifier
                           .fillMaxWidth()
                           .padding(16.dp),
                       contentAlignment = Alignment.Center
                   ) {
                       CircularProgressIndicator(
                           modifier = Modifier.size(24.dp),
                           strokeWidth = 2.dp,
                           color = MaterialTheme.colorScheme.secondary
                       )
                  }
              }
             }

        }


        // Recently Added (Local Only)
        if (!isOnlineMode && recentlyAddedSongs.isNotEmpty()) {
            item(key = "recently_added_header") {
                GoogleSectionHeader("Agregadas recientemente")
            }
            item(key = "recently_added_list") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = recentlyAddedSongs.take(10), // Limit to 10 items
                        key = { it.id }
                    ) { song ->
                        GoogleStyleCard(song) { onSongClick(song, recentlyAddedSongs) }
                    }
                }
            }
        }
        
        // Recently Played (Local Only - fallback if offline)
        if (!isOnlineMode && recentlyPlayed.isNotEmpty()) {
            item(key = "recent_header") {
                GoogleSectionHeader("Escuchado recientemente")
            }
            item(key = "recent_list") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = recentlyPlayed.take(10),
                        key = { "recent_${it.id}" }
                    ) { song ->
                        GoogleStyleCard(song) { onSongClick(song, recentlyPlayed) }
                    }
                }
            }
        }
        
        // Albums (Local Only)
        if (!isOnlineMode && albums.isNotEmpty()) {
            item(key = "albums_header") {
                GoogleSectionHeader("Tus álbumes")
            }
            item(key = "albums_list") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = albums.take(10),
                        key = { "album_${it.id}" }
                    ) { album ->
                        GoogleStyleAlbumCard(album) { onAlbumClick(album) }
                    }
                }
            }
        }
        
        // Suggested (Local Only)
        if (!isOnlineMode && suggestedSongs.isNotEmpty()) {
            item(key = "suggested_header") {
                GoogleSectionHeader("Sugerencias rápidas")
            }
            items(
                items = suggestedSongs,
                key = { "suggested_${it.id}" }
            ) { song ->
                GoogleStyleListItem(song, onClick = { onSongClick(song, suggestedSongs) })
            }
        }
        
        // Empty State
        if (songs.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tu música aparecerá aquí",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp) // Reduced spacing
    )
}

@Composable
private fun ActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val isGlassy = MaterialTheme.colorScheme.background == Color.Transparent
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp), // Slightly rounded square-ish pill
        color = if (isGlassy) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp, 
            color = if (isGlassy) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GoogleStyleCard(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp) // Slightly narrower
            .clickable(onClick = onClick)
    ) {
        // Image Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.aspectRatio(1f)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Text (No container)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2, // Allow 2 lines
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
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
private fun GoogleStyleAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.aspectRatio(1f)
        ) {
            if (album.artworkUri != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(album.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (MaterialTheme.colorScheme.background == Color.Transparent) 0.1f else 1f)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
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
private fun GoogleStyleListItem(
    song: Song,
    onDownloadClick: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        // 3-Dots Menu
        Box(modifier = Modifier.offset(x = 8.dp)) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Descargar") },
                    onClick = { 
                        showMenu = false
                        onDownloadClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun GoogleStyleWideCard(
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth() // adjusted to fill container
            .clickable(onClick = onClick)
            .padding(end = 8.dp), // reduced padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(64.dp) // Bigger image (was 56)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall, // Bigger font
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Removed Play Button to reduce clutter and making the whole card feel clickable like YTM
    }
}

@Composable
private fun GoogleStyleArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.aspectRatio(1f)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(artist.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
private fun GoogleStyleImmersiveCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp) // Large width
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(180.dp) // Large Square
                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = MaterialTheme.colorScheme.primary)
        ) {
            if (album.artworkUri != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(album.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Gradient Overlay for text readability if title is overlay? No, we put title below.
            // But let's add a subtle "Play" icon overlay
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                    )
                ),
                contentAlignment = Alignment.BottomEnd
            ) {
                 Icon(
                     imageVector = Icons.Rounded.PlayArrow,
                     contentDescription = null,
                     tint = Color.White,
                     modifier = Modifier.padding(12.dp).size(32.dp)
                 )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun GoogleStylePlaylistCard(
    playlist: com.amurayada.music.data.model.Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.aspectRatio(1f)
        ) {
            if (playlist.artworkUri != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(playlist.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = playlist.author ?: "Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
