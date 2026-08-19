package com.scanner.pro.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.scanner.pro.model.OcrLanguage
import com.scanner.pro.model.ScanFilterType

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("scanner_settings", Context.MODE_PRIVATE)

    var autoSave: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    var autoCapture: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPTURE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPTURE, value).apply()

    var defaultFilter: ScanFilterType
        get() = ScanFilterType.values()[prefs.getInt(KEY_DEFAULT_FILTER, ScanFilterType.DOCUMENT.ordinal)]
        set(value) = prefs.edit().putInt(KEY_DEFAULT_FILTER, value.ordinal).apply()

    /** JPEG quality used for processed pages and non-lossless PDF export, 0-100. */
    var imageQuality: Int
        get() = prefs.getInt(KEY_IMAGE_QUALITY, 90)
        set(value) = prefs.edit().putInt(KEY_IMAGE_QUALITY, value.coerceIn(10, 100)).apply()

    var pdfCompressionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PDF_COMPRESSION, true)
        set(value) = prefs.edit().putBoolean(KEY_PDF_COMPRESSION, value).apply()

    var ocrLanguage: OcrLanguage
        get() = OcrLanguage.values()[prefs.getInt(KEY_OCR_LANGUAGE, OcrLanguage.ENGLISH.ordinal)]
        set(value) = prefs.edit().putInt(KEY_OCR_LANGUAGE, value.ordinal).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.values()[prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)]
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value.ordinal).apply()
            applyTheme(value)
        }

    fun applyTheme(mode: ThemeMode = themeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    companion object {
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_AUTO_CAPTURE = "auto_capture"
        private const val KEY_DEFAULT_FILTER = "default_filter"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_PDF_COMPRESSION = "pdf_compression"
        private const val KEY_OCR_LANGUAGE = "ocr_language"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
