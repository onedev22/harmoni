package com.amurayada.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    secondary = DeepPurple80,
    tertiary = Teal80,
    background = SurfaceDark,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    secondary = DeepPurple40,
    tertiary = Teal40,
    background = SurfaceLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    customPrimaryColor: Int? = null,
    isCustomBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    // If AMOLED mode is on, we force dark theme base
    val effectiveDarkTheme = darkTheme || amoledMode
    
    val baseColorScheme = when {
        customPrimaryColor != null -> {
            val primary = Color(customPrimaryColor)
            if (effectiveDarkTheme) {
                DarkColorScheme.copy(
                    primary = primary,
                    onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = primary.copy(alpha = 0.3f), // Simple derivation
                    onPrimaryContainer = Color.White,
                    secondary = primary, // Reuse for simplicity or derive
                    tertiary = primary
                )
            } else {
                LightColorScheme.copy(
                    primary = primary,
                    onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = primary.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.Black,
                    secondary = primary,
                    tertiary = primary
                )
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Apply AMOLED black if enabled
    var colorScheme = if (amoledMode) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerHigh = Color(0xFF121212), // Slightly lighter for contrast
            surfaceContainerHighest = Color(0xFF1E1E1E)
        )
    } else {
        baseColorScheme
    }

    // For custom backgrounds, we only make the root background transparent.
    // We keep surface and containers solid for readable popups/menus.
    if (isCustomBackground) {
        colorScheme = colorScheme.copy(
            background = Color.Transparent,
            surface = if (effectiveDarkTheme) Color(0xFF121212) else Color.White,
            surfaceContainer = if (effectiveDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF3F3F3),
            surfaceContainerLow = if (effectiveDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF7F7F7),
            surfaceContainerHigh = if (effectiveDarkTheme) Color(0xFF222222) else Color(0xFFEBEBEB)
        )
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}