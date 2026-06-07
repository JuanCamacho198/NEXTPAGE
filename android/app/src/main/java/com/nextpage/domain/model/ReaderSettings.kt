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
 */
enum class ReaderTheme(val bgHex: String, val textHex: String) {
    DARK("#0B1120", "#E2E8F0"),
    SEPIA("#FBF1C7", "#3E2723"),
    LIGHT("#FFFFFF", "#1A1A2E");

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
 * User-customizable reading settings.
 */
data class ReaderSettings(
    val fontSize: FontSizePreset = FontSizePreset.MEDIUM,
    val theme: ReaderTheme = ReaderTheme.DARK,
    val lineHeight: LineHeightPreset = LineHeightPreset.NORMAL
) {
    companion object {
        const val PREFS_KEY = "reader_settings"
    }
}
