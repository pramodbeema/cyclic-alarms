package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Always dark — matches the icon's dark navy aesthetic
private val AppColorScheme = darkColorScheme(
    primary              = BrandBlue,
    onPrimary            = Color(0xFF003166),
    primaryContainer     = Color(0xFF1C2233),
    onPrimaryContainer   = CyclicTime,
    secondary            = BrandPurple,
    onSecondary          = Color(0xFF2D1B5E),
    secondaryContainer   = Color(0xFF1E1A2E),
    onSecondaryContainer = WeeklyTime,
    tertiary             = BrandCyan,
    onTertiary           = Color(0xFF003544),
    background           = AppBackground,
    onBackground         = TextPrimary,
    surface              = SurfaceColor,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceVariant,
    onSurfaceVariant     = TextSecondary,
    outline              = OutlineColor,
    error                = DeleteRed,
    onError              = Color.White
)

// Light color scheme for Light mode
private val LightColorScheme = lightColorScheme(
    primary              = BrandBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFD6E4FF),
    onPrimaryContainer   = Color(0xFF001D40),
    secondary            = BrandPurple,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFEBDEFF),
    onSecondaryContainer = Color(0xFF1E0057),
    tertiary             = BrandCyan,
    onTertiary           = Color.White,
    background           = LightAppBackground,
    onBackground         = LightTextPrimary,
    surface              = LightSurfaceColor,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = LightTextSecondary,
    outline              = LightOutlineColor,
    error                = DeleteRed,
    onError              = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "Dark",    // "Dark", "Light", or "System"
    dynamicColor: Boolean = false, // kept for legacy, unused
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "Light" -> false
        "Dark"  -> true
        else    -> systemDark  // "System"
    }

    val colorScheme  = if (isDark) AppColorScheme else LightColorScheme
    val appColors    = if (isDark) DarkAppColors   else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
