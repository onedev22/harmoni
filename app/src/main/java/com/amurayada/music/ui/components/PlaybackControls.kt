package com.amurayada.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.PlaybackMode
import com.amurayada.music.data.model.PlaybackState
import com.amurayada.music.data.model.RepeatMode

@Composable
fun PlaybackControls(
    playbackState: PlaybackState,
    playbackMode: PlaybackMode,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShuffleClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (playbackMode.isShuffleEnabled) accentColor else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(
            onClick = onSkipPreviousClick,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        
        FilledIconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accentColor
            )
        ) {
            Icon(
                imageVector = if (playbackState is PlaybackState.Playing) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = if (playbackState is PlaybackState.Playing) "Pausar" else "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        
        IconButton(
            onClick = onSkipNextClick,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        
        IconButton(
            onClick = onRepeatClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = when (playbackMode.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = "Repetir",
                tint = if (playbackMode.repeatMode != RepeatMode.OFF) accentColor else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
