package com.amurayada.music.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Manages YouTube Music authentication via cookies.
 * Uses encrypted SharedPreferences for secure storage.
 */
class YouTubeAuthManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "youtube_auth"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_VISITOR_DATA = "visitor_data"
        private const val KEY_DATA_SYNC_ID = "data_sync_id"
        
        // Essential cookies for authentication
        private val REQUIRED_COOKIES = listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "__Secure-3PAPISID")
        
        @Volatile
        private var INSTANCE: YouTubeAuthManager? = null
        
        fun getInstance(context: Context): YouTubeAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: YouTubeAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular prefs if encryption fails
            e.printStackTrace()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Save cookies extracted from WebView after successful login.
     * @param cookieString Full cookie string from CookieManager.getCookie()
     */
    fun saveCookies(cookieString: String) {
        prefs.edit().putString(KEY_COOKIES, cookieString).apply()
    }
    
    /**
     * Get stored cookies.
     */
    fun getCookies(): String? {
        return prefs.getString(KEY_COOKIES, null)
    }
    
    /**
     * Check if user is logged in (has valid cookies).
     */
    fun isLoggedIn(): Boolean {
        val cookies = getCookies() ?: return false
        // Check for essential auth cookies
        return REQUIRED_COOKIES.any { cookies.contains(it) }
    }
    
    /**
     * Clear all authentication data (logout).
     */
    fun logout() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Get Cookie header value for HTTP requests.
     */
    fun getCookieHeader(): String {
        return getCookies() ?: ""
    }
    
    /**
     * Generate SAPISIDHASH for authenticated API requests.
     * Format: SAPISIDHASH {timestamp}_{sha1(timestamp + " " + SAPISID + " " + origin)}
     */
    fun generateSapisidHash(origin: String = "https://music.youtube.com"): String? {
        val cookies = getCookies() ?: return null
        
        // Extract SAPISID from cookies
        val sapisid = extractCookieValue(cookies, "SAPISID") 
            ?: extractCookieValue(cookies, "__Secure-3PAPISID")
            ?: return null
        
        val timestamp = System.currentTimeMillis() / 1000
        val dataToHash = "$timestamp $sapisid $origin"
        
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val hashBytes = md.digest(dataToHash.toByteArray())
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_$hashHex"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Extract a specific cookie value from cookie string.
     */
    private fun extractCookieValue(cookies: String, name: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("$name=") }
            ?.substringAfter("=")
    }
    
    /**
     * Save visitor data for session continuity.
     */
    fun saveVisitorData(visitorData: String) {
        prefs.edit().putString(KEY_VISITOR_DATA, visitorData).apply()
    }
    
    /**
     * Get visitor data.
     */
    fun getVisitorData(): String? {
        return prefs.getString(KEY_VISITOR_DATA, null)
    }
    
    /**
     * Save data sync ID.
     */
    fun saveDataSyncId(dataSyncId: String) {
        prefs.edit().putString(KEY_DATA_SYNC_ID, dataSyncId).apply()
    }
    
    /**
     * Get data sync ID.
     */
    fun getDataSyncId(): String? {
        return prefs.getString(KEY_DATA_SYNC_ID, null)
    }
}
