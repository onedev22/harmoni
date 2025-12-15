package com.amurayada.music.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Loading : PlaybackState()
    @Immutable
    data class Playing(val song: Song, val position: Long = 0) : PlaybackState()
    @Immutable
    data class Paused(val song: Song, val position: Long = 0) : PlaybackState()
    data object Stopped : PlaybackState()
}

@Immutable
data class PlaybackMode(
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

@Stable
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
