package com.amurayada.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int
)
