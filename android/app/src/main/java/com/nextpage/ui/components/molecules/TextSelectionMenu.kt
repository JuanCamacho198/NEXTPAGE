package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.HighlightColor

/**
 * Floating action menu shown when the user selects new text.
 *
 * Design: horizontal pill of icon actions:
 *     [Palette] | [Copy] | [Dictionary] | [Share] | [Annotate]
 *
 * All colours are derived from [MaterialTheme.colorScheme] so the menu adapts
 * to light and dark themes automatically.
 *
 * @param selectedColor currently active highlight color (drives the Palette icon tint)
 * @param onColorSelected called when the Palette action is tapped
 * @param onCopy copy to clipboard
 * @param onDictionary open the definition input
 * @param onShare share the selected text
 * @param onAnnotate open the note modal
 */
@Composable
fun TextSelectionMenu(
    selectedColor: String = HighlightColor.YELLOW.hex,
    onColorSelected: () -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onShare: () -> Unit,
    onAnnotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val contentColor = contentColorFor(containerColor)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(50))
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
            icon = Icons.Default.MenuBook,
            contentDescription = stringResource(R.string.context_menu_dictionary),
            tint = contentColor,
            onClick = onDictionary
        )

        SelectionMenuIcon(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.context_menu_share),
            tint = contentColor,
            onClick = onShare
        )

        SelectionMenuIcon(
            icon = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.context_menu_annotate),
            tint = contentColor,
            onClick = onAnnotate
        )
    }
}

@Composable
internal fun SelectionMenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun MenuVerticalDivider(color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .size(20.dp)
            .background(color)
    )
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
