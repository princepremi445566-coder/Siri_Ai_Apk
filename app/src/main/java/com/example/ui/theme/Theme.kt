package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SiriPrimaryDark,
    onPrimary = SiriOnPrimaryDark,
    primaryContainer = SiriPrimaryContainerDark,
    onPrimaryContainer = SiriOnPrimaryContainerDark,
    secondary = SiriSecondaryDark,
    onSecondary = SiriOnSecondaryDark,
    secondaryContainer = SiriSecondaryContainerDark,
    onSecondaryContainer = SiriOnSecondaryContainerDark,
    tertiary = SiriTertiaryDark,
    onTertiary = SiriOnTertiaryDark,
    tertiaryContainer = SiriTertiaryContainerDark,
    onTertiaryContainer = SiriOnTertiaryContainerDark,
    background = SiriDarkBackground,
    onBackground = SiriTextPrimary,
    surface = SiriDarkSurface,
    onSurface = SiriTextPrimary,
    surfaceVariant = SiriDarkSurfaceVariant,
    onSurfaceVariant = SiriTextSecondary,
    outline = SiriDarkSurfaceBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SiriPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = SiriSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = SiriTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF9D174D),
    background = SiriLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = SiriLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SiriLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun SiriAITheme(
    darkTheme: Boolean = true, // Default to sleek futuristic dark mode for Siri AI
    dynamicColor: Boolean = false, // Keep consistent brand identity
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
