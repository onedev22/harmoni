package com.amurayada.music.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

fun extractDominantColor(palette: Palette?): Color {
    return extractGradientColors(palette).first
}

fun extractGradientColors(palette: Palette?): Pair<Color, Color> {
    if (palette == null) return Color.Black to Color.Black
    
    // First priority: vibrantSwatch (if it exists, it's usually the best choice)
    palette.vibrantSwatch?.let {
        val color = Color(it.rgb)
        return color to color
    }
    
    // Gather remaining swatches
    val swatches = listOfNotNull(
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.mutedSwatch,
        palette.lightMutedSwatch,
        palette.darkMutedSwatch
    )
    
    if (swatches.isEmpty()) return Color.Black to Color.Black
    
    // Calculate vibrancy score: high saturation + medium lightness preference
    val bestSwatch = swatches.maxByOrNull { swatch ->
        val saturation = swatch.hsl[1]  // 0.0 to 1.0
        val lightness = swatch.hsl[2]   // 0.0 to 1.0
        
        // Penalize very light (pastel) and very dark colors
        val lightnessPenalty = when {
            lightness > 0.7f -> 0.3f  // Heavy pastel penalty
            lightness < 0.3f -> 0.5f  // Dark color penalty
            else -> 1.0f              // Sweet spot (0.3-0.7)
        }
        
        saturation * lightnessPenalty
    } ?: swatches.first()
    
    val color = Color(bestSwatch.rgb)
    return color to color
}

fun Color.darken(factor: Float): Color {
    return Color(
        red = this.red * factor,
        green = this.green * factor,
        blue = this.blue * factor,
        alpha = this.alpha
    )
}
