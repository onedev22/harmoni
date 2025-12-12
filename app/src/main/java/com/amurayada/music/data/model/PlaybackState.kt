package com.amurayada.music.data.model

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Loading : PlaybackState()
    data class Playing(val song: Song, val position: Long = 0) : PlaybackState()
    data class Paused(val song: Song, val position: Long = 0) : PlaybackState()
    data object Stopped : PlaybackState()
}

data class PlaybackMode(
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
