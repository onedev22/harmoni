package com.amurayada.music.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Calendar

class HeadphoneUsageMonitor(private val context: Context, private val onPauseRequest: () -> Unit) {

    companion object {
        const val ACTION_SHOW_WARNING_60 = "com.amurayada.music.ACTION_SHOW_WARNING_60"
        const val ACTION_SHOW_WARNING_75 = "com.amurayada.music.ACTION_SHOW_WARNING_75"
        const val ACTION_SHOW_WARNING_90 = "com.amurayada.music.ACTION_SHOW_WARNING_90"
        
        // Thresholds
        private const val TIME_60_MIN = 60 * 60 * 1000L
        private const val TIME_75_MIN = 75 * 60 * 1000L
        private const val TIME_90_MIN = 90 * 60 * 1000L
        
        // For testing, uncomment these:
        // private const val TIME_60_MIN = 10 * 1000L
        // private const val TIME_75_MIN = 20 * 1000L
        // private const val TIME_90_MIN = 30 * 1000L

        private const val SESSION_RESET_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes pause resets session
        
        private const val PREFS_NAME = "headphone_usage_prefs"
        private const val KEY_DAILY_CANCELS = "daily_cancels"
        private const val KEY_LAST_CANCEL_DATE = "last_cancel_date"
        const val MAX_DAILY_CANCELS = 3
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isHeadphoneConnected = false
    private var isPlaying = false
    
    // Session tracking
    private var sessionStartTime = 0L
    private var accumulatedTime = 0L
    private var lastPauseTime = 0L
    
    // State to avoid repeating warnings for the same threshold in one session
    private var warning60Shown = false
    private var warning75Shown = false
    private var warning90Shown = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && isHeadphoneConnected) {
                updateSession()
                handler.postDelayed(this, 1000) // Check every second
            }
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                updateHeadphoneState(state == 1 || isBluetoothHeadsetConnected())
            } else if (intent.action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
                updateHeadphoneState(isWiredHeadsetConnected() || isBluetoothHeadsetConnected())
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(headsetReceiver, filter)
        
        // Initial check
        updateHeadphoneState(isWiredHeadsetConnected() || isBluetoothHeadsetConnected())
    }

    fun onIsPlayingChanged(playing: Boolean) {
        if (isPlaying == playing) return
        
        isPlaying = playing
        if (isPlaying) {
            // Resuming
            if (isHeadphoneConnected) {
                val now = System.currentTimeMillis()
                if (lastPauseTime > 0 && (now - lastPauseTime) > SESSION_RESET_TIMEOUT_MS) {
                    resetSession()
                }
                sessionStartTime = now
                handler.post(tickRunnable)
            }
        } else {
            // Pausing
            if (isHeadphoneConnected) {
                accumulatedTime += System.currentTimeMillis() - sessionStartTime
                lastPauseTime = System.currentTimeMillis()
                handler.removeCallbacks(tickRunnable)
            }
        }
    }

    private fun updateHeadphoneState(connected: Boolean) {
        if (isHeadphoneConnected != connected) {
            isHeadphoneConnected = connected
            Log.d("HeadphoneMonitor", "Headphone connected: $connected")
            
            if (!connected) {
                // Disconnected -> Reset everything
                resetSession()
                handler.removeCallbacks(tickRunnable)
            } else {
                // Connected
                if (isPlaying) {
                    sessionStartTime = System.currentTimeMillis()
                    handler.post(tickRunnable)
                }
            }
        }
    }
    
    private fun resetSession() {
        accumulatedTime = 0
        lastPauseTime = 0
        warning60Shown = false
        warning75Shown = false
        warning90Shown = false
        Log.d("HeadphoneMonitor", "Session reset")
    }

    private fun updateSession() {
        val currentSessionDuration = System.currentTimeMillis() - sessionStartTime
        val totalDuration = accumulatedTime + currentSessionDuration
        
        if (totalDuration >= TIME_90_MIN && !warning90Shown) {
            warning90Shown = true
            triggerWarning(ACTION_SHOW_WARNING_90)
            // Auto-pause is handled by UI countdown or immediate pause request if needed
            // But user said "Music will pause in 10 seconds". UI should handle the countdown and call pause.
        } else if (totalDuration >= TIME_75_MIN && !warning75Shown) {
            warning75Shown = true
            triggerWarning(ACTION_SHOW_WARNING_75)
        } else if (totalDuration >= TIME_60_MIN && !warning60Shown) {
            warning60Shown = true
            triggerWarning(ACTION_SHOW_WARNING_60)
        }
    }

    private fun triggerWarning(action: String) {
        Log.d("HeadphoneMonitor", "Triggering warning: $action")
        val intent = Intent(action)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    // Daily Limit Logic
    fun getDailyCancels(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getLong(KEY_LAST_CANCEL_DATE, 0)
        val today = getTodayStart()
        
        if (lastDate != today) {
            // New day, reset
            prefs.edit()
                .putLong(KEY_LAST_CANCEL_DATE, today)
                .putInt(KEY_DAILY_CANCELS, 0)
                .apply()
            return 0
        }
        
        return prefs.getInt(KEY_DAILY_CANCELS, 0)
    }
    
    fun incrementDailyCancel() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getDailyCancels() // This also handles day reset
        prefs.edit().putInt(KEY_DAILY_CANCELS, current + 1).apply()
    }
    
    private fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun isWiredHeadsetConnected(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isWiredHeadsetOn
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isBluetoothA2dpOn
    }

    fun release() {
        try {
            context.unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        handler.removeCallbacksAndMessages(null)
    }
}
