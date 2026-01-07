package com.amurayada.music.utils

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object AudioEffectsManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled = _isEnabled.asStateFlow()
    
    private val _bandLevels = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val bandLevels = _bandLevels.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()
    
    var audioSessionId: Int = 0
        set(value) {
            if (field != value) {
                field = value
                if (value != 0) {
                    initEffects(value)
                }
            }
        }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean("enabled", false)
        _bassBoostStrength.value = prefs.getInt("bass_boost", 0)
        _virtualizerStrength.value = prefs.getInt("virtualizer", 0)
        
        val levelsJson = prefs.getString("levels", null)
        if (levelsJson != null) {
            val levelsMap = mutableMapOf<Int, Int>()
            val json = JSONObject(levelsJson)
            json.keys().forEach { key ->
                levelsMap[key.toInt()] = json.getInt(key)
            }
            _bandLevels.value = levelsMap
        }
    }

    private fun initEffects(sessionId: Int) {
        try {
            release()
            
            // Equalizer
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _isEnabled.value
                _bandLevels.value.forEach { (band, level) ->
                    try {
                        setBandLevel(band.toShort(), level.toShort())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Bass Boost
            bassBoost = android.media.audiofx.BassBoost(0, sessionId).apply {
                enabled = _isEnabled.value
                try {
                    if (strengthSupported) {
                        setStrength(_bassBoostStrength.value.toShort())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Virtualizer
            virtualizer = android.media.audiofx.Virtualizer(0, sessionId).apply {
                enabled = _isEnabled.value
                try {
                    if (strengthSupported) {
                        setStrength(_virtualizerStrength.value.toShort())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEnabled(enabled: Boolean, context: Context) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        
        context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
    }

    fun setBandLevel(band: Int, level: Int, context: Context) {
        val current = _bandLevels.value.toMutableMap()
        current[band] = level
        _bandLevels.value = current
        
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Persist
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k.toString(), v) }
        context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            .edit().putString("levels", json.toString()).apply()
    }

    fun setBassBoostStrength(strength: Int, context: Context) {
        _bassBoostStrength.value = strength
        try {
            bassBoost?.setStrength(strength.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            .edit().putInt("bass_boost", strength).apply()
    }

    fun setVirtualizerStrength(strength: Int, context: Context) {
        _virtualizerStrength.value = strength
        try {
            virtualizer?.setStrength(strength.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            .edit().putInt("virtualizer", strength).apply()
    }

    fun getBandFrequency(band: Int): Int {
        return try {
            equalizer?.getCenterFreq(band.toShort()) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getBandCount(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 5
        } catch (e: Exception) {
            5
        }
    }

    fun getLevelRange(): Pair<Int, Int> {
        return try {
            val range = equalizer?.bandLevelRange
            if (range != null && range.size >= 2) {
                range[0].toInt() to range[1].toInt()
            } else {
                -1500 to 1500
            }
        } catch (e: Exception) {
            -1500 to 1500
        }
    }

    fun getPresets(): List<String> {
        val count = equalizer?.numberOfPresets?.toInt() ?: 0
        return (0 until count).map { equalizer?.getPresetName(it.toShort()) ?: "Preset $it" }
    }

    fun usePreset(presetIndex: Int, context: Context) {
        try {
            equalizer?.usePreset(presetIndex.toShort())
            
            // Sync our bandLevels flow
            val count = getBandCount()
            val newLevels = mutableMapOf<Int, Int>()
            for (i in 0 until count) {
                newLevels[i] = equalizer?.getBandLevel(i.toShort())?.toInt() ?: 0
            }
            _bandLevels.value = newLevels
            
            // Persist
            val json = JSONObject()
            newLevels.forEach { (k, v) -> json.put(k.toString(), v) }
            context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
                .edit().putString("levels", json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun release() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
        virtualizer?.release()
        virtualizer = null
    }
}
