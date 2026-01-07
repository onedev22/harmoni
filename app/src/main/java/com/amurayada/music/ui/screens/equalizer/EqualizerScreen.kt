package com.amurayada.music.ui.screens.equalizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amurayada.music.ui.viewmodel.EqualizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel,
    onBackClick: () -> Unit
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val bandLevels by viewModel.bandLevels.collectAsState()
    val bandCount = remember { viewModel.getBandCount() }
    val levelRange = remember { viewModel.getLevelRange() }
    
    val bassBoost by viewModel.bassBoost.collectAsState()
    val virtualizer by viewModel.virtualizer.collectAsState()
    
    val presets = remember { viewModel.getPresets() }
    var selectedPreset by remember { mutableIntStateOf(-1) }
    
    // Glassy Background Color
    val glassyColor = Color.White.copy(alpha = 0.15f)
    
    Scaffold(
        containerColor = Color.Transparent, 
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Estudio de Sonido", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .height(36.dp)
                            .clickable { viewModel.toggleEnabled(!isEnabled) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                if (isEnabled) "ON" else "OFF",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Premium Monitor Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassyColor) // Semi-transparent white
            ) {
                // "Screen" shine effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(0f, 200f)
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.tertiary
                    
                    for (i in 0 until bandCount) {
                        val level = bandLevels[i] ?: 0
                        val normalizedLevel = (level - levelRange.first).toFloat() / (levelRange.second - levelRange.first)
                        val animatedHeight by animateFloatAsState(
                            targetValue = if (isEnabled) normalizedLevel.coerceIn(0.05f, 1f) else 0.05f,
                            animationSpec = tween(300),
                            label = "barHeight"
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(animatedHeight)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(secondary, primary)
                                    )
                                )
                        )
                    }
                }
            }
            
            // 2. Presets Chips
            if (presets.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(presets) { index, preset ->
                        val isSelected = selectedPreset == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else glassyColor,
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { 
                                    selectedPreset = index
                                    viewModel.usePreset(index)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // 3. Effects Section (Bass & Surround)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bass Boost
                EffectCard(
                    title = "Graves",
                    value = bassBoost,
                    max = 1000,
                    isEnabled = isEnabled,
                    onValueChange = { viewModel.setBassBoost(it) },
                    modifier = Modifier.weight(1f),
                    color = glassyColor
                )
                
                // Virtualizer
                EffectCard(
                    title = "Surround",
                    value = virtualizer,
                    max = 1000,
                    isEnabled = isEnabled,
                    onValueChange = { viewModel.setVirtualizer(it) },
                    modifier = Modifier.weight(1f),
                    color = glassyColor
                )
            }
            
            Spacer(Modifier.height(32.dp))

            // 4. Master Faders (Equalizer Bands)
            Text(
                "MEZCLADOR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, bottom = 16.dp)
            )

            // Horizontal scroll container for faders - Centered and Spaced Evenly
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                 Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Tighter spacing for better alignment feel
                ) {
                    for (i in 0 until bandCount) {
                        val level = bandLevels[i] ?: 0
                        val freq = viewModel.getBandFrequency(i)
                        
                        PremiumFader(
                            freq = formatFrequency(freq),
                            level = level,
                            minLevel = levelRange.first,
                            maxLevel = levelRange.second,
                            isEnabled = isEnabled,
                            onLevelChange = { 
                                selectedPreset = -1 
                                viewModel.setBandLevel(i, it) 
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun EffectCard(
    title: String,
    value: Int,
    max: Int,
    isEnabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        
        // Horizontal Slider for Effects
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
        Text(
            "${(value / 10)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PremiumFader(
    freq: String,
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    isEnabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp)
    ) {
        Text(
            text = "${if (level > 0) "+" else ""}${level / 100}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .height(220.dp) // Taller fader
                .width(48.dp),
            contentAlignment = Alignment.Center
        ) {
            PremiumVerticalSlider(
                value = level.toFloat(),
                min = minLevel.toFloat(),
                max = maxLevel.toFloat(),
                onValueChange = { onLevelChange(it.toInt()) },
                enabled = isEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = freq,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumVerticalSlider(
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.2f) // Glassy track
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val thumbShadowColor = Color.Black.copy(alpha = 0.2f)

    Canvas(
        modifier = modifier
            .pointerInput(enabled, min, max) {
                if (!enabled) return@pointerInput
                val totalHeight = size.height
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                         val range = max - min
                         val percent = 1f - (offset.y / totalHeight).coerceIn(0f, 1f)
                         val newValue = min + (range * percent)
                         onValueChange(newValue)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val range = max - min
                        val percent = 1f - (change.position.y / totalHeight).coerceIn(0f, 1f)
                        val newValue = min + (range * percent)
                        onValueChange(newValue)
                    }
                )
            }
            .pointerInput(enabled, min, max) {
                if (!enabled) return@pointerInput
                 val totalHeight = size.height
                 detectTapGestures(
                     onTap = { offset ->
                         val range = max - min
                         val percent = 1f - (offset.y / totalHeight).coerceIn(0f, 1f)
                         val newValue = min + (range * percent)
                         onValueChange(newValue)
                     }
                 )
            }
    ) {
        val width = size.width
        val height = size.height
        val trackWidth = 12.dp.toPx() // Thicker track
        val cornerRadius = trackWidth / 2
        
        // 1. Groove (Track Background)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(center.x - trackWidth / 2, 0f),
            size = Size(trackWidth, height),
            cornerRadius = CornerRadius(cornerRadius)
        )
        
        val fraction = (value - min) / (max - min)
        val thumbY = height * (1 - fraction)
        
        // 2. Active Fill (Gradient)
        if (enabled) {
            val fillHeight = height - thumbY
            if (fillHeight > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor,
                            primaryColor.copy(alpha = 0.6f)
                        ),
                        startY = thumbY,
                        endY = height
                    ),
                    topLeft = Offset(center.x - trackWidth / 2, thumbY),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }
        }

        // 3. Premium Thumb (Knob)
        val thumbRadius = 12.dp.toPx()
        val thumbCenter = Offset(center.x, thumbY.coerceIn(0f, height))

        if (enabled) {
            // Shadow
            drawCircle(
                color = thumbShadowColor,
                radius = thumbRadius + 2.dp.toPx(),
                center = thumbCenter + Offset(0f, 2.dp.toPx())
            )
            // Main Body (White)
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = thumbCenter
            )
            // Ring Border (Primary)
            drawCircle(
                color = primaryColor,
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 3.dp.toPx())
            )
        } else {
             drawCircle(
                color = disabledColor,
                radius = thumbRadius,
                center = thumbCenter
            )
        }
    }
}

private fun formatFrequency(freq: Int): String {
    return if (freq >= 1000 * 1000) {
        "${freq / (1000 * 1000)}k"
    } else if (freq >= 1000) {
        "${freq / 1000}k"
    } else {
        "$freq"
    }
}
