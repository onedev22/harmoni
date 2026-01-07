package com.amurayada.music.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.amurayada.music.data.database.DownloadDao
import com.amurayada.music.data.database.DownloadEntity
import com.amurayada.music.data.model.DownloadProgress
import com.amurayada.music.data.model.DownloadStatus
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val streamRepository: StreamRepository
) : DownloadRepository {
    
    private val activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    
    override suspend fun downloadSong(song: Song): Flow<DownloadProgress> = flow {
        try {
            // Emit queued status
            val queuedProgress = DownloadProgress(
                songId = song.id,
                progress = 0f,
                status = DownloadStatus.QUEUED
            )
            emit(queuedProgress)
            updateActiveDownload(song.id, queuedProgress)
            
            // (Directory creation handled dynamically below)
            
            // Sanitize filename
            val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            val filename = "$sanitizedTitle.m4a"
            
            // Determine Output Stream and Path based on Preferences
            val prefs = context.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
            val downloadMode = prefs.getString("download_path_mode", "default")
            val customUriString = prefs.getString("download_path_uri", null)
            
            val (outputStream, savedPath) = if (downloadMode == "custom" && !customUriString.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(customUriString)
                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    
                    if (docFile != null && docFile.canWrite()) {
                        // Check if file exists to avoid duplicates or overwrite
                        val existingFile = docFile.findFile(filename)
                        val targetFile = existingFile ?: docFile.createFile("audio/mp4", filename)
                        
                        if (targetFile != null) {
                            val stream = context.contentResolver.openOutputStream(targetFile.uri) 
                                ?: throw Exception("Cannot open output stream for URI: ${targetFile.uri}")
                            Pair(stream, targetFile.uri.toString())
                        } else {
                            throw Exception("Failed to create file in custom directory")
                        }
                    } else {
                        throw Exception("Cannot write to custom directory")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DownloadRepo", "Error using custom path, falling back to default", e)
                    // Fallback to default
                    val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Downloads")
                    if (!downloadDir.exists()) downloadDir.mkdirs()
                    val file = File(downloadDir, filename)
                    Pair(java.io.FileOutputStream(file), file.absolutePath)
                }
            } else {
                // Default Path
                val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Downloads")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, filename)
                Pair(java.io.FileOutputStream(file), file.absolutePath)
            }
            
            // Configure youtubedl request
            // Download with progress tracking using NewPipe Extractor
            withContext(Dispatchers.IO) {
                android.util.Log.d("DownloadRepo", "Iniciando descarga de: ${song.path}")
                
                // Obtener URL del stream usando NewPipe
                // Extraer video ID del path de múltiples formas
                val videoUrl = when {
                    // Si ya es una URL completa de YouTube
                    song.path.startsWith("http") && (song.path.contains("youtube.com") || song.path.contains("youtu.be")) -> song.path
                    // Si el path contiene el video ID en formato watch?v=
                    song.path.contains("watch?v=") -> song.path
                    // Si el path es solo un video ID (11 caracteres alfanuméricos con - y _)
                    song.path.matches(Regex("^[a-zA-Z0-9_-]{11}$")) -> "https://music.youtube.com/watch?v=${song.path}"
                    // Intentar extraer video ID de cualquier URL que lo contenga
                    else -> {
                        val videoIdRegex = Regex("(?:v=|/)([a-zA-Z0-9_-]{11})(?:[&?/]|$)")
                        val match = videoIdRegex.find(song.path)
                        if (match != null) {
                            "https://music.youtube.com/watch?v=${match.groupValues[1]}"
                        } else {
                            // Último recurso: si el path parece un ID válido aunque sea más corto/largo
                            if (song.path.length in 8..15 && song.path.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                                "https://music.youtube.com/watch?v=${song.path}"
                            } else {
                                android.util.Log.e("DownloadRepo", "No se pudo extraer video ID del path: ${song.path}")
                                throw Exception("No se pudo obtener video ID. Path original: ${song.path}")
                            }
                        }
                    }
                }
                
                android.util.Log.d("DownloadRepo", "URL construida para descarga: $videoUrl")
                
                val streamUrl = streamRepository.getStreamUrl(song.copy(path = videoUrl))
                    ?: throw Exception("No se pudo obtener enlace de descarga. URL construida: $videoUrl, Path original: ${song.path}")
                
                android.util.Log.d("DownloadRepo", "Stream URL obtenida: ${streamUrl.take(50)}...")
                
                // Descargar archivo usando HttpURLConnection con headers optimizados
                val url = java.net.URL(streamUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                // Headers para parecer un navegador real y evitar throttling
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.setRequestProperty("Accept", "audio/webm,audio/ogg,audio/wav,audio/*;q=0.9,application/ogg;q=0.7,video/*;q=0.6,*/*;q=0.5")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
                connection.setRequestProperty("Accept-Encoding", "identity") // Sin compresión para streaming
                connection.setRequestProperty("Connection", "keep-alive")
                connection.setRequestProperty("Sec-Fetch-Dest", "audio")
                connection.setRequestProperty("Sec-Fetch-Mode", "no-cors")
                connection.setRequestProperty("Sec-Fetch-Site", "cross-site")
                connection.setRequestProperty("Range", "bytes=0-") // Solicitar todo el archivo
                connection.connectTimeout = 30000
                connection.readTimeout = 120000  // 2 minutos timeout
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                
                val responseCode = connection.responseCode
                android.util.Log.d("DownloadRepo", "HTTP Response Code: $responseCode")
                
                if (responseCode != java.net.HttpURLConnection.HTTP_OK && responseCode != 206) {
                    throw Exception("HTTP Error: $responseCode - ${connection.responseMessage}")
                }
                
                val totalBytes = connection.contentLength.toLong()
                android.util.Log.d("DownloadRepo", "Tamaño del archivo: $totalBytes bytes")
                
                // Usar BufferedInputStream con buffer grande para mejor rendimiento
                java.io.BufferedInputStream(connection.inputStream, 1024 * 1024).use { input ->
                    java.io.BufferedOutputStream(outputStream, 1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)  // Buffer de 1MB (optimizado)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastLogTime = System.currentTimeMillis()
                        
                        android.util.Log.d("DownloadRepo", "Comenzando a escribir archivo...")
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            
                            // Log progress cada 2 segundos
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime > 2000) {
                                val percent = if (totalBytes > 0) (totalRead * 100 / totalBytes) else 0
                                android.util.Log.d("DownloadRepo", "Progreso: $percent% ($totalRead / $totalBytes bytes)")
                                lastLogTime = now
                            }
                            
                            val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                            val downloadProgress = DownloadProgress(
                                songId = song.id,
                                progress = progress,
                                status = DownloadStatus.DOWNLOADING
                            )
                            updateActiveDownload(song.id, downloadProgress)
                        }
                    }
                }
                
                android.util.Log.d("DownloadRepo", "Descarga completada: $savedPath")
            }
            
            // Download album art if available
            val localAlbumArtPath = downloadAlbumArt(song, savedPath)
            
            // Write metadata to the downloaded file
            writeMetadataToFile(song, savedPath, localAlbumArtPath)
            
            // Trigger MediaScanner for local files OR resolved SAF paths
            val scannablePath = if (savedPath.startsWith("content://")) {
                resolveSafToPhysicalPath(savedPath)
            } else {
                savedPath
            }
            
            if (scannablePath != null) {
                android.util.Log.d("DownloadRepo", "Scanning file: $scannablePath")
                MediaScannerConnection.scanFile(context, arrayOf(scannablePath), null, null)
            }
            
            // Fix fileSize for SAF
            val actualFileSize = if (savedPath.startsWith("content://")) {
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse(savedPath), "r")?.use { 
                        it.statSize 
                    } ?: 0L
                } catch (e: Exception) { 0L }
            } else {
                File(savedPath).length()
            }

            // Save to database
            val downloadEntity = DownloadEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                youtubeUrl = song.path,
                localPath = savedPath,
                thumbnailUrl = localAlbumArtPath ?: song.albumArtUri?.toString(),
                fileSize = actualFileSize
            )
            downloadDao.insertDownload(downloadEntity)
            
            // Emit completed status
            val completedProgress = DownloadProgress(
                songId = song.id,
                progress = 1f,
                status = DownloadStatus.COMPLETED,
                totalBytes = actualFileSize
            )
            emit(completedProgress)
            removeActiveDownload(song.id)
            
        } catch (e: Exception) {
            e.printStackTrace()
            val failedProgress = DownloadProgress(
                songId = song.id,
                progress = 0f,
                status = DownloadStatus.FAILED,
                error = e.message
            )
            emit(failedProgress)
            removeActiveDownload(song.id)
        }
    }
    
    override suspend fun cancelDownload(songId: Long) {
        // Update status to cancelled
        val cancelledProgress = DownloadProgress(
            songId = songId,
            progress = 0f,
            status = DownloadStatus.CANCELLED
        )
        updateActiveDownload(songId, cancelledProgress)
        removeActiveDownload(songId)
    }
    
    override suspend fun getDownloadedSongs(): List<Song> = withContext(Dispatchers.IO) {
        downloadDao.getAllDownloads().first().map { entity ->
            Song(
                id = entity.songId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                albumArtUri = entity.thumbnailUrl?.let { Uri.parse(it) },
                path = entity.localPath ?: entity.youtubeUrl,
                dateAdded = entity.downloadedAt,
                albumId = entity.album.hashCode().toLong()
            )
        }
    }
    
    override fun observeDownloadedSongs(): Flow<List<Song>> {
        return downloadDao.getAllDownloads().map { entities ->
            entities.map { entity ->
                Song(
                    id = entity.songId,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    duration = entity.duration,
                    albumArtUri = entity.thumbnailUrl?.let { Uri.parse(it) },
                    path = entity.localPath ?: entity.youtubeUrl,
                    dateAdded = entity.downloadedAt,
                    albumId = entity.album.hashCode().toLong()
                )
            }
        }
    }
    
    override suspend fun deleteDownload(songId: Long): Unit = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownload(songId)
        download?.let {
            // Delete file
            it.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            // Delete from database
            downloadDao.deleteDownloadById(songId)
        }
    }
    
    override suspend fun isDownloaded(songId: Long): Boolean = withContext(Dispatchers.IO) {
        downloadDao.isDownloaded(songId)
    }
    
    override fun getDownloadProgress(songId: Long): Flow<DownloadProgress?> {
        return activeDownloads.map { it[songId] }
    }
    
    override fun getActiveDownloads(): Flow<List<DownloadProgress>> {
        return activeDownloads.map { it.values.toList() }
    }
    
    private fun updateActiveDownload(songId: Long, progress: DownloadProgress) {
        activeDownloads.value = activeDownloads.value + (songId to progress)
    }
    
    private fun removeActiveDownload(songId: Long) {
        activeDownloads.value = activeDownloads.value - songId
    }
    
    /**
     * Downloads album art and saves it locally next to the song file
     * Returns the local path to the downloaded art, or null if it fails
     */
    private suspend fun downloadAlbumArt(song: Song, songPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val albumArtUrl = song.albumArtUri?.toString() ?: return@withContext null
            
            // Determine the directory and filename for the album art
            val artFilename = "${song.title.replace(Regex("[^a-zA-Z0-9\\s-]"), "")}_cover.jpg"
            
            val artPath = if (songPath.startsWith("content://")) {
                // For SAF URIs, save in app's internal directory
                val artDir = File(context.filesDir, "album_art")
                if (!artDir.exists()) artDir.mkdirs()
                File(artDir, artFilename).absolutePath
            } else {
                // Save next to the song file
                val songFile = File(songPath)
                val artFile = File(songFile.parentFile, artFilename)
                artFile.absolutePath
            }
            
            // Download the image
            val url = java.net.URL(albumArtUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.doInput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val input = connection.inputStream
                val output = java.io.FileOutputStream(artPath)
                
                input.use { inputStream ->
                    output.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                android.util.Log.d("DownloadRepo", "Album art downloaded: $artPath")
                artPath
            } else {
                android.util.Log.w("DownloadRepo", "Failed to download album art: HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Error downloading album art", e)
            null
        }
    }
    
    /**
     * Writes ID3 metadata to the downloaded MP4 file
     */
    private suspend fun writeMetadataToFile(song: Song, filePath: String, albumArtPath: String?) = withContext(Dispatchers.IO) {
        val isSaf = filePath.startsWith("content://")
        val tempFile = File(context.cacheDir, "temp_metadata_${System.currentTimeMillis()}.m4a")
        
        try {
            if (isSaf) {
                android.util.Log.d("DownloadRepo", "Handling SAF metadata via temp file")
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            val fileToTag = if (isSaf) tempFile else File(filePath)
            if (!fileToTag.exists()) {
                android.util.Log.w("DownloadRepo", "File for tagging doesn't exist: ${fileToTag.absolutePath}")
                return@withContext
            }
            
            // Fallback for "Unknown" artist
            var finalArtist = song.artist
            val unknownStrings = listOf("Unknown", "Artista Desconocido", "Unknown Artist", "Varios Artistas")
            if (finalArtist in unknownStrings) {
                // Heuristic: If title contains common separators, extract first part
                val separators = listOf(" - ", " | ", " / ", " : ")
                for (sep in separators) {
                    if (song.title.contains(sep)) {
                        finalArtist = song.title.split(sep).first().trim()
                        break
                    }
                }
            }

            // Use JAudioTagger to write metadata
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(fileToTag)
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Set basic metadata
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, song.title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, finalArtist)
            if (!song.album.isNullOrEmpty()) {
                tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, song.album)
            }
            
            // Add album art if available
            albumArtPath?.let { artPath ->
                val artFile = File(artPath)
                if (artFile.exists()) {
                    try {
                        val artwork = org.jaudiotagger.tag.images.ArtworkFactory.createArtworkFromFile(artFile)
                        tag.deleteArtworkField() // Clear existing to avoid duplicates
                        tag.setField(artwork)
                        android.util.Log.d("DownloadRepo", "Album art embedded in metadata")
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadRepo", "Failed to create artwork: ${e.message}")
                    }
                }
            }
            
            // Save the file
            audioFile.commit()
            
            // If SAF, write back to original URI
            if (isSaf) {
                val uri = Uri.parse(filePath)
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.d("DownloadRepo", "SAF metadata written back to URI")
            } else {
                android.util.Log.d("DownloadRepo", "Metadata written to local file: $filePath")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Error writing metadata", e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override suspend fun validateDownloads() {
        withContext(Dispatchers.IO) {
            try {
                val entities = downloadDao.getAllDownloads().first()
                for (entity in entities) {
                    val path = entity.localPath ?: continue
                    if (!fileExists(path)) {
                        android.util.Log.i("DownloadRepo", "🗑️ Removing ghost download (file not found): ${entity.title}")
                        downloadDao.deleteDownloadById(entity.songId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Error validating downloads", e)
            }
        }
    }

    private fun fileExists(path: String): Boolean {
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                docFile?.exists() == true
            } else {
                File(path).exists()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Best-effort SAF to physical path resolution.
     * Only works for "primary" (internal storage) URIs commonly used in Music folders.
     */
    private fun resolveSafToPhysicalPath(uriString: String): String? {
        try {
            val uri = Uri.parse(uriString)
            if (uri.authority == "com.android.externalstorage.documents") {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    val path = split[1]
                    return "${android.os.Environment.getExternalStorageDirectory()}/$path"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Error resolving SAF path: ${e.message}")
        }
        return null
    }
}
