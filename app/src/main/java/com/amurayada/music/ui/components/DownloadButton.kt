package com.amurayada.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.amurayada.music.data.model.DownloadProgress
import com.amurayada.music.data.model.DownloadStatus

@Composable
fun DownloadButton(
    isDownloaded: Boolean,
    downloadProgress: DownloadProgress?,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "download")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    when {
        isDownloaded -> {
            IconButton(onClick = onDeleteClick, modifier = modifier) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        downloadProgress?.status == DownloadStatus.DOWNLOADING -> {
            IconButton(onClick = {}, modifier = modifier, enabled = false) {
                Icon(
                    imageVector = Icons.Rounded.Downloading,
                    contentDescription = "Downloading",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
        else -> {
            IconButton(onClick = onDownloadClick, modifier = modifier) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download"
                )
            }
        }
    }
}
