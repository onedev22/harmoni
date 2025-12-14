package com.amurayada.music.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceTheme
import androidx.compose.runtime.remember
import com.amurayada.music.R

class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val title = prefs[MusicWidgetState.titleKey] ?: MusicWidgetState.DEFAULT_TITLE
            val artist = prefs[MusicWidgetState.artistKey] ?: MusicWidgetState.DEFAULT_ARTIST
            val isPlaying = prefs[MusicWidgetState.isPlayingKey] ?: false
            val isFavorite = prefs[MusicWidgetState.isFavoriteKey] ?: false
            val coverUriString = prefs[MusicWidgetState.coverUriKey]
            
            // Load bitmap if URI exists
            val coverBitmap = remember(coverUriString) {
                if (coverUriString != null) {
                    loadBitmapFromUri(context, coverUriString)
                } else {
                    null
                }
            }
            
            MusicWidgetContent(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                isFavorite = isFavorite,
                coverBitmap = coverBitmap
            )
        }
    }

    @Composable
    private fun MusicWidgetContent(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isFavorite: Boolean,
        coverBitmap: android.graphics.Bitmap?
    ) {
        // Responsive Logic
        // Responsive Logic
        val size = androidx.glance.LocalSize.current
        val isSmall = size.width < 300.dp // Threshold for scaling down
        
        // Dimensions based on size
        val coverSize = if (isSmall) 56.dp else 78.dp
        val playButtonSize = if (isSmall) 56.dp else 78.dp
        val likeButtonWidth = if (isSmall) 40.dp else 53.dp
        val likeButtonHeight = if (isSmall) 56.dp else 78.dp
        val cornerRadius = if (isSmall) 18.dp else 24.dp
        val spacerSize = if (isSmall) 6.dp else 14.dp
        val iconSize = if (isSmall) 22.dp else 24.dp
        val playIconSize = if (isSmall) 28.dp else 32.dp
        val titleSize = if (isSmall) 17.sp else 20.sp
        val artistSize = if (isSmall) 13.sp else 16.sp
        val containerCornerRadius = if (isSmall) 22.dp else 35.dp
        
        // Main Container
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(if (isSmall) GlanceModifier.height(76.dp) else GlanceModifier.fillMaxHeight())
                .background(Color(0xFF1d2a1e))
                .cornerRadius(containerCornerRadius)
                .padding(spacerSize)
                .clickable(actionRunCallback<OpenAppActionCallback>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Cover Art
            Box(
                modifier = GlanceModifier
                    .size(coverSize)
                    .background(ColorProvider(Color(0xFFff7a5c)))
                    .cornerRadius(cornerRadius),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        provider = ImageProvider(coverBitmap),
                        contentDescription = "Album Art",
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(cornerRadius),
                        contentScale = androidx.glance.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "🎵",
                        style = TextStyle(fontSize = if (isSmall) 18.sp else 24.sp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(spacerSize))

            // Text Info
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFf1f5ec)),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = artist,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF90a090)),
                        fontSize = artistSize
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(spacerSize))

            // Like Button (Always visible, scaled)
            Box(
                modifier = GlanceModifier
                    .width(likeButtonWidth)
                    .height(likeButtonHeight)
                    .background(Color(0xFFdff3f4))
                    .cornerRadius(if (isSmall) 12.dp else 18.dp)
                    .clickable(actionRunCallback<ToggleFavoriteActionCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    contentDescription = "Favorite",
                    modifier = GlanceModifier.size(iconSize),
                    colorFilter = androidx.glance.ColorFilter.tint(if (isFavorite) GlanceTheme.colors.primary else ColorProvider(Color(0xFF1d2a1e)))
                )
            }

            Spacer(modifier = GlanceModifier.width(spacerSize))

            // Play Button
            Box(
                modifier = GlanceModifier
                    .size(playButtonSize)
                    .background(Color(0xFF7cff6b))
                    .cornerRadius(if (isSmall) 16.dp else 22.dp)
                    .clickable(actionRunCallback<PlayPauseActionCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = GlanceModifier.size(playIconSize),
                    colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color(0xFF042b10)))
                )
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uriString: String): android.graphics.Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Resize to avoid IPC transaction limit (approx 200x200 is safe and good quality for widget)
            if (originalBitmap != null) {
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)
                if (scaledBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }
                scaledBitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
