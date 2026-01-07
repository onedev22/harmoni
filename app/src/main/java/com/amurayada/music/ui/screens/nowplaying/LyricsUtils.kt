package com.amurayada.music.ui.screens.nowplaying

data class LyricLine(val timestamp: Long, val text: String)

fun parseLyrics(lyrics: String?): List<LyricLine> {
    if (lyrics.isNullOrBlank()) return emptyList()
    
    val regex = Regex("\\[(\\d{2})[.:](\\d{2})[.:](\\d{2,3})](.*)")
    val lines = mutableListOf<LyricLine>()
    
    lyrics.lines().forEach { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, ms, text) = match.destructured
            val minutes = min.toLong()
            val seconds = sec.toLong()
            // Handle 2 or 3 digit milliseconds
            val milliseconds = if (ms.length == 2) ms.toLong() * 10 else ms.toLong()
            
            val totalMillis = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
            lines.add(LyricLine(totalMillis, text.trim()))
        }
    }
    
    return lines.sortedBy { it.timestamp }
}

fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
