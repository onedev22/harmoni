package com.amurayada.music.ui.screens.album

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: Album?,
    songs: List<Song>,
    allAlbums: List<Album>,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (album == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Álbum no encontrado")
        }
        return
    }
    
    val context = LocalContext.current
    
    // Dynamic colors from album art
    var gradientColor by remember { mutableStateOf(Color.Black) }
    
    LaunchedEffect(album.artworkUri) {
        try {
            val request = ImageRequest.Builder(context)
                .data(album.artworkUri)
                .allowHardware(false)
                .build()
            val result = ImageLoader(context).execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let {
                Palette.from(it).generate { palette ->
                    gradientColor = getColorFromPalette(palette)
                }
            }
        } catch (e: Exception) {
            gradientColor = Color.Black
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Gradient: SimpMusic Style - Linear 135 degrees
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to gradientColor,
                        0.3f to Color.Black
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
            // Album header
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = album.artworkUri,
                        contentDescription = album.name,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${songs.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            containerColor = gradientColor
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
            
            // Other albums from this artist
            item(key = "other_albums") {
                val otherAlbums = allAlbums.filter { 
                    it.artist == album.artist && it.id != album.id 
                }
                
                if (otherAlbums.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Más de ${album.artist}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                count = otherAlbums.size,
                                key = { index -> otherAlbums[index].id },
                                contentType = { "album_card" }
                            ) { index ->
                                val otherAlbum = otherAlbums[index]
                                Column(
                                    modifier = Modifier
                                        .width(140.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onAlbumClick(otherAlbum) }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(otherAlbum.artworkUri)
                                                .crossfade(true)
                                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                .size(300)
                                                .build(),
                                            contentDescription = otherAlbum.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = otherAlbum.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${otherAlbum.songCount} canciones",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private fun getColorFromPalette(palette: Palette?): Color {
    if (palette == null) return Color.Black
    
    val defaultColor = 0x000000
    
    val darkVibrant = palette.getDarkVibrantColor(defaultColor)
    if (darkVibrant != defaultColor) return Color(darkVibrant)
    
    val darkMuted = palette.getDarkMutedColor(defaultColor)
    if (darkMuted != defaultColor) return Color(darkMuted)
    
    val vibrant = palette.getVibrantColor(defaultColor)
    if (vibrant != defaultColor) return Color(vibrant)
    
    val muted = palette.getMutedColor(defaultColor)
    if (muted != defaultColor) return Color(muted)
    
    val lightVibrant = palette.getLightVibrantColor(defaultColor)
    if (lightVibrant != defaultColor) return Color(lightVibrant)
    
    val lightMuted = palette.getLightMutedColor(defaultColor)
    if (lightMuted != defaultColor) return Color(lightMuted)
    
    return Color.Black
}
