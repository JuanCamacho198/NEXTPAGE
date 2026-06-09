package com.nextpage.domain.model

/**
 * Presets for the reader's font size.
 */
enum class FontSizePreset(val sizePx: Int) {
    SMALL(16),
    MEDIUM(20),
    LARGE(24),
    XLARGE(28);

    companion object {
        fun fromPx(px: Int): FontSizePreset =
            entries.find { it.sizePx == px } ?: MEDIUM

        fun fromOrdinal(ordinal: Int): FontSizePreset =
            entries.getOrElse(ordinal) { MEDIUM }
    }
}

/**
 * Reading background / text color theme.
 * OLED is a distinct variant from DARK: bg=#0d1322ff, text=#dde2f8ff.
 */
enum class ReaderTheme(val bgHex: String, val textHex: String) {
    DARK("#121212", "#E2E8F0"),
    SEPIA("#F4ECD8", "#3E2723"),
    LIGHT("#FFFFFF", "#1A1A2E"),
    OLED("#0d1322", "#dde2f8");

    companion object {
        fun fromOrdinal(ordinal: Int): ReaderTheme =
            entries.getOrElse(ordinal) { DARK }
    }
}

/**
 * Line spacing preset for the EPUB WebView.
 */
enum class LineHeightPreset(val value: Float) {
    TIGHT(1.3f),
    NORMAL(1.6f),
    COMFORTABLE(1.8f),
    WIDE(2.0f);

    companion object {
        fun fromOrdinal(ordinal: Int): LineHeightPreset =
            entries.getOrElse(ordinal) { NORMAL }
    }
}

/**
 * Scroll mode for the reading view.
 */
enum class ScrollMode {
    VERTICAL,
    PAGINATED
}

/**
 * Layout preferences for margins and alignment.
 */
data class LayoutPreferences(
    val margins: Margins = Margins.NORMAL,
    val alignment: Alignment = Alignment.JUSTIFY
) {
    enum class Margins {
        NARROW,
        NORMAL,
        WIDE
    }

    enum class Alignment {
        LEFT,
        JUSTIFY
    }
}

/**
 * User-customizable reading settings.
 *
 * New fields added during the Reader Pencil Redesign:
 * - [fontName]: selected font family ("Georgia", "Arial", "Merriweather")
 * - [scrollMode]: vertical scrolling vs paginated
 * - [layoutPrefs]: margins and alignment preferences
 * - [editorValues]: whether to apply editor CSS values
 * - [verticalScroll]: whether vertical scroll mode is active
 */
data class ReaderSettings(
    val fontSize: FontSizePreset = FontSizePreset.MEDIUM,
    val theme: ReaderTheme = ReaderTheme.DARK,
    val lineHeight: LineHeightPreset = LineHeightPreset.NORMAL,
    val fontName: String = "Georgia",
    val scrollMode: ScrollMode = ScrollMode.VERTICAL,
    val layoutPrefs: LayoutPreferences = LayoutPreferences(),
    val editorValues: Boolean = true,
    val verticalScroll: Boolean = false
) {
    companion object {
        const val PREFS_KEY = "reader_settings"
    }
}
