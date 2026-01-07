package com.amurayada.music.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.Song
import com.amurayada.music.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Transparent) 
                        androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (MaterialTheme.colorScheme.background == androidx.compose.ui.graphics.Color.Transparent) 
                        androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (downloadedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Downloaded songs will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(downloadedSongs) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song, downloadedSongs) },
                        trailingContent = {
                            IconButton(onClick = { onDeleteClick(song.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete download"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
