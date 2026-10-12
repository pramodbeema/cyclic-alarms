package com.example.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class SupportedLanguage(
    val code: String,        // "" for System Default, "en", "tr", etc.
    val nativeName: String,  // Display name in its own language
    val englishName: String, // Display name in English
    val flagEmoji: String    // Emoji flag or icon
)

object LocaleManager {
    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("", "System Default", "Default", "🌐"),
        SupportedLanguage("en", "English", "English", "🇺🇸"),
        SupportedLanguage("tr", "Türkçe", "Turkish", "🇹🇷"),
        SupportedLanguage("es", "Español", "Spanish", "🇪🇸"),
        SupportedLanguage("de", "Deutsch", "German", "🇩🇪"),
        SupportedLanguage("fr", "Français", "French", "🇫🇷"),
        SupportedLanguage("hi", "हिन्दी", "Hindi", "🇮🇳"),
        SupportedLanguage("pt", "Português", "Portuguese", "🇧🇷"),
        SupportedLanguage("ru", "Русский", "Russian", "🇷🇺"),
        SupportedLanguage("zh", "中文", "Chinese (Simplified)", "🇨🇳"),
        SupportedLanguage("ja", "日本語", "Japanese", "🇯🇵"),
        SupportedLanguage("ar", "العربية", "Arabic", "🇸🇦")
    )

    fun getLanguageTag(context: Context): String {
        val prefs = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
        return prefs.getString("app_language", "") ?: ""
    }

    fun setLanguage(context: Context, languageTag: String, restartActivity: Boolean = true) {
        val prefs = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", languageTag).apply()

        applyLocaleToContext(context, languageTag)

        if (restartActivity && context is Activity) {
            context.recreate()
        }
    }

    fun applyLocaleToContext(context: Context, languageTag: String): Context {
        val locale = if (languageTag.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(if (languageTag.isEmpty()) LocaleList.getDefault() else LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        return context.createConfigurationContext(config)
    }
}
