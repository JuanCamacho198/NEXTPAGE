package com.nextpage.domain.model

/**
 * Color options for highlights matching the Pencil design palette.
 */
enum class HighlightColor(val hex: String) {
    YELLOW("#FDE047"),
    GREEN("#86EFAC"),
    PINK("#F9A8D4"),
    BLUE("#93C5FD"),
    PURPLE("#D8B4FE");

    companion object {
        fun fromHex(hex: String): HighlightColor? =
            entries.find { it.hex.equals(hex, ignoreCase = true) }
    }
}

/**
 * A highlight or annotation within a book.
 *
 * @property tag Optional user-defined tag for categorising highlights
 * @property comment Optional longer-form comment / annotation
 */
data class Highlight(
    val id: String,
    val bookId: String,
    val cfiRange: String,
    val textContent: String,
    val note: String?,
    val color: String,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val tag: String? = null,
    val comment: String? = null
)
