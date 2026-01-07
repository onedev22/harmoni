package com.amurayada.music.data.source

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.StreamRepository
import kotlinx.coroutines.runBlocking

/**
 * A [DataSource] that resolves YouTube URLs to actual stream URLs just-in-time.
 * This prevents "expired URL" errors and "infinite skip loops" by ensuring
 * the URL is fresh when playback is about to start.
 */
class ResolvingDataSource(
    private val upstream: DataSource,
    private val streamRepository: StreamRepository
) : DataSource {

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val streamRepository: StreamRepository
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return ResolvingDataSource(upstreamFactory.createDataSource(), streamRepository)
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val originalUri = dataSpec.uri
        var resolvedUri = originalUri

        // Check if resolution is needed (is it a YouTube URL?)
        if (isYouTubeUrl(originalUri.toString())) {
             // Resolve synchronously (we are already on a background thread in ExoPlayer)
             try {
                 val resolvedString = runBlocking {
                     // Create a dummy song just to pass the path
                     val dummySong = Song(0, "", "", "", 0, null, originalUri.toString(), 0L)
                     streamRepository.getStreamUrl(dummySong)
                 }
                 
                 if (resolvedString != null) {
                     resolvedUri = Uri.parse(resolvedString)
                     android.util.Log.d("ResolvingDataSource", "Resolved: $originalUri -> $resolvedUri")
                 } else {
                     android.util.Log.e("ResolvingDataSource", "Failed to resolve: $originalUri")
                 }
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }

        // Create new DataSpec with resolved URI (and empty key to avoid caching conflicts if needed)
        val newDataSpec = dataSpec.buildUpon()
            .setUri(resolvedUri)
            .setKey(originalUri.toString()) // Keep original key for cache consistency
            .build()

        return upstream.open(newDataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return upstream.uri
    }

    override fun close() {
        upstream.close()
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be") || url.contains("music.youtube.com")
    }
}
