package com.amurayada.music.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = application.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
    
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    private val _useDynamicColors = MutableStateFlow(prefs.getBoolean("dynamic_colors", true))
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()
    
    private val _sleepTimerMinutes = MutableStateFlow(prefs.getInt("sleep_timer", 0))
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()
    
    // Returns null for system theme (handled by isSystemInDarkTheme)
    private val _isDarkThemeOverride = MutableStateFlow<Boolean?>(
        when (prefs.getString("theme_mode", "system")) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    )
    val isDarkThemeOverride: StateFlow<Boolean?> = _isDarkThemeOverride.asStateFlow()
    
    // For compatibility - always returns a boolean
    val isDarkTheme: StateFlow<Boolean> = MutableStateFlow(
        _isDarkThemeOverride.value ?: false
    )
    
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        _isDarkThemeOverride.value = when (mode) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        prefs.edit().putString("theme_mode", mode).apply()
    }
    
    fun setDynamicColors(enabled: Boolean) {
        _useDynamicColors.value = enabled
        prefs.edit().putBoolean("dynamic_colors", enabled).apply()
    }
    
    private val _isAmoledMode = MutableStateFlow(prefs.getBoolean("amoled_mode", false))
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()
    
    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        prefs.edit().putInt("sleep_timer", minutes).apply()
    }
    
    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
        prefs.edit().putBoolean("amoled_mode", enabled).apply()
    }
    
    fun getThemeModeDisplay(): String {
        return when (_themeMode.value) {
            "light" -> "Claro"
            "dark" -> "Oscuro"
            else -> "Sistema"
        }
    }
}
