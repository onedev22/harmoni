package com.amurayada.music.data.repository

import com.amurayada.music.data.model.Song
import com.amurayada.music.data.newpipe.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor

class StreamRepositoryImpl : StreamRepository {

    init {
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(NewPipeDownloader())
        }
    }

    override suspend fun getStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            if (!song.path.startsWith("http")) return@withContext song.path

            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(song.path) as StreamExtractor
            extractor.fetchPage()
            
            val audioStreams = extractor.audioStreams
            if (audioStreams.isNotEmpty()) {
                // Priorizar M4A (MPEG_4) ya que es mejor para incrustar metadatos en esta app
                val best = audioStreams.sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { 
                    it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4 
                }.thenByDescending { it.averageBitrate }).firstOrNull()
                
                return@withContext best?.content
            }
            
            val videoStreams = extractor.videoStreams
            if (videoStreams.isNotEmpty()) {
                return@withContext videoStreams.firstOrNull()?.content
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateTitleSimilarity(songTitle: String, videoTitle: String): Double {
        val songWords = songTitle.lowercase().split(Regex("[^a-z0-9]")).filter { it.length > 2 }.toSet()
        if (songWords.isEmpty()) return 1.0 // Fallback si no hay palabras específicas
        
        val videoWords = videoTitle.lowercase().split(Regex("[^a-z0-9]")).toSet()
        val commonWords = songWords.intersect(videoWords)
        
        return commonWords.size.toDouble() / songWords.size.toDouble()
    }

    private suspend fun searchFallbackVideo(song: Song): Pair<String, Long>? = withContext(Dispatchers.IO) {
        try {
            val query = "${song.title} ${song.artist} official video"
            android.util.Log.d("StreamRepo", "Buscando video de respaldo para: $query")
            
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val items = searchExtractor.initialPage.items
            val bestVideoItem = items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .map { item ->
                    val name = item.name.lowercase()
                    val uploader = item.uploaderName?.lowercase() ?: ""
                    var score = 0
                    
                .map { item ->
                    val name = item.name.lowercase()
                    val uploader = item.uploaderName?.lowercase() ?: ""
                    var score = 0
                    
                    // 1. Similitud de Título
                    val similarity = calculateTitleSimilarity(song.title, item.name)
                    if (similarity < 0.5) score -= 150 
                    else score += (similarity * 100).toInt()

                    // 2. Oficialidad / Uploader (RELAJADO para colaboraciones)
                    val artistName = song.artist.lowercase()
                    val isOfficial = uploader.contains("official") || uploader.contains("vevo") || 
                                    uploader.contains(artistName)
                    
                    if (isOfficial) score += 50 // Bono por oficial, pero NO obligatorio

                    if (name.contains("official video")) score += 30
                    if (name.contains("music video")) score += 20
                    
                    // 3. Penalizaciones por Audio/Teaser
                    if (name.contains("audio")) score -= 100
                    if (name.contains("teaser") || name.contains("trailer") || 
                        name.contains("preview") || name.contains("snippet")) {
                        score -= 500 // Descalificar
                    }
                    
                    // 4. Coincidencia de Duración (Indiferente según solicitud de usuario)
                    // Ya no añadimos bono/penalización basado en similitud de duración
                    
                    // 5. PROTECCIÓN VIDEO "TOPIC" / ESTÁTICO (DESCALIFICACIÓN NUCLEAR)
                    val isTopic = uploader.contains("- topic") || name.contains("- topic")
                    val isProvided = name.contains("provided to youtube") || uploader.contains("youtube")
                    
                    if (isTopic || isProvided) score -= 1000 
                    
                }
                .filter { it.second > 40 } // Umbral más bajo ya que la oficialidad no es obligatoria
                .sortedByDescending { it.second }
                .firstOrNull()?.first

            if (bestVideoItem != null) {
                android.util.Log.d("StreamRepo", "Found fallback video: ${bestVideoItem.name} (${bestVideoItem.url})")
                val extractor = service.getStreamExtractor(bestVideoItem.url) as StreamExtractor
                extractor.fetchPage()
                
                val bestUrl = selectBestVideoStream(extractor)
                if (bestUrl != null) {
                    return@withContext Pair(bestUrl, bestVideoItem.duration)
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("StreamRepo", "Fallback search failed", e)
            null
        }
    }

    private fun selectBestVideoStream(extractor: StreamExtractor): String? {
        // Intentar streams muxed primero (contienen audio y video, sin post-procesamiento)
        val videoStreams = extractor.videoStreams
        if (videoStreams.isNotEmpty()) {
            val bestVideo = videoStreams
                .filter { it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4 }
                .filter { (it.resolution.replace("p", "").toIntOrNull() ?: 0) <= 720 }
                .maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
            
            if (bestVideo != null) return bestVideo.content
            return videoStreams.first().content
        }
        
        // Respaldo a solo video si es necesario (aunque usualmente queremos muxed para PlayerView)
        val videoOnlyStreams = extractor.videoOnlyStreams
        if (videoOnlyStreams.isNotEmpty()) {
            val bestVideoOnly = videoOnlyStreams
                .filter { it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4 }
                .filter { (it.resolution.replace("p", "").toIntOrNull() ?: 0) <= 720 }
                .maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
            
            if (bestVideoOnly != null) return bestVideoOnly.content
        }
        return null
    }

    override suspend fun getVideoStreamUrl(song: Song): Pair<String, Long>? = withContext(Dispatchers.IO) {
        try {
            if (!song.path.startsWith("http")) {
                android.util.Log.d("StreamRepo", "Archivo local, sin stream de video disponible")
                return@withContext null
            }

            android.util.Log.d("StreamRepo", "Obteniendo stream de video para: ${song.path}")

            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(song.path) as StreamExtractor
            extractor.fetchPage()
            
            val uploader = extractor.uploaderName ?: ""
            val title = extractor.name ?: ""
            if (uploader.endsWith("- Topic") || title.contains("Official Audio", ignoreCase = true)) {
                android.util.Log.d("StreamRepo", "Video es imagen estática (Topic: '$uploader', Titulo: '$title'). Intentando búsqueda de respaldo...")
                return@withContext searchFallbackVideo(song)
            }

            val result = selectBestVideoStream(extractor)
            if (result == null) {
                return@withContext searchFallbackVideo(song)
            }
            
            // Primario es bueno, retornar con duración
            Pair(result, extractor.length)
        } catch (e: Exception) {
            android.util.Log.e("StreamRepo", "Fallo al obtener stream de video, intentando respaldo", e)
            searchFallbackVideo(song)
        }
    }
}
