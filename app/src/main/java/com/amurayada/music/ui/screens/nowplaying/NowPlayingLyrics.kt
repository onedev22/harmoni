package com.amurayada.music.ui.screens.nowplaying

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SyncedLyricsView(
    lyrics: String?,
    lyricsSource: String?,
    currentPosition: Long,
    gradientColor: Color,
    onClose: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedLyrics by remember(lyrics) { mutableStateOf(lyrics ?: "") }
    val parsedLyrics = remember(lyrics) { parseLyrics(lyrics) }
    val listState = rememberLazyListState()
    
    // Auto-scroll logic (only when not editing)
    LaunchedEffect(currentPosition, parsedLyrics, isEditing) {
        if (parsedLyrics.isNotEmpty() && !isEditing) {
            val currentIndex = parsedLyrics.indexOfLast { it.timestamp <= currentPosition }
            if (currentIndex >= 0) {
                listState.animateScrollToItem(currentIndex, scrollOffset = -200)
            }
        }
    }

    // Gesture State
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > kotlin.math.abs(offsetY)) {
                            // Horizontal Swipe
                            if (kotlin.math.abs(offsetX) > 100) {
                                if (offsetX > 0) onSkipPrevious() else onSkipNext()
                            }
                        } else {
                            // Vertical Swipe
                            if (offsetY > 100) {
                                onClose()
                            }
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(gradientColor, Color.Black, Color.Black),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Block clicks */ }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with status bar padding
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Text(
                    text = if (isEditing) "Editar Letra" else "Letra",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { 
                    if (isEditing) {
                        onSaveLyrics(editedLyrics)
                        isEditing = false
                    } else {
                        isEditing = true
                    }
                }) {
                    Icon(
                        if (isEditing) Icons.Rounded.Check else Icons.Rounded.Edit, 
                        contentDescription = if (isEditing) "Guardar" else "Editar", 
                        tint = if (isEditing) Color(0xFF4CAF50) else Color.White
                    )
                }
            }
            
            if (isEditing) {
                // Edit mode - show text field
                OutlinedTextField(
                    value = editedLyrics,
                    onValueChange = { editedLyrics = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White
                    ),
                    placeholder = {
                        Text(
                            "Pega la letra aquí...\n\nFormato sincronizado:\n[00:12.34] Primera línea\n[00:15.67] Segunda línea\n\nO letra sin sincronizar:\nPrimera línea\nSegunda línea",
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                )
            } else {
                // View mode
                if (parsedLyrics.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Lyrics, 
                                contentDescription = null, 
                                tint = Color.White.copy(alpha = 0.15f), 
                                modifier = Modifier.size(100.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No hay letra disponible",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Premium Action Chip Style Button
                            Surface(
                                onClick = { isEditing = true },
                                shape = RoundedCornerShape(50.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    Color.White.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Agregar letra", 
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 100.dp, horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(parsedLyrics) { index, line ->
                            val isCurrent = if (index == parsedLyrics.lastIndex) {
                                currentPosition >= line.timestamp
                            } else {
                                currentPosition >= line.timestamp && currentPosition < parsedLyrics[index + 1].timestamp
                            }
                            
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .scale(if (isCurrent) 1.1f else 1f)
                                    .animateContentSize()
                            )
                        }
                        
                        // Attribution
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = if (lyricsSource == "LRCLIB") "Letras de LRCLIB" else "SimpMusic lyrics",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
