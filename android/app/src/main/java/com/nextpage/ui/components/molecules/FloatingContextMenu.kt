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
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
 * Horizontal floating context menu shown when the user expands the
 * selection menu.
 *
 * Design node `FaPN3` — a single pill (`cornerRadius = 9999`,
 * background `#161F33`, 6dp padding) of icon buttons separated by
 * thin vertical dividers:
 *
 *     [Palette] | [Copy] | [Tag] | [Note] | [Comment] | [Share] | [Delete]
 *
 * Each action is a 40dp circular icon button. The leading Palette button
 * toggles the inline color row (delegates to [onShowColorRow] if a
 * collapse behavior is desired; otherwise it is a no-op placeholder that
 * the parent can wire up later).
 *
 * @param selectedColor currently active highlight color (drives the
 *  ring on the Palette icon)
 * @param onColorSelected invoked with the chosen [HighlightColor.hex] (legacy;
 *  currently only used by the colour picker bar [cnVL6]).
 * @param onCopy copy to clipboard
 * @param onAddTag / onAddNote / onAddComment / onShare annotation actions
 * @param onDelete delete the selected highlight
 * @param onDismiss dismiss the menu
 * @param onShowColorPicker opens the kixeV colour picker popover from the
 *  Palette button (replaces the old behaviour of re-applying the current colour).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FloatingContextMenu(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onCopy: () -> Unit,
    onAddTag: () -> Unit,
    onAddNote: () -> Unit,
    onAddComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onShowColorRow: () -> Unit = {},
    onShowColorPicker: () -> Unit = {},
    hasActiveHighlight: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(50))
            .background(
                color = Color(0xFF161F33),
                shape = RoundedCornerShape(50)
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Palette (highlight color) ─────────────────────────────
        ContextIconAction(
            icon = Icons.Default.Palette,
            contentDescription = stringResource(R.string.context_menu_color),
            tint = parseColorHex(selectedColor),
            onClick = onShowColorPicker
        )
        MenuDivider()

        // ── Copy ──────────────────────────────────────────────────
        ContextIconAction(
            icon = Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.text_selection_copy),
            onClick = onCopy
        )

        // ── Tag ───────────────────────────────────────────────────
        ContextIconAction(
            icon = Icons.AutoMirrored.Filled.Label,
            contentDescription = stringResource(R.string.context_menu_tag),
            tint = if (hasActiveHighlight) Color(0xFFDDE2F8) else Color(0xFF4A5568),
            enabled = hasActiveHighlight,
            onClick = onAddTag
        )

        // ── Note ──────────────────────────────────────────────────
        ContextIconAction(
            icon = Icons.Default.NoteAlt,
            contentDescription = stringResource(R.string.context_menu_note),
            tint = if (hasActiveHighlight) Color(0xFFDDE2F8) else Color(0xFF4A5568),
            enabled = hasActiveHighlight,
            onClick = onAddNote
        )

        // ── Comment ───────────────────────────────────────────────
        ContextIconAction(
            icon = Icons.AutoMirrored.Filled.Comment,
            contentDescription = stringResource(R.string.context_menu_comment),
            tint = if (hasActiveHighlight) Color(0xFFDDE2F8) else Color(0xFF4A5568),
            enabled = hasActiveHighlight,
            onClick = onAddComment
        )
        MenuDivider()

        // ── Share ─────────────────────────────────────────────────
        ContextIconAction(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.context_menu_share),
            tint = if (hasActiveHighlight) Color(0xFFDDE2F8) else Color(0xFF4A5568),
            enabled = hasActiveHighlight,
            onClick = onShare
        )

        // ── Delete (destructive — red tint) ───────────────────────
        ContextIconAction(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.context_menu_delete),
            tint = if (hasActiveHighlight) Color(0xFFEF4444) else Color(0xFF4A5568),
            enabled = hasActiveHighlight,
            onClick = onDelete
        )
    }
}

/** 40dp circular icon button matching the design's action buttons. */
@Composable
private fun ContextIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color(0xFFDDE2F8),
    enabled: Boolean = true,
    modifier: Modifier = Modifier
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
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Thin vertical divider between menu groups (matches design `#42475440`). */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .size(20.dp)
            .background(Color(0x42475440))
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
