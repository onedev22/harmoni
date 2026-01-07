package com.amurayada.music.ui.screens.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentSong: Song,
    onDismissRequest: () -> Unit,
    onQueueItemClick: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onReorderQueue: (Int, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${queue.size} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            HorizontalDivider()
            
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "La cola está vacía",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val density = LocalDensity.current
                val itemHeight = 72.dp
                val itemHeightPx = with(density) { itemHeight.toPx() }
                
                val hapticFeedback = LocalHapticFeedback.current
                
                var draggingItem by remember { mutableStateOf<Song?>(null) }
                var draggingIndex by remember { mutableStateOf(-1) }
                var draggingItemOffset by remember { mutableStateOf(0f) }
                var lastSwappedIndex by remember { mutableStateOf(-1) }
                
                val listState = rememberLazyListState()
                
                LaunchedEffect(Unit) {
                    val currentIndex = queue.indexOfFirst { it.id == currentSong.id }
                    if (currentIndex >= 0) {
                        listState.scrollToItem(currentIndex)
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val containerHeight = maxHeight
                    val containerHeightPx = with(density) { containerHeight.toPx() }
                    
                    LaunchedEffect(draggingItem, draggingItemOffset) {
                        if (draggingItem != null) {
                            val scrollThreshold = 150f
                            val maxScrollSpeed = 30f
                            
                            while (draggingItem != null) {
                                var scrollDelta = 0f
                                if (draggingItemOffset < scrollThreshold) {
                                    val ratio = (scrollThreshold - draggingItemOffset) / scrollThreshold
                                    scrollDelta = -maxScrollSpeed * ratio
                                } else if (draggingItemOffset > containerHeightPx - itemHeightPx - scrollThreshold) {
                                    val ratio = (draggingItemOffset - (containerHeightPx - itemHeightPx - scrollThreshold)) / scrollThreshold
                                    scrollDelta = maxScrollSpeed * ratio
                                }
                                
                                if (scrollDelta != 0f) {
                                    listState.scrollBy(scrollDelta)
                                }
                                
                                val layoutInfo = listState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                val draggedCenter = draggingItemOffset + itemHeightPx / 2
                                
                                val targetItem = visibleItems.find { item ->
                                    draggedCenter >= item.offset && draggedCenter <= (item.offset + item.size)
                                }
                                
                                if (targetItem != null) {
                                    val targetIndex = targetItem.index.coerceIn(0, queue.lastIndex)
                                    if (targetIndex != draggingIndex && targetIndex >= 0 && targetIndex != lastSwappedIndex) {
                                        onReorderQueue(draggingIndex, targetIndex)
                                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        lastSwappedIndex = draggingIndex
                                        draggingIndex = targetIndex
                                    }
                                }
                                
                                withFrameNanos { }
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "queue_item" }
                        ) { index, song ->
                            val isPlaying = song.id == currentSong.id
                            val isDragging = index == draggingIndex
                            
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        onRemoveFromQueue(song)
                                        true
                                    } else false
                                }
                            )
                            
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) 
                                        MaterialTheme.colorScheme.errorContainer 
                                    else Color.Transparent
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                modifier = Modifier
                                    .zIndex(if (isDragging) 0f else 1f)
                                    .alpha(if (isDragging) 0f else 1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemHeight)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { 
                                            onQueueItemClick(song)
                                            onDismissRequest()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song.albumArtUri)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .size(144)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isPlaying) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    if (isPlaying) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Reproduciendo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.DragHandle,
                                            contentDescription = "Reordenar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggingItem = song
                                                            draggingIndex = index
                                                            lastSwappedIndex = -1
                                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                                            if (itemInfo != null) {
                                                                draggingItemOffset = itemInfo.offset.toFloat()
                                                            }
                                                        },
                                                        onDragEnd = { 
                                                            draggingItem = null
                                                            draggingIndex = -1
                                                        },
                                                        onDragCancel = { 
                                                            draggingItem = null
                                                            draggingIndex = -1
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            draggingItemOffset += dragAmount.y
                                                        }
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (draggingItem != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .graphicsLayer {
                                    translationY = draggingItemOffset
                                    shadowElevation = 8.dp.toPx()
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    alpha = 0.95f
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(draggingItem!!.albumArtUri)
                                        .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .size(144)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = draggingItem!!.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = draggingItem!!.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
