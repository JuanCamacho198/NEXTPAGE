package com.nextpage.data.session

import android.content.Context
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.LayoutPreferences
import com.nextpage.domain.model.LineHeightPreset
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import org.json.JSONArray

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
        val fontSizeName = prefs.getString(KEY_FONT_SIZE, FontSizePreset.M.name)
            ?: FontSizePreset.M.name
        val themeName = prefs.getString(KEY_THEME, ReaderTheme.DARK.name)
            ?: ReaderTheme.DARK.name
        val lineHeightName = prefs.getString(KEY_LINE_HEIGHT, LineHeightPreset.NORMAL.name)
            ?: LineHeightPreset.NORMAL.name

        val alignmentName = prefs.getString(KEY_ALIGNMENT, null)
            ?: LayoutPreferences.Alignment.JUSTIFY.name
        val leftMargin = prefs.getInt(KEY_LEFT_MARGIN, 16)
        val rightMargin = prefs.getInt(KEY_RIGHT_MARGIN, 16)

        val customColorsJson = prefs.getString(KEY_PALETTE, null)
        val customColors: List<String>? = if (customColorsJson != null) {
            try {
                val arr = JSONArray(customColorsJson)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { null }
        } else null

        return ReaderSettings(
            fontSize = safeValueOf(FontSizePreset.entries, fontSizeName, FontSizePreset.M),
            theme = safeValueOf(ReaderTheme.entries, themeName, ReaderTheme.DARK),
            lineHeight = safeValueOf(LineHeightPreset.entries, lineHeightName, LineHeightPreset.NORMAL),
            layoutPrefs = LayoutPreferences(
                leftMargin = leftMargin,
                rightMargin = rightMargin,
                alignment = safeValueOf(
                    LayoutPreferences.Alignment.entries,
                    alignmentName,
                    LayoutPreferences.Alignment.JUSTIFY
                )
            ),
            customHighlightColors = customColors
        )
    }

    /**
     * Persist settings.
     */
    fun save(settings: ReaderSettings) {
        val editor = prefs.edit()
            .putString(KEY_FONT_SIZE, settings.fontSize.name)
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_LINE_HEIGHT, settings.lineHeight.name)
            .putString(KEY_ALIGNMENT, settings.layoutPrefs.alignment.name)
            .putInt(KEY_LEFT_MARGIN, settings.layoutPrefs.leftMargin)
            .putInt(KEY_RIGHT_MARGIN, settings.layoutPrefs.rightMargin)

        if (settings.customHighlightColors != null) {
            val arr = JSONArray()
            for (color in settings.customHighlightColors) arr.put(color)
            editor.putString(KEY_PALETTE, arr.toString())
        } else {
            editor.remove(KEY_PALETTE)
        }

        editor.apply()
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
        private const val KEY_ALIGNMENT = "alignment"
        private const val KEY_LEFT_MARGIN = "left_margin"
        private const val KEY_RIGHT_MARGIN = "right_margin"
        private const val KEY_PALETTE = "highlight_palette"
    }
}
