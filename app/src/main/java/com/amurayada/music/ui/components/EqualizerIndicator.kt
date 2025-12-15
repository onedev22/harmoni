package com.amurayada.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

@Composable
fun EqualizerIndicator(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp,
    minHeight: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    // Single shared animation (0f to 1f) instead of 3 separate animations
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shared_phase"
    )
    
    val heightRange = maxHeight - minHeight
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(barCount) { index ->
            // Calculate height based on shared phase + offset
            val offset = index * 0.33f
            val normalizedPhase = ((phase + offset) % 1f)
            
            val height = if (isAnimating) {
                // Simple sine wave calculation (no animation overhead)
                val waveValue = sin(normalizedPhase * PI * 2).toFloat().absoluteValue
                minHeight + (heightRange * waveValue)
            } else {
                minHeight + heightRange / 3
            }

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height)
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
}
