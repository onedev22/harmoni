package com.amurayada.music.data.repository

import com.amurayada.music.data.model.Song

interface StreamRepository {
    suspend fun getStreamUrl(song: Song): String?
    suspend fun getVideoStreamUrl(song: Song): Pair<String, Long>? // URL and duration in seconds
}
