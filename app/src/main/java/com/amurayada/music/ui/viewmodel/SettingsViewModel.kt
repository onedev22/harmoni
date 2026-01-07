package com.amurayada.music.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amurayada.music.data.repository.ReleaseInfo
import com.amurayada.music.data.repository.UpdateRepository
import com.amurayada.music.data.repository.UpdateRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * ViewModel encargado de la gestión de configuraciones globales de la aplicación,
 * persistencia de preferencias de usuario y el sistema de actualizaciones.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    // Almacenamiento persistente mediante SharedPreferences
    private val prefs = application.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    // Cliente HTTP compartido para consultas de red
    private val client = OkHttpClient()
    
    // Repositorio para la gestión de versiones desde GitHub
    private val updateRepository: UpdateRepository = UpdateRepositoryImpl(client)

    // Versión actual configurada en el proyecto
    val CURRENT_VERSION = "1.1"

    // --- Estados de Configuración ---
    
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    private val _useDynamicColors = MutableStateFlow(prefs.getBoolean("dynamic_colors", true))
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()
    
    private val _sleepTimerMinutes = MutableStateFlow(prefs.getInt("sleep_timer", 0))
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    // --- Personalización Visual ---
    
    private val _customPrimaryColor = MutableStateFlow<Int?>(
        if (prefs.contains("custom_primary_color")) prefs.getInt("custom_primary_color", -1) else null
    )
    val customPrimaryColor: StateFlow<Int?> = _customPrimaryColor.asStateFlow()

    private val _customBackgroundImageUri = MutableStateFlow<String?>(prefs.getString("custom_bg_uri", null))
    val customBackgroundImageUri: StateFlow<String?> = _customBackgroundImageUri.asStateFlow()

    private val _playerBackgroundType = MutableStateFlow(prefs.getString("player_bg_type", "auto") ?: "auto")
    val playerBackgroundType: StateFlow<String> = _playerBackgroundType.asStateFlow()

    private val _playerBackgroundColor = MutableStateFlow<Int?>(
        if (prefs.contains("player_bg_color")) prefs.getInt("player_bg_color", -1) else null
    )
    val playerBackgroundColor: StateFlow<Int?> = _playerBackgroundColor.asStateFlow()

    private val _playerAlbumArtScale = MutableStateFlow(prefs.getFloat("player_art_scale", 1.0f))
    val playerAlbumArtScale: StateFlow<Float> = _playerAlbumArtScale.asStateFlow()
    
    // Control manual del tema oscuro (null indica seguir el sistema)
    private val _isDarkThemeOverride = MutableStateFlow<Boolean?>(
        when (prefs.getString("theme_mode", "system")) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    )
    val isDarkThemeOverride: StateFlow<Boolean?> = _isDarkThemeOverride.asStateFlow()
    
    val isDarkTheme: StateFlow<Boolean> = MutableStateFlow(
        _isDarkThemeOverride.value ?: false
    )

    // --- Sistema de Actualizaciones ---
    
    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Checking : UpdateStatus()
        data class UpdateAvailable(val release: ReleaseInfo) : UpdateStatus()
        object UpToDate : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    var latestRelease by mutableStateOf<ReleaseInfo?>(null)
        private set

    /**
     * Consulta la API de GitHub para verificar si existe una nueva versión del APK.
     * Compara semánticamente la versión remota con [CURRENT_VERSION].
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            val result = updateRepository.getLatestRelease()
            
            result.onSuccess { release ->
                latestRelease = release
                // Verificación simple de versión (suponiendo formato X.Y)
                if (isNewerVersion(CURRENT_VERSION, release.versionName)) {
                    _updateStatus.value = UpdateStatus.UpdateAvailable(release)
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
            }.onFailure { error ->
                _updateStatus.value = UpdateStatus.Error(error.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Lógica de comparación de versiones.
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toInt() }
            val latestParts = latest.split(".").map { it.toInt() }
            
            for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            false
        } catch (e: Exception) {
            latest != current // Fallback a comparación de strings
        }
    }
    
    // --- Métodos de Setter con Persistencia ---

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

    fun setCustomPrimaryColor(color: Int?) {
        _customPrimaryColor.value = color
        if (color != null) {
            prefs.edit().putInt("custom_primary_color", color).apply()
        } else {
            prefs.edit().remove("custom_primary_color").apply()
        }
    }

    fun setCustomBackgroundImage(uri: String?) {
        _customBackgroundImageUri.value = uri
        if (uri != null) {
            prefs.edit().putString("custom_bg_uri", uri).apply()
        } else {
            prefs.edit().remove("custom_bg_uri").apply()
        }
    }
    
    fun setPlayerBackgroundType(type: String) {
        _playerBackgroundType.value = type
        prefs.edit().putString("player_bg_type", type).apply()
    }

    fun setPlayerBackgroundColor(color: Int) {
        _playerBackgroundColor.value = color
        prefs.edit().putInt("player_bg_color", color).apply()
    }

    fun setPlayerAlbumArtScale(scale: Float) {
        _playerAlbumArtScale.value = scale
        prefs.edit().putFloat("player_art_scale", scale).apply()
    }

    /**
     * Copia una imagen seleccionada por el usuario al almacenamiento interno
     * para garantizar persistencia y evitar problemas de permisos de URI.
     */
    fun copyImageToInternalStorage(context: Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "custom_bg_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(context.filesDir, fileName)
            val outputStream = java.io.FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
        prefs.edit().putBoolean("amoled_mode", enabled).apply()
    }

    private val _onlineMode = MutableStateFlow(prefs.getBoolean("online_mode", false))
    val onlineMode: StateFlow<Boolean> = _onlineMode.asStateFlow()

    fun setOnlineMode(enabled: Boolean) {
        _onlineMode.value = enabled
        prefs.edit().putBoolean("online_mode", enabled).apply()
    }

    private val _applyBackgroundToPlayer = MutableStateFlow(prefs.getBoolean("apply_bg_to_player", false))
    val applyBackgroundToPlayer: StateFlow<Boolean> = _applyBackgroundToPlayer.asStateFlow()

    fun setApplyBackgroundToPlayer(enabled: Boolean) {
        _applyBackgroundToPlayer.value = enabled
        prefs.edit().putBoolean("apply_bg_to_player", enabled).apply()
    }

    private val _isCanvasEnabled = MutableStateFlow(prefs.getBoolean("is_canvas_enabled", false))
    val isCanvasEnabled: StateFlow<Boolean> = _isCanvasEnabled.asStateFlow()

    fun setCanvasEnabled(enabled: Boolean) {
        _isCanvasEnabled.value = enabled
        prefs.edit().putBoolean("is_canvas_enabled", enabled).apply()
    }
    
    init {
    }
    
    fun getThemeModeDisplay(): String {
        return when (_themeMode.value) {
            "light" -> "Claro"
            "dark" -> "Oscuro"
            else -> "Sistema"
        }
    }

    /**
     * Restaura los valores de personalización visual por defecto.
     */
    fun resetPersonalization() {
        _customPrimaryColor.value = null
        _customBackgroundImageUri.value = null
        _playerBackgroundType.value = "auto"
        _playerBackgroundColor.value = null
        _playerAlbumArtScale.value = 1.0f
        
        prefs.edit().apply {
            remove("custom_primary_color")
            remove("custom_bg_uri")
            putString("player_bg_type", "auto")
            remove("player_bg_color")
            putFloat("player_art_scale", 1.0f)
            apply()
        }
    }
}
