package com.amurayada.music.ui.screens.library

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song

enum class LibraryTab { SONGS, ALBUMS, ARTISTS, GENRES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    genres: List<com.amurayada.music.data.model.Genre>,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onGenreClick: (com.amurayada.music.data.model.Genre) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    currentSong: Song? = null,
    isPlaying: Boolean = false
) {
    // Removed local state: var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Biblioteca",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when(selectedTab) {
                            LibraryTab.SONGS -> "${songs.size} canciones"
                            LibraryTab.ALBUMS -> "${albums.size} álbumes"
                            LibraryTab.ARTISTS -> "${artists.size} artistas"
                            LibraryTab.GENRES -> "${genres.size} géneros"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                }
            }
        )
        
        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == LibraryTab.SONGS,
                onClick = { onTabSelected(LibraryTab.SONGS) },
                text = { Text("Canciones") }
            )
            Tab(
                selected = selectedTab == LibraryTab.ALBUMS,
                onClick = { onTabSelected(LibraryTab.ALBUMS) },
                text = { Text("Álbumes") }
            )
            Tab(
                selected = selectedTab == LibraryTab.ARTISTS,
                onClick = { onTabSelected(LibraryTab.ARTISTS) },
                text = { Text("Artistas") }
            )
            Tab(
                selected = selectedTab == LibraryTab.GENRES,
                onClick = { onTabSelected(LibraryTab.GENRES) },
                text = { Text("Géneros") }
            )
        }
        
        // Content with HorizontalPager
        val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
        
        // Sync TabRow with Pager
        LaunchedEffect(selectedTab) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
        
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
            if (!pagerState.isScrollInProgress) {
                onTabSelected(LibraryTab.entries[pagerState.currentPage])
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.SONGS -> SongsListScreen(
                    songs = songs,
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
                    currentSong = currentSong,
                    isPlaying = isPlaying
                )
                
                LibraryTab.ALBUMS -> AlbumsGridScreen(
                    albums = albums,
                    onAlbumClick = onAlbumClick
                )
                
                LibraryTab.ARTISTS -> ArtistsListScreen(
                    artists = artists,
                    onArtistClick = onArtistClick
                )
                
                LibraryTab.GENRES -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(genres) { genre ->
                        ListItem(
                            headlineContent = { Text(genre.name) },
                            supportingContent = { Text("${genre.songCount} canciones") },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Category,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onGenreClick(genre) }
                        )
                    }
                }
            }
        }
    }
}
