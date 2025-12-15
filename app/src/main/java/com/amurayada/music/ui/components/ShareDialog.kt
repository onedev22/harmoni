package com.amurayada.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amurayada.music.data.model.Song
import com.amurayada.music.utils.ShareUtils
import kotlinx.coroutines.launch

@Composable
fun ShareDialog(
    song: Song,
    dominantColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // We need a reference to the view to capture it
    var captureView by remember { mutableStateOf<View?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Compartir Canción",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Container that captures the view
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // This AndroidView wraps the Compose content so we can get a View reference
                AndroidView(
                    factory = { ctx ->
                        androidx.compose.ui.platform.ComposeView(ctx).apply {
                            setContent {
                                ShareSongCard(
                                    song = song,
                                    dominantColor = dominantColor
                                )
                            }
                        }
                    },
                    update = { view ->
                        captureView = view
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                
                Button(
                    onClick = {
                        captureView?.let { view ->
                            if (view.width > 0 && view.height > 0) {
                                val bitmap = captureBitmapFromView(view)
                                if (bitmap != null) {
                                    ShareUtils.shareBitmap(
                                        context, 
                                        bitmap, 
                                        "Escuchando ${song.title} de ${song.artist} en Harmoni"
                                    )
                                    onDismiss()
                                } else {
                                    android.widget.Toast.makeText(context, "Error al capturar imagen", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Espera a que cargue...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Compartir")
                }
            }
        }
    }
}

fun captureBitmapFromView(view: View): Bitmap? {
    try {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        // Force background to be drawn if transparent
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        view.draw(canvas)
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
