package com.amurayada.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.amurayada.music.utils.AudioEffectsManager
import kotlinx.coroutines.flow.asStateFlow

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {
    val isEnabled = AudioEffectsManager.isEnabled
    val bandLevels = AudioEffectsManager.bandLevels
    val bassBoost = AudioEffectsManager.bassBoostStrength
    val virtualizer = AudioEffectsManager.virtualizerStrength

    fun toggleEnabled(enabled: Boolean) {
        AudioEffectsManager.setEnabled(enabled, getApplication())
    }

    fun setBandLevel(band: Int, level: Int) {
        AudioEffectsManager.setBandLevel(band, level, getApplication())
    }
    
    fun setBassBoost(strength: Int) {
        AudioEffectsManager.setBassBoostStrength(strength, getApplication())
    }
    
    fun setVirtualizer(strength: Int) {
        AudioEffectsManager.setVirtualizerStrength(strength, getApplication())
    }

    fun getBandFrequency(band: Int) = AudioEffectsManager.getBandFrequency(band)
    fun getBandCount() = AudioEffectsManager.getBandCount()
    fun getLevelRange() = AudioEffectsManager.getLevelRange()

    fun getPresets() = AudioEffectsManager.getPresets()
    fun usePreset(index: Int) {
        AudioEffectsManager.usePreset(index, getApplication())
    }
}
