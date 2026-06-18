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
 * Horizontal floating context menu shown when the user taps an existing highlight.
 *
 * Design node `FaPN3` — a single pill of icon actions separated by thin
 * vertical dividers:
 *
 *     [Palette] | [Copy] | [Tag] | [Annotate] | [Share] | [Delete]
 *
 * Colours are derived from [MaterialTheme.colorScheme] so the menu works in
 * both light and dark themes.
 *
 * @param selectedColor currently active highlight color (drives the Palette icon tint)
 * @param onColorSelected called when the Palette action is tapped
 * @param onCopy copy to clipboard
 * @param onAddTag open the anchored tag input
 * @param onAnnotate open the note modal
 * @param onShare share the highlight text
 * @param onDelete delete the selected highlight
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
    val sanitized = hex.removePrefix("#")
    val longHex = when (sanitized.length) {
        6 -> "FF$sanitized"
        8 -> sanitized
        else -> "FF000000"
    }
    return Color(longHex.toLong(16))
}
