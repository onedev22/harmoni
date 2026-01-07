package com.amurayada.music.ui.screens.timecapsule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amurayada.music.data.model.Song
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsuleScreen(
    allSongs: List<Song>,
    playCounts: Map<Long, Int>,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val stats = remember(allSongs, playCounts) {
        calculateTimeCapsuleStats(allSongs, playCounts)
    }
    
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var selectedEra by remember { mutableStateOf("beginnings") }
    
    // Filter songs based on selected era
    val displayedSongs = remember(selectedEra, stats) {
        when (selectedEra) {
            "beginnings" -> stats.earlySongs.take(4)
            "peak" -> stats.topPlayedSongs.take(4)
            "current" -> stats.recentSongs.take(4)
            "discovery" -> stats.discoverySongs.take(4)
            else -> stats.previewSongs
        }
    }
    
    // Force standard UI to avoid layout issues with custom background image
    val isGlassy = false // MaterialTheme.colorScheme.background == Color.Transparent
    
    Scaffold(
        containerColor = Color.Transparent, // Keep transparent so the gradient header looks right, but content will be opaque
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 1. Header (Dynamic based on selectedEra)
            TimeCapsuleHeader(
                topPadding = paddingValues.calculateTopPadding(),
                selectedEra = selectedEra
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        if (isGlassy) Color.White.copy(alpha = 0.05f) 
                        else colorScheme.surface
                    )
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 2. Navigation Tabs
                EraTabs(selectedEra) { selectedEra = it }
                
                // 3. Stats Grid
                StatsGrid(stats)
                
                // 4. Timeline (Filtered by selectedEra)
                TimelineSection(stats, selectedEra, onSongClick)
                
                // 5. Preview Grid (Dynamic based on tab)
                PreviewGrid(
                    title = getEraTitle(selectedEra),
                    songs = displayedSongs, 
                    onSongClick = onSongClick
                )
                
                // 6. Actions
                ActionButtons(
                    onStartJourney = {
                        if (stats.earlySongs.isNotEmpty()) {
                            onSongClick(stats.earlySongs.first(), stats.earlySongs)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

fun getEraTitle(era: String): String {
    return when (era) {
        "beginnings" -> "Tus Inicios"
        "peak" -> "Época Dorada"
        "current" -> "Ahora Mismo"
        "discovery" -> "Descubrimientos"
        else -> "Previsualización"
    }
}

fun getEraSubtitle(era: String): String {
    return when (era) {
        "beginnings" -> "Donde todo comenzó"
        "peak" -> "Tu momento musical más intenso"
        "current" -> "Tu evolución actual"
        "discovery" -> "Nuevos horizontes musicales"
        else -> "Tu viaje musical a través del tiempo"
    }
}

@Composable
fun TimeCapsuleHeader(topPadding: androidx.compose.ui.unit.Dp, selectedEra: String) {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF7B61FF), // Primary from CSS
                        Color(0xFF4CC9F0)  // Secondary from CSS
                    )
                )
            )
    ) {
        // Decorative elements
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp)
                .size(200.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Capsule Icon - Clean version (no box)
            Icon(
                Icons.Rounded.Restore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(80.dp) // Larger icon
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "TIME CAPSULE",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Dynamic Subtitle based on Era
            Text(
                text = getEraSubtitle(selectedEra),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun EraTabs(selectedEra: String, onEraSelected: (String) -> Unit) {
    val eras = listOf(
        "beginnings" to "Tus Inicios",
        "peak" to "Época Dorada",
        "current" to "Ahora",
        "discovery" to "Descubrimientos"
    )
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(eras) { (id, label) ->
            val isSelected = selectedEra == id
            val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            
            Surface(
                onClick = { onEraSelected(id) },
                shape = RoundedCornerShape(50),
                color = backgroundColor,
                modifier = Modifier.height(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun StatsGrid(stats: TimeCapsuleStats) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Rounded.Timeline,
                value = stats.formattedDuration,
                label = "Tiempo total",
                color = Color(0xFF7B61FF),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Rounded.Diversity3,
                value = stats.totalArtists.toString(),
                label = "Artistas",
                color = Color(0xFF7209B7),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Rounded.LibraryMusic,
                value = stats.totalSongs.toString(),
                label = "Canciones",
                color = Color(0xFFF72585),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Rounded.EmojiObjects,
                value = "${stats.musicalMemory}%",
                label = "Memoria musical",
                color = Color(0xFF4AD66D),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.then(
            if (MaterialTheme.colorScheme.background == Color.Transparent) 
                Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)) 
            else Modifier
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color.Transparent) 
                Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (MaterialTheme.colorScheme.background == Color.Transparent) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), // Ensure column fills width for centering
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TimelineSection(stats: TimeCapsuleStats, selectedEra: String, onSongClick: (Song, List<Song>) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.HistoryEdu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tu línea de tiempo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter visible cards based on selectedEra
        
        // Early Era (First Song)
        if (selectedEra == "beginnings") {
            stats.firstAddedSong?.let { song ->
                val year = Calendar.getInstance().apply { timeInMillis = song.dateAdded * 1000 }.get(Calendar.YEAR)
                EraCard(
                    title = "El Comienzo",
                    subtitle = "Tu primera canción ($year)",
                    description = "Aquí empezó todo. La primera canción que agregaste a tu colección.",
                    stats = EraStats(stats.earlySongsCount, "Nostalgia"),
                    color = Color(0xFF4CC9F0),
                    song = song,
                    onSongClick = { onSongClick(song, listOf(song)) }
                )
            }
        }
        
        // Golden Era (Most played old song)
        if (selectedEra == "peak") {
            stats.mostPlayedOldSong?.let { song ->
                EraCard(
                    title = "Época Dorada",
                    subtitle = "Tu clásico favorito",
                    description = "Una de tus canciones más escuchadas de tus primeros tiempos.",
                    stats = EraStats(stats.earlySongs.size, "Clásico"),
                    color = Color(0xFFF72585),
                    song = song,
                    onSongClick = { onSongClick(song, listOf(song)) }
                )
            }
        }
        
        // Current Era (Most played recent)
        if (selectedEra == "current") {
            stats.mostPlayedRecentSong?.let { song ->
                EraCard(
                    title = "Ahora Mismo",
                    subtitle = "Tu obsesión actual",
                    description = "Lo que más has estado escuchando recientemente.",
                    stats = EraStats(stats.recentSongsCount, "Actual"),
                    color = Color(0xFF4AD66D),
                    song = song,
                    onSongClick = { onSongClick(song, listOf(song)) }
                )
            }
        }
        
        // Discovery Era
        if (selectedEra == "discovery") {
             EraCard(
                title = "Nuevos Horizontes",
                subtitle = "Por descubrir",
                description = "Explora canciones que quizás hayas olvidado o que están esperando ser escuchadas.",
                stats = EraStats(stats.discoverySongs.size, "Descubrir"),
                color = Color(0xFF7B61FF),
                song = stats.discoverySongs.firstOrNull(),
                onSongClick = { 
                    if (stats.discoverySongs.isNotEmpty()) {
                        onSongClick(stats.discoverySongs.first(), stats.discoverySongs) 
                    }
                }
            )
        }
    }
}

@Composable
fun EraCard(
    title: String,
    subtitle: String,
    description: String,
    stats: EraStats,
    color: Color,
    song: Song? = null,
    onSongClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color.Transparent) 
                Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (MaterialTheme.colorScheme.background == Color.Transparent) 
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp)) 
                else Modifier
            )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Colored strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Surface(
                        color = color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = stats.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (song != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Mini Song Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSongClick),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
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
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EraStatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PreviewGrid(title: String, songs: List<Song>, onSongClick: (Song, List<Song>) -> Unit) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Algunas joyas de esta colección",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (songs.isEmpty()) {
            Text(
                text = "No hay canciones suficientes para esta era.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Grid of items
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val chunked = songs.chunked(2)
                chunked.forEach { rowSongs ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowSongs.forEach { song ->
                            PreviewSongCard(
                                song = song,
                                modifier = Modifier.weight(1f),
                                onClick = { onSongClick(song, songs) }
                            )
                        }
                        if (rowSongs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewSongCard(song: Song, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${Calendar.getInstance().apply { timeInMillis = song.dateAdded * 1000 }.get(Calendar.YEAR)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActionButtons(onStartJourney: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onStartJourney,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7B61FF)
            )
        ) {
            Icon(Icons.Rounded.PlayCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Iniciar Viaje")
        }
    }
}

data class TimeCapsuleStats(
    val formattedDuration: String,
    val totalArtists: Int,
    val totalSongs: Int,
    val musicalMemory: Int,
    val earlySongsCount: Int,
    val recentSongsCount: Int,
    val previewSongs: List<Song>,
    val earlySongs: List<Song>,
    val recentSongs: List<Song>,
    val topPlayedSongs: List<Song>,
    val discoverySongs: List<Song>,
    val firstAddedSong: Song?,
    val mostPlayedOldSong: Song?,
    val mostPlayedRecentSong: Song?
)

data class EraStats(
    val count: Int,
    val badge: String
)

fun calculateTimeCapsuleStats(allSongs: List<Song>, playCounts: Map<Long, Int>): TimeCapsuleStats {
    var totalMillis = 0L
    
    allSongs.forEach { song ->
        totalMillis += song.duration
    }
    
    // Format duration
    val totalMinutes = (totalMillis / (1000 * 60)).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val formattedDuration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    
    // Artists: Normalize and count unique
    val totalArtists = allSongs.map { it.artist.trim() }
        .distinctBy { it.lowercase() }
        .count()
    
    // Musical Memory: % of library played at least once
    val playedSongsCount = allSongs.count { playCounts.containsKey(it.id) && playCounts[it.id]!! > 0 }
    val musicalMemory = if (allSongs.isNotEmpty()) {
        (playedSongsCount.toFloat() / allSongs.size * 100).toInt()
    } else {
        0
    }
    
    // Sort by date added
    val sortedSongs = allSongs.sortedBy { it.dateAdded }
    val earlySongs = sortedSongs.take(100)
    val recentSongs = sortedSongs.takeLast(100)
    
    // Top Played Songs (Peak Era)
    val topPlayedSongs = allSongs.sortedByDescending { playCounts[it.id] ?: 0 }.take(20)
    
    // Discovery Songs (Random mix)
    val discoverySongs = allSongs.shuffled().take(20)
    
    // Preview songs (default)
    val previewSongs = (earlySongs.take(2) + recentSongs.take(2)).shuffled()
    
    // Dynamic Timeline Songs
    val firstAddedSong = sortedSongs.firstOrNull()
    
    // Most played old song (from early songs)
    val mostPlayedOldSong = earlySongs.maxByOrNull { playCounts[it.id] ?: 0 }
    
    // Most played recent song (from recent songs)
    val mostPlayedRecentSong = recentSongs.maxByOrNull { playCounts[it.id] ?: 0 }
    
    return TimeCapsuleStats(
        formattedDuration = formattedDuration,
        totalArtists = totalArtists,
        totalSongs = allSongs.size,
        musicalMemory = musicalMemory,
        earlySongsCount = earlySongs.size,
        recentSongsCount = recentSongs.size,
        previewSongs = previewSongs,
        earlySongs = earlySongs,
        recentSongs = recentSongs,
        topPlayedSongs = topPlayedSongs,
        discoverySongs = discoverySongs,
        firstAddedSong = firstAddedSong,
        mostPlayedOldSong = mostPlayedOldSong,
        mostPlayedRecentSong = mostPlayedRecentSong
    )
}
