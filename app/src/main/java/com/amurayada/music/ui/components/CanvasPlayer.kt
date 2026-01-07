package com.amurayada.music.ui.components

import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

private const val TAG = "CanvasPlayer"

/**
 * Canvas Video Player:
 * - Uses YouTube video streams for background visuals.
 * - Uses TextureView for better stability in Compose (fixes "Unsupported input buffer" on some devices).
 * - 1.05x zoom to avoid edge artifacts.
 * - Fade-in on first frame render.
 * - Silent looping playback.
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasPlayer(
    uri: String?,
    startMs: Long = 40000L, // Default start at 40s
    endMs: Long = 50000L,   // Default end at 50s (10s loop)
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember { mutableStateOf(false) }
    
    // We need to know the video dimensions to calculate the CenterCrop matrix
    var videoWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var videoHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f // Silent
            repeatMode = Player.REPEAT_MODE_ONE // Loop forever
            // We handle scaling manually with Matrix for perfect CenterCrop
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }
    
    // Listen for first frame render, video size, and errors
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.d(TAG, "First frame rendered")
                isFirstFrameRendered = true
            }
            
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    Log.d(TAG, "Video size changed: $videoWidth x $videoHeight")
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}")
                onError?.invoke()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(uri) {
        if (uri == null) {
            exoPlayer.pause()
            isFirstFrameRendered = false
            return@LaunchedEffect
        }
        
        // Use default 10s loop if no specific range provided (or if defaults are used)
        // If the caller wants the full video, they should usually pass specific params, 
        // but for Canvas, we prefer the loop.
        val effectiveStart = if (startMs < 0) 0L else startMs
        val effectiveEnd = if (endMs <= effectiveStart) effectiveStart + 10000L else endMs
        
        Log.d(TAG, "Loading Canvas: uri=$uri, start=$effectiveStart, end=$effectiveEnd")
        isFirstFrameRendered = false
        
        try {
            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            val baseMediaItem = MediaItem.fromUri(Uri.parse(uri))
            var mediaSource = mediaSourceFactory.createMediaSource(baseMediaItem)
            
            // Clipping Logic - Always apply for Canvas feel
            val clippingSource = androidx.media3.exoplayer.source.ClippingMediaSource(
                mediaSource,
                effectiveStart * 1000,
                effectiveEnd * 1000,
                false,
                false,
                true
            )
            mediaSource = androidx.media3.exoplayer.source.LoopingMediaSource(clippingSource)
            
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Canvas video", e)
            onError?.invoke()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Releasing player")
            exoPlayer.release()
        }
    }

    // Smooth Alpha Transition
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "canvasAlpha"
    )

    // Layered composition: Video + Dark Overlay
    Box(modifier = modifier) {
        // Video layer
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    exoPlayer.setVideoTextureView(this)
                }
            },
            update = { textureView ->
                // Apply CenterCrop transformation
                if (videoWidth > 0 && videoHeight > 0 && textureView.width > 0 && textureView.height > 0) {
                    val viewWidth = textureView.width.toFloat()
                    val viewHeight = textureView.height.toFloat()
                    
                    val scaleX = viewHeight / videoHeight * videoWidth / viewWidth
                    val scaleY = 1f // Scale to fit height primarily
                    
                    // Logic for CenterCrop:
                    // If video is wider than view (relative to height), we crop sides.
                    // If view is wider than video (relative to height), we crop top/bottom.
                    
                    val videoAspect = videoWidth.toFloat() / videoHeight
                    val viewAspect = viewWidth / viewHeight
                    
                    val scaleXFinal: Float
                    val scaleYFinal: Float
                    
                    if (videoAspect > viewAspect) {
                        // Video is wider -> Scale by height, crop width
                        scaleXFinal = (videoAspect / viewAspect)
                        scaleYFinal = 1f
                    } else {
                        // View is wider -> Scale by width, crop height
                        scaleXFinal = 1f
                        scaleYFinal = (viewAspect / videoAspect)
                    }

                    val matrix = android.graphics.Matrix()
                    // Pivot at center
                    matrix.setScale(scaleXFinal, scaleYFinal, viewWidth / 2f, viewHeight / 2f)
                    textureView.setTransform(matrix)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        )
        
        // Dark overlay (10% black) for text readability - Premium style
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
        )
    }
}
