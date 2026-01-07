package com.amurayada.music.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artist: Artist,
    topSongs: List<Song>,
    albums: List<Album>,
    singles: List<Album> = emptyList(),
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val defaultBackground = MaterialTheme.colorScheme.background
    var gradientColors by remember { mutableStateOf(Color.Transparent to defaultBackground) }
    
    LaunchedEffect(artist.imageUrl) {
        if (artist.imageUrl != null) {
            try {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(artist.imageUrl)
                    .allowHardware(false)
                    .build()
                val result = coil.ImageLoader(context).execute(request)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bitmap?.let {
                    androidx.palette.graphics.Palette.from(it).generate { palette ->
                        gradientColors = com.amurayada.music.ui.utils.extractGradientColors(palette)
                    }
                }
            } catch (e: Exception) {
                // Keep default
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 200.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Artist Header (Immersive Image)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp) // Taller header
                ) {
                    if (artist.imageUrl != null) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(artist.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = artist.name,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (MaterialTheme.colorScheme.background == Color.Transparent) 
                                        Color.White.copy(alpha = 0.1f) 
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artist.name.firstOrNull()?.toString() ?: "",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // Artist Name Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${artist.songCount} Suscriptores", // Using songCount as subscriber proxy
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Popular Songs
            if (topSongs.isNotEmpty()) {
                item {
                    Text(
                        text = "Canciones populares",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp)
                    )
                }
                itemsIndexed(
                    items = topSongs.take(5),
                    key = { _, song -> song.id }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song, topSongs) },
                        index = index + 1
                    )
                }
            }
            
            // More Songs (Horizontal Carousel - "Más canciones")
            if (topSongs.size > 5) {
                item {
                    Text(
                        text = "Más canciones",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = topSongs.drop(5),
                            key = { it.id }
                        ) { song -> // Show remaining songs
                             Column(
                                 modifier = Modifier
                                     .width(140.dp)
                                     .clickable { onSongClick(song, topSongs) }
                             ) {
                                 AsyncImage(
                                     model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                         .data(song.albumArtUri)
                                         .crossfade(true)
                                         .build(),
                                     contentDescription = song.title,
                                     modifier = Modifier
                                         .size(140.dp)
                                         .clip(RoundedCornerShape(8.dp)),
                                     contentScale = ContentScale.Crop
                                 )
                                 Spacer(modifier = Modifier.height(8.dp))
                                 Text(
                                     text = song.title,
                                     style = MaterialTheme.typography.bodyMedium,
                                     maxLines = 1,
                                     overflow = TextOverflow.Ellipsis
                                 )
                                 Text(
                                     text = song.artist,
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     maxLines = 1
                                 )
                             }
                        }
                    }
                }
            }

            // Albums (Horizontal)
            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Álbumes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = albums,
                            key = { it.id }
                        ) { album ->
                            Column(
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onAlbumClick(album) }
                            ) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(album.artworkUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = album.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Álbum • ${album.artist}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Singles (Horizontal)
            if (singles.isNotEmpty()) {
                item {
                    Text(
                        text = "Sencillos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = singles,
                            key = { it.id }
                        ) { album ->
                            Column(
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onAlbumClick(album) }
                            ) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(album.artworkUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = album.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sencillo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }


        }
        
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)

        }
    }
}
