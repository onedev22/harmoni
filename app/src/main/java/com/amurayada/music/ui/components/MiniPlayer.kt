package com.amurayada.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import coil.compose.AsyncImage
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.Song

@Composable
fun MiniPlayer(
    currentSong: Song?,
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit,
    customBgUri: String? = null,
    isAmoledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return
    
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    // Use same styling as NavigationBar for consistency
    // If we have a custom background, use transparent surface and render blurred image manually
    val isCustomBg = customBgUri != null
    
    val titleColor = if (isCustomBg) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isCustomBg) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (isCustomBg) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isCustomBg) Modifier.drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawLine(
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else Modifier
            ),
        color = when {
            isCustomBg -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
            isAmoledMode -> androidx.compose.ui.graphics.Color.Black
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

        
            Column {
                // Progress bar at top - thin line
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpandClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Art
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp
                    ) {
                        AsyncImage(
                            model = currentSong.albumArtUri,
                            contentDescription = "Portada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Song Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = titleColor
                        )
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Controls - simple icon buttons like nav bar items
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState is PlaybackState.Playing) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (playbackState is PlaybackState.Playing) "Pausar" else "Reproducir",
                            tint = iconColor
                        )
                    }
                    
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            tint = iconColor
                        )
                    }
                }
            }
        }
    }
}
