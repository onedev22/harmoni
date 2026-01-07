package com.amurayada.music.ui.screens.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    allSongs: List<Song>,
    playCounts: Map<Long, Int>,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    // Calculate stats
    val stats = remember(allSongs, playCounts) {
        calculateRecapStats(allSongs, playCounts)
    }
    
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    
    // Force standard UI to avoid layout issues with custom background image
    val isGlassy = false // MaterialTheme.colorScheme.background == Color.Transparent
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // Transparent TopBar for back button
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
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás")
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
            // 1. Header Section
            RecapHeader(paddingValues.calculateTopPadding())
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp) // Overlap with header
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        if (isGlassy) Color.White.copy(alpha = 0.05f) 
                        else colorScheme.surface
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // 2. Stats Grid
                StatsGrid(stats)
                
                // 3. Top Favorites Section
                TopFavoritesSection(stats.mostPlayedSongs.take(5), onSongClick)
                
                // 4. Chart Section (Top Artists)
                if (stats.topArtists.isNotEmpty()) {
                    TopArtistsChart(stats.topArtists)
                }
                
                // 5. Actions removed
                // ActionButtons()
            }
        }
    }
}

@Composable
fun RecapHeader(topPadding: androidx.compose.ui.unit.Dp) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Dynamic Date
    val calendar = Calendar.getInstance()
    val month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())?.uppercase() ?: "MES"
    val year = calendar.get(Calendar.YEAR)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colorScheme.primary,
                        colorScheme.tertiary
                    )
                )
            )
    ) {
        // Decorative circle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp)
                .size(200.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Month Badge
            Surface(
                color = colorScheme.primaryContainer,
                shape = RoundedCornerShape(100.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "$month $year", 
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Tu Recap\nMusical",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 40.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Un vistazo a tu música",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun StatsGrid(stats: RecapStats) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Rounded.Schedule,
                value = stats.totalMinutes.toString(),
                label = "Minutos escuchados",
                color = colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Rounded.People,
                value = stats.uniqueArtists.toString(),
                label = "Artistas diferentes",
                color = colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Rounded.LibraryMusic,
                value = stats.uniqueSongs.toString(),
                label = "Canciones únicas",
                color = colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Rounded.Explore,
                value = stats.newDiscoveries.toString(),
                label = "Nuevos descubrimientos",
                color = colorScheme.error,
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
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color.Transparent) 
                Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (MaterialTheme.colorScheme.background == Color.Transparent) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun TopFavoritesSection(topSongs: List<Song>, onSongClick: (Song, List<Song>) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tus favoritos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (topSongs.isEmpty()) {
            Text(
                text = "Escucha más música para ver tus favoritos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            topSongs.forEachIndexed { index, song ->
                TopSongItem(
                    rank = index + 1,
                    song = song,
                    onClick = { onSongClick(song, topSongs) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TopSongItem(rank: Int, song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank Badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Icon(
            Icons.Rounded.PlayCircle,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun TopArtistsChart(topArtists: List<Pair<String, Int>>) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Artistas más escuchados",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Find max for scaling
                val maxPlays = topArtists.maxOfOrNull { it.second } ?: 1
                
                topArtists.forEachIndexed { index, (artist, plays) ->
                    val progress = (plays.toFloat() / maxPlays).coerceIn(0f, 1f)
                    ArtistRowItem(
                        rank = index + 1,
                        artist = artist,
                        plays = plays,
                        progress = progress
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistRowItem(rank: Int, artist: String, plays: Int, progress: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Rank
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$plays",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Horizontal Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                )
            }
        }
    }
}

// ActionButtons removed as per user request

data class RecapStats(
    val totalMinutes: Int,
    val uniqueArtists: Int,
    val uniqueSongs: Int,
    val newDiscoveries: Int,
    val mostPlayedSongs: List<Song>,
    val topArtists: List<Pair<String, Int>>
)

fun calculateRecapStats(allSongs: List<Song>, playCounts: Map<Long, Int>): RecapStats {
    var totalMillis = 0L
    val artistCounts = mutableMapOf<String, Int>()
    val playedSongs = mutableListOf<Song>()
    
    // Filter songs that have play counts
    allSongs.forEach { song ->
        val count = playCounts[song.id] ?: 0
        if (count > 0) {
            totalMillis += song.duration * count
            artistCounts[song.artist] = (artistCounts[song.artist] ?: 0) + count
            playedSongs.add(song)
        }
    }
    
    val totalMinutes = (totalMillis / (1000 * 60)).toInt()
    
    // Calculate new discoveries (songs added in the last 30 days)
    val thirtyDaysAgo = System.currentTimeMillis() / 1000 - (30 * 24 * 60 * 60)
    val newDiscoveries = allSongs.count { it.dateAdded > thirtyDaysAgo }
    
    val mostPlayedSongs = playedSongs.sortedByDescending { playCounts[it.id] ?: 0 }.take(20)
    
    val topArtists = artistCounts.toList()
        .sortedByDescending { it.second }
        .take(5)
    
    return RecapStats(
        totalMinutes = totalMinutes,
        uniqueArtists = artistCounts.size,
        uniqueSongs = playedSongs.size,
        newDiscoveries = newDiscoveries,
        mostPlayedSongs = mostPlayedSongs,
        topArtists = topArtists
    )
}
