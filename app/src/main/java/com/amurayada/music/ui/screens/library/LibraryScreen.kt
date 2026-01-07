package com.amurayada.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Genre
import com.amurayada.music.data.model.Playlist
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.launch

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, DOWNLOADS
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    genres: List<Genre>,
    downloadedSongs: List<Song> = emptyList(),
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onGenreClick: (Genre) -> Unit = {},
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Playlist, Song) -> Unit = { _, _ -> },
    onCreatePlaylist: () -> Unit = {},
    onDeleteSong: (Song) -> Unit = {},
    onDeleteDownload: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { LibraryTab.entries.size }
    )
    val scope = rememberCoroutineScope()
    
    // Sync Pager -> External State only (One-way binding to avoid loops)
    LaunchedEffect(pagerState.currentPage) {
        onTabSelected(LibraryTab.entries[pagerState.currentPage])
    }
    
    // Note: We ignore incoming selectedTab changes to avoid jitter, 
    // assuming this screen controls the tab state primarily.
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    "Biblioteca",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (MaterialTheme.colorScheme.background == Color.Transparent) Color.Transparent else MaterialTheme.colorScheme.surface
            )
        )
        
        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = if (MaterialTheme.colorScheme.background == Color.Transparent) Color.Transparent else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 16.dp,
            divider = {}
        ) {
            LibraryTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = when (tab) {
                                LibraryTab.SONGS -> "Canciones"
                                LibraryTab.ALBUMS -> "Álbumes"
                                LibraryTab.ARTISTS -> "Artistas"
                                LibraryTab.DOWNLOADS -> "Descargas"
                            },
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        // Content Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.SONGS -> {
                    SongsListScreen(
                        songs = songs,
                        playlists = playlists,
                        onSongClick = { song -> onSongClick(song, songs) },
                        onPlayAll = { 
                            if (songs.isNotEmpty()) {
                                onSongClick(songs.first(), songs)
                            }
                        },
                        onShuffle = {
                            if (songs.isNotEmpty()) {
                                val shuffled = songs.shuffled()
                                onSongClick(shuffled.first(), shuffled)
                            }
                        },
                        onAddToPlaylist = onAddToPlaylist,
                        onCreatePlaylist = onCreatePlaylist,
                        onDeleteSong = onDeleteSong,
                        currentSong = currentSong,
                        isPlaying = isPlaying
                    )
                }
                LibraryTab.ALBUMS -> {
                    AlbumsGridScreen(
                        albums = albums,
                        onAlbumClick = onAlbumClick
                    )
                }
                LibraryTab.ARTISTS -> {
                    ArtistsListScreen(
                        artists = artists,
                        onArtistClick = onArtistClick
                    )
                }
                LibraryTab.DOWNLOADS -> {
                    DownloadsListScreen(
                        downloadedSongs = downloadedSongs,
                        onSongClick = { song -> onSongClick(song, downloadedSongs) },
                        onDeleteDownload = onDeleteDownload,
                        playlists = playlists,
                        onAddToPlaylist = onAddToPlaylist,
                        onCreatePlaylist = onCreatePlaylist
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsListScreen(
    downloadedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onDeleteDownload: (Song) -> Unit,
    playlists: List<Playlist>,
    onAddToPlaylist: (Playlist, Song) -> Unit,
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (downloadedSongs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "No hay descargas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Las canciones descargadas aparecerán aquí",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        SongsListScreen(
            songs = downloadedSongs,
            playlists = playlists,
            onSongClick = onSongClick,
            onPlayAll = {
                if (downloadedSongs.isNotEmpty()) {
                    onSongClick(downloadedSongs.first())
                }
            },
            onShuffle = {
                if (downloadedSongs.isNotEmpty()) {
                    val shuffled = downloadedSongs.shuffled()
                    onSongClick(shuffled.first())
                }
            },
            onAddToPlaylist = onAddToPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onDeleteSong = onDeleteDownload,
            modifier = modifier
        )
    }
}

@Composable
fun OnlinePlaylistsScreen(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "No se encontraron playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inicia sesión en YouTube Music para ver tus playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(playlists.size) { index ->
                val playlist = playlists[index]
                // Using existing card or create a simple one
                // Let's use a simple detailed card here
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .clickable { onPlaylistClick(playlist) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Artwork
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (playlist.artworkUri != null) {
                                CoilImage(
                                    data = playlist.artworkUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                        
                        // Info
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!playlist.author.isNullOrEmpty()) {
                                Text(
                                    text = playlist.author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// Helper for Coil Image if not imported
@Composable
fun CoilImage(
    data: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit
) {
    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(data)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
