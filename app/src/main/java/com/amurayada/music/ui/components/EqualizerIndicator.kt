package com.amurayada.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(barCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "equalizer_$index")
            val duration = 400 + (index * 150) // Staggered duration
            
            val height by if (isAnimating) {
                infiniteTransition.animateValue(
                    initialValue = minHeight,
                    targetValue = maxHeight,
                    typeConverter = Dp.VectorConverter,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 100)
                    ),
                    label = "height"
                )
            } else {
                // Static height when paused (middle height)
                remember { mutableStateOf(minHeight + (maxHeight - minHeight) / 3) }
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
