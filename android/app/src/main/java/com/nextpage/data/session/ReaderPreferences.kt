package com.nextpage.data.session

import android.content.Context
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.LineHeightPreset
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme

/**
 * Persists reader settings (font size, theme, line height) to SharedPreferences.
 * Uses enum [name] for stable persistence across enum reordering.
 */
class ReaderPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Load saved settings or return defaults.
     */
    fun load(): ReaderSettings {
        val fontSizeName = prefs.getString(KEY_FONT_SIZE, FontSizePreset.MEDIUM.name)
            ?: FontSizePreset.MEDIUM.name
        val themeName = prefs.getString(KEY_THEME, ReaderTheme.DARK.name)
            ?: ReaderTheme.DARK.name
        val lineHeightName = prefs.getString(KEY_LINE_HEIGHT, LineHeightPreset.NORMAL.name)
            ?: LineHeightPreset.NORMAL.name

        return ReaderSettings(
            fontSize = safeValueOf(FontSizePreset.entries, fontSizeName, FontSizePreset.MEDIUM),
            theme = safeValueOf(ReaderTheme.entries, themeName, ReaderTheme.DARK),
            lineHeight = safeValueOf(LineHeightPreset.entries, lineHeightName, LineHeightPreset.NORMAL)
        )
    }

    /**
     * Persist settings.
     */
    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putString(KEY_FONT_SIZE, settings.fontSize.name)
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_LINE_HEIGHT, settings.lineHeight.name)
            .apply()
    }

    /**
     * Safe enum lookup by name, falling back to a default if not found.
     */
    private inline fun <reified T : Enum<T>> safeValueOf(
        entries: List<T>,
        name: String,
        default: T
    ): T {
        return entries.find { it.name == name } ?: default
    }

    companion object {
        private const val PREFS_NAME = "nextpage_reader_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_LINE_HEIGHT = "line_height"
    }
}
