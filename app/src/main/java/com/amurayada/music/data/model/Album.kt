package com.amurayada.music.data.model

import android.net.Uri

import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val artworkUri: Uri?,
    val year: Int = 0,
    val songCount: Int = 0
)
