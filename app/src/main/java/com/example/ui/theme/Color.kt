package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Fixed brand / action colors (same in both themes) ──
val BrandBlue          = Color(0xFF58A6FF)
val BrandPurple        = Color(0xFF9B72F5)
val BrandPurpleDeep    = Color(0xFF7C3AED)
val BrandCyan          = Color(0xFF79C0FF)
val DeleteRed          = Color(0xFFFF4444)
val SuccessGreen       = Color(0xFF3FB950)
val WarningAmber       = Color(0xFFD29922)

// ── Dark palette ──
val AppBackground      = Color(0xFF0D1117)
val SurfaceColor       = Color(0xFF161B22)
val SurfaceVariant     = Color(0xFF21262D)
val OutlineColor       = Color(0xFF30363D)
val TextPrimary        = Color(0xFFE6EDF3)
val TextSecondary      = Color(0xFF8B949E)
val TextDisabled       = Color(0xFF484F58)

// ── Alarm card colors (dark) ──
val CyclicCardBg       = Color(0xFF1C2233)
val CyclicCardBorder   = Color(0xFF2D3A56)
val CyclicAccent       = Color(0xFF58A6FF)
val CyclicTime         = Color(0xFFCDE8FF)
val WeeklyCardBg       = Color(0xFF1E1A2E)
val WeeklyCardBorder   = Color(0xFF3D2F5A)
val WeeklyAccent       = Color(0xFF9B72F5)
val WeeklyTime         = Color(0xFFE2D9FF)

// ── Light palette equivalents ──
// Background is a noticeably tinted blue-grey so the whiteness slider starts
// from a clearly non-white point and the user can feel the full range.
val LightAppBackground  = Color(0xFFE3E7F0)   // was 0xFFF5F6FA — more tinted
val LightSurfaceColor   = Color(0xFFF5F7FC)   // was 0xFFFFFFFF — slight tint
val LightSurfaceVariant = Color(0xFFD8DCE8)   // was 0xFFE8EAF0
val LightOutlineColor   = Color(0xFFADB2BF)   // was 0xFFBEC2CC
val LightTextPrimary    = Color(0xFF1A1C22)
val LightTextSecondary  = Color(0xFF44474F)
val LightTextDisabled   = Color(0xFFAAADB5)

val LightCyclicCardBg      = Color(0xFFD6E6FF)   // was 0xFFE8F1FF — more vivid
val LightCyclicCardBorder  = Color(0xFF8AAFE0)   // was 0xFFB0CAEE
val LightWeeklyCardBg      = Color(0xFFEADEFF)   // was 0xFFF3EEFF
val LightWeeklyCardBorder  = Color(0xFFB89AE8)   // was 0xFFCDB8F0

// ── Light mode alarm-time colours — high-contrast so they read on light bg ──
// Dark equivalent uses CyclicTime (0xFFCDE8FF) which is near-white — unusable in light.
val LightCyclicTime     = Color(0xFF1A56A8)   // deep blue — readable on LightCyclicCardBg
val LightWeeklyTime     = Color(0xFF5B25C4)   // deep purple — readable on LightWeeklyCardBg

// ── Pure Dark (AMOLED Black) palette ──
val PureDarkBackground     = Color(0xFF000000)
val PureDarkSurface        = Color(0xFF0A0A0A)
val PureDarkSurfaceVariant = Color(0xFF141414)
val PureDarkOutline        = Color(0xFF222222)
val PureDarkCyclicCardBg   = Color(0xFF0A0F1A)
val PureDarkCyclicBorder   = Color(0xFF1A2035)
val PureDarkWeeklyCardBg   = Color(0xFF0A0814)
val PureDarkWeeklyBorder   = Color(0xFF1E1432)


// ─────────────────────────────────────────────────────────
//  AppColors — runtime-swappable color set
// ─────────────────────────────────────────────────────────
@Immutable
data class AppColors(
    val appBackground : Color,
    val surfaceColor  : Color,
    val surfaceVariant: Color,
    val outlineColor  : Color,
    val textPrimary   : Color,
    val textSecondary : Color,
    val textDisabled  : Color,
    val cyclicCardBg  : Color,
    val cyclicCardBorder: Color,
    val weeklyCardBg  : Color,
    val weeklyCardBorder: Color,
    val isDark        : Boolean,
    // Alarm-time digit colours — dark mode uses near-white tints; light mode uses deep hues
    val cyclicTimeColor: Color = CyclicTime,
    val weeklyTimeColor: Color = WeeklyTime,
)

val DarkAppColors = AppColors(
    appBackground    = AppBackground,
    surfaceColor     = SurfaceColor,
    surfaceVariant   = SurfaceVariant,
    outlineColor     = OutlineColor,
    textPrimary      = TextPrimary,
    textSecondary    = TextSecondary,
    textDisabled     = TextDisabled,
    cyclicCardBg     = CyclicCardBg,
    cyclicCardBorder = CyclicCardBorder,
    weeklyCardBg     = WeeklyCardBg,
    weeklyCardBorder = WeeklyCardBorder,
    isDark           = true,
)

val LightAppColors = AppColors(
    appBackground    = LightAppBackground,
    surfaceColor     = LightSurfaceColor,
    surfaceVariant   = LightSurfaceVariant,
    outlineColor     = LightOutlineColor,
    textPrimary      = LightTextPrimary,
    textSecondary    = LightTextSecondary,
    textDisabled     = LightTextDisabled,
    cyclicCardBg     = LightCyclicCardBg,
    cyclicCardBorder = LightCyclicCardBorder,
    weeklyCardBg     = LightWeeklyCardBg,
    weeklyCardBorder = LightWeeklyCardBorder,
    isDark           = false,
    cyclicTimeColor  = LightCyclicTime,
    weeklyTimeColor  = LightWeeklyTime,
)

val PureDarkAppColors = AppColors(
    appBackground    = PureDarkBackground,
    surfaceColor     = PureDarkSurface,
    surfaceVariant   = PureDarkSurfaceVariant,
    outlineColor     = PureDarkOutline,
    textPrimary      = TextPrimary,
    textSecondary    = TextSecondary,
    textDisabled     = TextDisabled,
    cyclicCardBg     = PureDarkCyclicCardBg,
    cyclicCardBorder = PureDarkCyclicBorder,
    weeklyCardBg     = PureDarkWeeklyCardBg,
    weeklyCardBorder = PureDarkWeeklyBorder,
    isDark           = true,
)


/** Access the current theme's color set anywhere in the composition tree. */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ── Legacy aliases so existing imports don't break ──
val DarkMidnight       = SurfaceVariant
val CardBackground     = SurfaceColor
val NeonPurple         = BrandPurple
val NeonPink           = BrandPurpleDeep
val ElectricViolet     = BrandBlue
val TextPrimary_       = TextPrimary
val TextSecondary_     = TextSecondary
val BoldBackground         = AppBackground
val BoldTitleColor         = TextPrimary
val BoldTextPrimary        = TextPrimary
val BoldTextSecondary      = TextSecondary
val BoldCyclicCardBg       = CyclicCardBg
val BoldCyclicCardAccent   = CyclicAccent
val BoldCyclicCardTime     = CyclicTime
val BoldCyclicCardSwitchBg = BrandBlue
val BoldRecurringCardBg    = WeeklyCardBg
val BoldRecurringCardAccent = WeeklyAccent
val BoldRecurringCardTime  = WeeklyTime
val BoldMiniCardBg         = Color(0xFF1A1F2E)
val BoldMiniCardAccent     = BrandCyan
