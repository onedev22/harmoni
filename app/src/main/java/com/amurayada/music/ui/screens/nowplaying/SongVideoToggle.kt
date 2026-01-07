package com.amurayada.music.ui.screens.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

@Composable
fun SongVideoToggle(
    isVideoMode: Boolean,
    isVideoLoading: Boolean,
    canShowVideo: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // If we can show video, we are enabled.
    // If we cannot show video, the "Video" part is disabled.
    
    // Background of the pill with Glass Effect (Clear Content)
    Surface(
        color = Color.White.copy(alpha = 0.25f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp, 
            Color.White.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(2.dp)
                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song Option
            ToggleOption(
                text = "Canción",
                isSelected = !isVideoMode,
                isEnabled = true,
                onClick = { if (isVideoMode) onToggle() }
            )
            
            // Video Option
            ToggleOption(
                text = "Video",
                isSelected = isVideoMode,
                isEnabled = canShowVideo,
                onClick = { 
                    if (!isVideoMode && canShowVideo) onToggle() 
                }
            )
        }
    }
}

@Composable
private fun ToggleOption(
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color.White.copy(alpha = 0.45f) else Color.Transparent,
        label = "bg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) Color.White 
        else if (isEnabled) Color.White.copy(alpha = 0.7f) 
        else Color.White.copy(alpha = 0.15f), // Much darker for "blocked" look
        label = "content"
    )
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
