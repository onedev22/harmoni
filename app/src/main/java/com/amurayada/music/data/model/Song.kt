package com.amurayada.music.data.model

import android.net.Uri

import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val albumArtUri: Uri?,
    val path: String,
    val dateAdded: Long,
    val albumId: Long = 0
)
