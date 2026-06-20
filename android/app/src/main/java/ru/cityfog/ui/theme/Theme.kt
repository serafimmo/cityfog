package ru.cityfog.ui.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CityFogDarkScheme = darkColorScheme(
    primary = CityFogColors.Cyan400,
    onPrimary = CityFogColors.Navy900,
    primaryContainer = CityFogColors.Navy700,
    onPrimaryContainer = CityFogColors.Cyan200,
    secondary = CityFogColors.Teal400,
    onSecondary = CityFogColors.Navy900,
    secondaryContainer = CityFogColors.Navy700,
    onSecondaryContainer = CityFogColors.Cyan200,
    tertiary = CityFogColors.Cyan300,
    onTertiary = CityFogColors.Navy900,
    tertiaryContainer = CityFogColors.Navy700,
    onTertiaryContainer = CityFogColors.Cyan200,
    background = CityFogColors.Navy900,
    onBackground = CityFogColors.TextPrimary,
    surface = CityFogColors.Navy800,
    onSurface = CityFogColors.TextPrimary,
    surfaceVariant = CityFogColors.Navy700,
    onSurfaceVariant = CityFogColors.TextSecondary,
    error = CityFogColors.Recording,
    onError = Color.White,
    outline = CityFogColors.Navy600
)

private val CityFogLightScheme = lightColorScheme(
    primary = CityFogColors.Teal500,
    onPrimary = Color.White,
    primaryContainer = CityFogColors.Cyan200,
    onPrimaryContainer = CityFogColors.Navy900,
    secondary = CityFogColors.Cyan400,
    onSecondary = Color.White,
    secondaryContainer = CityFogColors.Cyan200,
    onSecondaryContainer = CityFogColors.Navy900,
    tertiary = CityFogColors.Teal400,
    onTertiary = Color.White,
    tertiaryContainer = CityFogColors.Cyan200,
    onTertiaryContainer = CityFogColors.Navy900,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    error = CityFogColors.Recording,
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun CityFogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Для CityFog тёмная тема — дефолт: ночной туман выглядит лучше.
    forceDark: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val effectiveDark = forceDark || darkTheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        effectiveDark -> CityFogDarkScheme
        else -> CityFogLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !effectiveDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
