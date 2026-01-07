package com.amurayada.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val dateCreated: Long = System.currentTimeMillis(),
    // Online / Extended properties
    val remoteId: String? = null, // BrowseId for online playlists
    val artworkUri: String? = null,
    val songCount: Int = 0,
    val author: String? = null
)
