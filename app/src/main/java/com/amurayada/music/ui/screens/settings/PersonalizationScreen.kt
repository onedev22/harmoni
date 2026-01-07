package com.amurayada.music.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amurayada.music.R
import com.amurayada.music.ui.viewmodel.SettingsViewModel

// Quick preset colors
private val PresetColors = listOf(
    Color(0xFF6200EE), Color(0xFFE91E63), Color(0xFFFF5722), 
    Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF795548)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    settingsViewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val customPrimaryColor by settingsViewModel.customPrimaryColor.collectAsState()
    val customBgUri by settingsViewModel.customBackgroundImageUri.collectAsState()
    val playerBgType by settingsViewModel.playerBackgroundType.collectAsState()
    val playerBgColor by settingsViewModel.playerBackgroundColor.collectAsState()
    val playerArtScale by settingsViewModel.playerAlbumArtScale.collectAsState()

    var showColorDialog by remember { mutableStateOf(false) }
    var showPlayerColorDialog by remember { mutableStateOf(false) }

    // Image Picker for Background
    val bgImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = settingsViewModel.copyImageToInternalStorage(context, it)
            settingsViewModel.setCustomBackgroundImage(path)
        }
    }

    val isGlassy = MaterialTheme.colorScheme.background == Color.Transparent

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Personalización", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isGlassy) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (isGlassy) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ============ PLAYER PREVIEW ============
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlassy) Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        else Modifier
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGlassy) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .let { mod ->
                            if (playerBgType == "auto") {
                                // Gradient based on primary color
                                val baseColor = if (customPrimaryColor != null) 
                                    Color(customPrimaryColor!!) 
                                else 
                                    MaterialTheme.colorScheme.primary
                                mod.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            baseColor.copy(alpha = 0.9f),
                                            baseColor.copy(alpha = 0.4f),
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                            } else if (playerBgType == "custom" && playerBgColor != null) {
                                mod.background(Color(playerBgColor!!))
                            } else {
                                mod
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Album Art Preview
                        Box(
                            modifier = Modifier
                                .size((100 * playerArtScale).dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((40 * playerArtScale).dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Título de Canción",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Nombre del Artista",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Fake progress bar
                        LinearProgressIndicator(
                            progress = { 0.4f },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (customPrimaryColor != null) Color(customPrimaryColor!!) else MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            
            Text(
                text = "Vista previa del reproductor",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // ============ THEME SECTION ============
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGlassy) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isGlassy) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(icon = Icons.Rounded.Palette, title = "Tema")

                    // Custom Primary Color
                    SettingRow(
                        icon = Icons.Rounded.ColorLens,
                        title = "Color de énfasis",
                        subtitle = if (customPrimaryColor != null) "Personalizado" else "Por defecto",
                        onClick = { showColorDialog = true },
                        trailing = {
                            if (customPrimaryColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(customPrimaryColor!!))
                                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Background Image
                    SettingRow(
                        icon = Icons.Rounded.Image,
                        title = "Imagen de fondo",
                        subtitle = if (customBgUri != null) "Imagen seleccionada" else "Sin imagen",
                        onClick = { bgImagePicker.launch("image/*") }
                    )

                    if (customBgUri != null) {
                        TextButton(
                            onClick = { settingsViewModel.setCustomBackgroundImage(null) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quitar")
                        }
                    }
                }
            }

            // ============ PLAYER SECTION ============
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGlassy) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isGlassy) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(icon = Icons.Rounded.PlayCircle, title = "Reproductor")

                    // Player Background Mode
                    Text(
                        text = "Fondo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                    )
                    
                    val applyBgToPlayer by settingsViewModel.applyBackgroundToPlayer.collectAsState()
                    
                    if (customBgUri != null) {
                        SettingRow(
                            icon = Icons.Rounded.Wallpaper,
                            title = "Usar fondo de la aplicación",
                            subtitle = "Aplicar la imagen de fondo también al reproductor",
                            onClick = { settingsViewModel.setApplyBackgroundToPlayer(!applyBgToPlayer) },
                            trailing = {
                                Switch(
                                    checked = applyBgToPlayer,
                                    onCheckedChange = { settingsViewModel.setApplyBackgroundToPlayer(it) }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "auto" to "Degradado",
                            "custom" to "Color sólido"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = playerBgType == type,
                                onClick = { settingsViewModel.setPlayerBackgroundType(type) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (playerBgType == "custom") {
                        Spacer(Modifier.height(12.dp))
                        SettingRow(
                            icon = Icons.Rounded.FormatPaint,
                            title = "Color de fondo",
                            subtitle = "Toca para personalizar",
                            onClick = { showPlayerColorDialog = true },
                            trailing = {
                                if (playerBgColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(playerBgColor!!))
                                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Album Art Scale
                    Text(
                        text = "Tamaño de portada: ${(playerArtScale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = playerArtScale,
                        onValueChange = { settingsViewModel.setPlayerAlbumArtScale(it) },
                        valueRange = 0.6f..1.4f,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Reset Button
            Button(
                onClick = { settingsViewModel.resetPersonalization() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Restablecer personalización")
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    // ============ COLOR PICKER DIALOGS ============
    if (showColorDialog) {
        ColorPickerDialog(
            title = "Color de énfasis",
            currentColor = customPrimaryColor?.let { Color(it) },
            onColorSelected = { color ->
                settingsViewModel.setCustomPrimaryColor(color?.toArgb())
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }

    if (showPlayerColorDialog) {
        ColorPickerDialog(
            title = "Color del reproductor",
            currentColor = playerBgColor?.let { Color(it) },
            onColorSelected = { color ->
                color?.let { settingsViewModel.setPlayerBackgroundColor(it.toArgb()) }
                showPlayerColorDialog = false
            },
            onDismiss = { showPlayerColorDialog = false },
            showResetOption = false
        )
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    currentColor: Color?,
    onColorSelected: (Color?) -> Unit,
    onDismiss: () -> Unit,
    showResetOption: Boolean = true
) {
    var selectedHue by remember { mutableFloatStateOf(currentColor?.let { getHue(it) } ?: 0f) }
    var selectedSaturation by remember { mutableFloatStateOf(currentColor?.let { getSaturation(it) } ?: 1f) }
    var selectedBrightness by remember { mutableFloatStateOf(currentColor?.let { getBrightness(it) } ?: 1f) }
    
    val currentPickedColor = Color.hsv(selectedHue, selectedSaturation, selectedBrightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color Preview
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(currentPickedColor)
                        .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
                
                // Hue Slider (Rainbow)
                Text("Tono", style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                selectedHue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                selectedHue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                            }
                        }
                ) {
                    // Indicator
                    Box(
                        modifier = Modifier
                            .offset(x = ((selectedHue / 360f) * 280).dp - 8.dp)
                            .size(16.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }
                
                // Saturation Slider
                Text("Saturación", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = selectedSaturation,
                    onValueChange = { selectedSaturation = it },
                    colors = SliderDefaults.colors(
                        thumbColor = currentPickedColor,
                        activeTrackColor = currentPickedColor
                    )
                )
                
                // Brightness Slider
                Text("Brillo", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = selectedBrightness,
                    onValueChange = { selectedBrightness = it },
                    colors = SliderDefaults.colors(
                        thumbColor = currentPickedColor,
                        activeTrackColor = currentPickedColor
                    )
                )
                
                // Quick Presets
                Text("Preajustes rápidos", style = MaterialTheme.typography.labelSmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PresetColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    selectedHue = getHue(color)
                                    selectedSaturation = getSaturation(color)
                                    selectedBrightness = getBrightness(color)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentPickedColor) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            Row {
                if (showResetOption) {
                    TextButton(onClick = { onColorSelected(null) }) {
                        Text("Restablecer")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

// Helper functions to extract HSV from Color
private fun getHue(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    
    return when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6)
        max == g -> 60f * (((b - r) / delta) + 2)
        else -> 60f * (((r - g) / delta) + 4)
    }.let { if (it < 0) it + 360 else it }
}

private fun getSaturation(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    return if (max == 0f) 0f else (max - min) / max
}

private fun getBrightness(color: Color): Float {
    return maxOf(color.red, color.green, color.blue)
}
