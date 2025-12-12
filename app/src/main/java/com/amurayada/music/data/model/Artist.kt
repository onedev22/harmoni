package com.amurayada.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int = 0,
    val songCount: Int = 0
)
