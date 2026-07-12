package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.nextpage.R

/**
 * Horizontal pill of icon actions shown when the user taps an
 * existing highlight. Sibling of [TextSelectionMenu] but with a
 * different action set: color, copy, tag, annotate, share, delete
 * (the "new selection" menu has Dictionary + no Tag/Delete).
 *
 * Design node `FaPN3` — single pill with thin vertical dividers
 * between action groups:
 * ```
 * [Palette] | [Copy] | [Tag] | [Annotate] | [Share] | [Delete]
 * ```
 * All colors come from [MaterialTheme.colorScheme] so the menu
 * adapts to light/dark themes.
 *
 * @param selectedColor Currently active highlight color (hex with or
 *   without `#`). Drives the Palette icon tint via
 *   [parseColorHex]. Bad hex strings resolve to opaque black.
 * @param onColorSelected Invoked when the Palette icon is tapped.
 *   Typically opens the [HighlightColorPickerPopover].
 * @param onCopy Copy the highlight text to the clipboard.
 * @param onAddTag Open the [AnchoredTagInput] for this highlight.
 * @param onAnnotate Open the [HighlightAnnotationModal] for this
 *   highlight.
 * @param onShare Share the highlight text via the system share sheet.
 * @param onDelete Delete this highlight. The Delete icon is tinted
 *   `colorScheme.error` to signal destructive intent.
 * @param modifier Modifier applied to the outer `Row`.
 *
 * **Visual**: pill-shaped `Row` with 12dp shadow, 6dp padding,
 *   50%-radius corners. Each icon is 40dp clickable circle; vertical
 *   dividers (1dp × 20dp) are placed between the Palette group and
 *   the Share group. Delete icon uses `colorScheme.error` tint.
 * **Behavior**: each icon calls its respective callback. No internal
 *   state, no animation.
 * **Recomposition**: recomposes when `selectedColor` or any callback
 *   changes.
 */
@Composable
fun FloatingContextMenu(
    selectedColor: String,
    onColorSelected: () -> Unit,
    onCopy: () -> Unit,
    onAddTag: () -> Unit,
    onAnnotate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val contentColor = contentColorFor(containerColor)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(50))
            .background(containerColor, RoundedCornerShape(50))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SelectionMenuIcon(
            icon = Icons.Default.Palette,
            contentDescription = stringResource(R.string.context_menu_color),
            tint = parseColorHex(selectedColor),
            onClick = onColorSelected
        )

        MenuVerticalDivider(dividerColor)

        SelectionMenuIcon(
            icon = Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.text_selection_copy),
            tint = contentColor,
            onClick = onCopy
        )

        SelectionMenuIcon(
            icon = Icons.AutoMirrored.Filled.Label,
            contentDescription = stringResource(R.string.context_menu_tag),
            tint = contentColor,
            onClick = onAddTag
        )

        SelectionMenuIcon(
            icon = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.context_menu_annotate),
            tint = contentColor,
            onClick = onAnnotate
        )

        MenuVerticalDivider(dividerColor)

        SelectionMenuIcon(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.context_menu_share),
            tint = contentColor,
            onClick = onShare
        )

        SelectionMenuIcon(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.context_menu_delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

private fun parseColorHex(hex: String): Color {
    return try {
        val sanitized = hex.removePrefix("#")
        val longHex = when (sanitized.length) {
            6 -> "FF$sanitized"
            8 -> sanitized
            else -> "FF000000"
        }
        Color(longHex.toLong(16))
    } catch (_: Exception) {
        Color.Magenta
    }
}
