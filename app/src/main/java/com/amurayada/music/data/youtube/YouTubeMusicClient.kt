package com.amurayada.music.data.youtube

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import java.util.TimeZone

/**
 * YouTube Music client configuration based on SimpMusic/WEB_REMIX client.
 * All values extracted from AppClient.kt in kotlinYtmusicScraper.
 */
object YouTubeMusicClient {
    const val CLIENT_NAME = "WEB_REMIX"
    const val CLIENT_VERSION = "1.20240819.01.00"
    const val INNER_TUBE_NAME = 67
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val REFERER = "https://music.youtube.com/"
    const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    
    // SharedPreferences for persistent storage
    private const val PREF_NAME = "ytm_client_prefs"
    private const val KEY_VISITOR_DATA = "visitor_data"
    private const val DEFAULT_VISITOR_DATA = "CgtXSVNLM0EwZzBGNCiZ"
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize with Android Context. Call this from Application.onCreate()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Visitor data for session tracking.
     * Persisted to SharedPreferences for consistent personalization.
     */
    var visitorData: String
        get() = prefs?.getString(KEY_VISITOR_DATA, DEFAULT_VISITOR_DATA) ?: DEFAULT_VISITOR_DATA
        set(value) {
            prefs?.edit()?.putString(KEY_VISITOR_DATA, value)?.apply()
        }

    fun savePreference(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun getPreference(key: String, default: String = ""): String {
        return prefs?.getString(key, default) ?: default
    }
    
    /**
     * Get current locale settings from device.
     * gl = Country code (CO, US, ES, etc.)
     * hl = Language code (es, en, etc.)
     */
    fun getLocale(): YouTubeLocale {
        val deviceLocale = Locale.getDefault()
        return YouTubeLocale(
            gl = deviceLocale.country.ifEmpty { "US" },
            hl = deviceLocale.language.ifEmpty { "en" }
        )
    }
    
    /**
     * Get UTC offset in minutes for the current timezone.
     */
    fun getUtcOffsetMinutes(): Int {
        return TimeZone.getDefault().rawOffset / 60000
    }
    
    /**
     * Build the context object for InnerTube API requests.
     */
    fun buildContext(locale: YouTubeLocale = getLocale()): String {
        return """
            {
                "client": {
                    "clientName": "$CLIENT_NAME",
                    "clientVersion": "$CLIENT_VERSION",
                    "clientScreen": "WATCH",
                    "hl": "${locale.hl}",
                    "gl": "${locale.gl}",
                    "utcOffsetMinutes": ${getUtcOffsetMinutes()},
                    "visitorData": "$visitorData"
                },
                "user": {
                    "enableSafetyMode": false,
                    "lockedSafetyMode": false
                }
            }
        """.trimIndent()
    }
    
    private var authManager: com.amurayada.music.data.auth.YouTubeAuthManager? = null
    
    /**
     * Set the auth manager for authenticated requests.
     */
    fun setAuthManager(manager: com.amurayada.music.data.auth.YouTubeAuthManager) {
        authManager = manager
    }
    
    /**
     * Check if user is logged in.
     */
    fun isLoggedIn(): Boolean {
        return authManager?.isLoggedIn() ?: false
    }
    
    /**
     * Get authentication headers for API requests.
     * Returns empty map if not logged in.
     */
    fun getAuthHeaders(): Map<String, String> {
        val manager = authManager ?: return emptyMap()
        if (!manager.isLoggedIn()) return emptyMap()
        
        val headers = mutableMapOf<String, String>()
        
        // Add cookies
        val cookies = manager.getCookieHeader()
        if (cookies.isNotEmpty()) {
            headers["Cookie"] = cookies
        }
        
        // Add SAPISIDHASH for authenticated endpoints
        val sapisidHash = manager.generateSapisidHash()
        if (sapisidHash != null) {
            headers["Authorization"] = sapisidHash
        }
        
        // Origin required for authenticated requests
        headers["Origin"] = "https://music.youtube.com"
        headers["X-Origin"] = "https://music.youtube.com"
        
        return headers
    }
}

/**
 * Locale settings for YouTube Music API.
 * @param gl Country code (e.g., "CO" for Colombia, "US" for United States)
 * @param hl Language code (e.g., "es" for Spanish, "en" for English)
 */
data class YouTubeLocale(
    val gl: String,
    val hl: String
)

