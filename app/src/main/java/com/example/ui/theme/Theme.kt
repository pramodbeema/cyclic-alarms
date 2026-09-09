package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

// Pure Dark (AMOLED black) color scheme
private val PureDarkColorScheme = darkColorScheme(
    primary              = BrandBlue,
    onPrimary            = Color(0xFF003166),
    primaryContainer     = Color(0xFF050A14),
    onPrimaryContainer   = CyclicTime,
    secondary            = BrandPurple,
    onSecondary          = Color(0xFF2D1B5E),
    secondaryContainer   = Color(0xFF080510),
    onSecondaryContainer = WeeklyTime,
    tertiary             = BrandCyan,
    onTertiary           = Color(0xFF003544),
    background           = PureDarkBackground,
    onBackground         = TextPrimary,
    surface              = PureDarkSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = PureDarkSurfaceVariant,
    onSurfaceVariant     = TextSecondary,
    outline              = PureDarkOutline,
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
    themeMode: String = "Dark",    // "Pure Dark", "Dark", "Light", or "System"
    dynamicColor: Boolean = false, // kept for legacy, unused
    lightWhiteness: Float = 0f,    // 0.0 = default light bg, 1.0 = pure white
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "Light"     -> false
        "Dark"      -> true
        "Pure Dark" -> true
        else        -> systemDark  // "System"
    }
    val isPureDark = themeMode == "Pure Dark"

    val colorScheme = when {
        isPureDark -> PureDarkColorScheme
        isDark     -> AppColorScheme
        else       -> {
            // Apply whiteness interpolation to light scheme background
            val adjustedBg = lerp(LightAppBackground, Color.White, lightWhiteness.coerceIn(0f, 1f))
            val adjustedSurface = lerp(LightSurfaceColor, Color.White, (lightWhiteness * 0.5f).coerceIn(0f, 1f))
            LightColorScheme.copy(background = adjustedBg, surface = adjustedSurface)
        }
    }

    val appColors = when {
        isPureDark -> PureDarkAppColors
        isDark     -> DarkAppColors
        else       -> {
            // Apply whiteness interpolation to AppColors background
            val adjustedBg = lerp(LightAppBackground, Color.White, lightWhiteness.coerceIn(0f, 1f))
            val adjustedSurface = lerp(LightSurfaceColor, Color.White, (lightWhiteness * 0.5f).coerceIn(0f, 1f))
            LightAppColors.copy(appBackground = adjustedBg, surfaceColor = adjustedSurface)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
