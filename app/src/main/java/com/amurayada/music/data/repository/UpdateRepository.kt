package com.amurayada.music.data.repository

data class ReleaseInfo(
    val versionName: String,
    val downloadUrl: String,
    val body: String
)

interface UpdateRepository {
    suspend fun getLatestRelease(): Result<ReleaseInfo>
}
