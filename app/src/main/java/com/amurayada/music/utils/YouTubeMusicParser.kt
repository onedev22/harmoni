package com.amurayada.music.utils

/**
 * Utility functions for parsing YouTube Music titles
 */
object YouTubeMusicParser {
    
    /**
     * Parse a YouTube Music title to extract artist and song name
     * Handles formats like:
     * - "Artist - Song Title (Official Video)"
     * - "Song Title by Artist"
     * - "Artist: Song Title"
     * - "Song Title - Artist - Topic"
     */
    fun parseTitle(fullTitle: String): Pair<String, String> {
        var title = fullTitle
        var artist = "Unknown"
        
        // Remove common suffixes
        title = title
            .replace(Regex("\\s*\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Audio\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Lyric.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*- Topic$", RegexOption.IGNORE_CASE), "")
            .trim()
        
        // Try to extract artist and title
        when {
            // Format: "Artist - Song Title"
            title.contains(" - ") -> {
                val parts = title.split(" - ", limit = 2)
                if (parts.size == 2) {
                    artist = parts[0].trim()
                    title = parts[1].trim()
                }
            }
            // Format: "Song Title by Artist"
            title.contains(" by ", ignoreCase = true) -> {
                val parts = title.split(Regex("\\s+by\\s+", RegexOption.IGNORE_CASE), limit = 2)
                if (parts.size == 2) {
                    title = parts[0].trim()
                    artist = parts[1].trim()
                }
            }
            // Format: "Artist: Song Title"
            title.contains(": ") -> {
                val parts = title.split(": ", limit = 2)
                if (parts.size == 2) {
                    artist = parts[0].trim()
                    title = parts[1].trim()
                }
            }
            else -> {
                // If no delimiter found, we can't determine artist from title
                // Return "Unknown" only if we really can't find anything, 
                // but caller should check if artist is "Unknown" and use fallback
            }
        }
        
        return Pair(artist, title)
    }
    
    /**
     * Clean a song title for lyrics search
     */
    fun cleanTitleForLyrics(title: String): String {
        return title
            .replace(Regex("\\s*\\(.*?\\)"), "") // Remove anything in parentheses
            .replace(Regex("\\s*\\[.*?\\]"), "") // Remove anything in brackets
            .replace(Regex("\\s*feat\\..*", RegexOption.IGNORE_CASE), "") // Remove featuring
            .replace(Regex("\\s*ft\\..*", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
