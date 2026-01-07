package com.amurayada.music.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UpdateRepositoryImpl(private val client: OkHttpClient) : UpdateRepository {

    override suspend fun getLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/harmony-music/Harmoni/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Hubo un error al conectar con el servidor: ${response.code}"))

                val json = JSONObject(response.body?.string() ?: "")
                val versionName = json.getString("tag_name").replace("v", "")
                val body = json.getString("body")
                
                // Buscar el primer asset que sea un APK
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (downloadUrl.isEmpty()) {
                    // Fallback a la página de release si no hay APK directo
                    downloadUrl = json.getString("html_url")
                }

                Result.success(ReleaseInfo(versionName, downloadUrl, body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
