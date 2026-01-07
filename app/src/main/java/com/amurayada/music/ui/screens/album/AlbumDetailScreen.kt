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
import com.amurayada.music.ui.utils.extractDominantColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: Album?,
    songs: List<Song>,
    allAlbums: List<Album>,
    libraryVersion: Long = 0L,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onUpdateAlbum: (String, String, String, android.net.Uri?) -> Unit = { _, _, _, _ -> }, // Callback for updates
    onGetGenre: suspend (Long) -> String? = { null }, // Callback to fetch genre
    onDeleteSong: (Song) -> Unit = {}, // Callback for deletion
    onDownloadSong: (Song) -> Unit = {},
    onDownloadAlbum: (List<Song>) -> Unit = {},
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
    var gradientColors by remember { mutableStateOf(Color.Black to Color.Black) }
    
    // Genre State
    var currentGenre by remember { mutableStateOf("") }
    
    // Editor Sheet State
    var showEditorSheet by remember { mutableStateOf(false) }
    
    // Fetch genre when album changes
    LaunchedEffect(album.id) {
        val genre = onGetGenre(album.id)
        currentGenre = genre ?: ""
    }
    
    // Re-run palette generation when album art or library version changes
    LaunchedEffect(album.artworkUri, libraryVersion) {
        try {
            val request = ImageRequest.Builder(context)
                .data(album.artworkUri)
                .setParameter("v", libraryVersion) // Force reload if version changes
                .allowHardware(false)
                .build()
            val result = ImageLoader(context).execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let {
                Palette.from(it).generate { palette ->
                    gradientColors = com.amurayada.music.ui.utils.extractGradientColors(palette)
                }
            }
        } catch (e: Exception) {
            gradientColors = Color.Black to Color.Black
        }
    }
    
    if (showEditorSheet) {
        com.amurayada.music.ui.components.AlbumEditorSheet(
            album = album,
            initialGenre = currentGenre,
            onDismiss = { showEditorSheet = false },
            onSave = { title, artist, genre, imageUri ->
                onUpdateAlbum(title, artist, genre, imageUri)
                showEditorSheet = false
            }
        )
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Gradient: SimpMusic Style - Vertical dark to black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            gradientColors.first, // Full vibrance
                            Color(
                                red = gradientColors.first.red * 0.2f,
                                green = gradientColors.first.green * 0.2f,
                                blue = gradientColors.first.blue * 0.2f
                            ),
                            Color.Black,
                            Color.Black,
                            Color.Black
                        )
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
                    },
                    actions = {
                        // TODO: Album editing is disabled for now
                        // IconButton(onClick = { showEditorSheet = true }) {
                        //     Icon(
                        //         Icons.Rounded.Edit,
                        //         contentDescription = "Editar Álbum",
                        //         tint = Color.White
                        //     )
                        // }
                    }
                )
            }
        ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 180.dp)
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
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.artworkUri)
                            .setParameter("v", libraryVersion) // Force reload
                            .crossfade(true)
                            .build(),
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
                            containerColor = gradientColors.first
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

                    // Download Button (Only for Online/YouTube Albums)
                    if (album.path.contains("http") || album.path.contains("youtube")) {
                        FilledTonalButton(
                            onClick = { onDownloadAlbum(songs) },
                            modifier = Modifier.weight(0.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Descargar", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            
            // Songs list
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song_item" }
            ) { index, song ->
                var showMenu by remember { mutableStateOf(false) }
                
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    index = index + 1,
                    trailingContent = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Opciones",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Descargar") },
                                    onClick = {
                                        showMenu = false
                                        onDownloadSong(song)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
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
