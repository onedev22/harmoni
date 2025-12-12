package com.amurayada.music.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object MusicWidgetState {
    val titleKey = stringPreferencesKey("title")
    val artistKey = stringPreferencesKey("artist")
    val isPlayingKey = booleanPreferencesKey("is_playing")
    val isFavoriteKey = booleanPreferencesKey("is_favorite")
    val coverUriKey = stringPreferencesKey("cover_uri")
    
    const val DEFAULT_TITLE = "Harmony"
    const val DEFAULT_ARTIST = "Ondedev+"
}
