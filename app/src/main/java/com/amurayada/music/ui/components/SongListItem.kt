package com.amurayada.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import com.amurayada.music.data.model.Song

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false
) {
    val backgroundColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
             if (index != null) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Album Art
        Box(modifier = Modifier.size(48.dp)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageRequest = remember(song.albumArtUri) {
                coil.request.ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .size(144)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (isCurrentSong && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerIndicator(
                        isAnimating = true,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxHeight = 24.dp,
                        minHeight = 8.dp,
                        barCount = 3,
                        barWidth = 4.dp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${formatDuration(song.duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
