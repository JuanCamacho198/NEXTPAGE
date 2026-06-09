package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.domain.model.HighlightColor

/**
 * Full floating context menu that appears when user expands the color picker.
 *
 * Design: 7 action rows grouped: Color Picker + Copy | Tag | Note | Comment
 * | Share | Delete, with vertical dividers between groups.
 * Background #161F33FF, shadow, rounded corners.
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .background(
                color = Color(0xFF161F33),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp)
    ) {
        // ── Group 1: Color Picker ──────────────────────────────────
        Text(
            text = stringResource(R.string.context_menu_color),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color(0xFF718096),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightColor.entries.forEach { highlightColor ->
                val isActive = selectedColor.equals(highlightColor.hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseColorHex(highlightColor.hex))
                        .then(
                            if (isActive) Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            else Modifier
                        )
                        .clickable { onColorSelected(highlightColor.hex) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Copy icon
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.text_selection_copy),
                tint = Color(0xFFADC6FF),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onCopy() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFF2F3445))

        // ── Group 2: Tag / Note / Comment ──────────────────────────
        ContextMenuAction(
            icon = Icons.Default.ContentCopy,
            label = stringResource(R.string.text_selection_copy),
            onClick = onCopy
        )
        HorizontalDivider(color = Color(0xFF2F3445))

        ContextMenuAction(
            label = stringResource(R.string.context_menu_tag),
            onClick = onAddTag
        )
        HorizontalDivider(color = Color(0xFF2F3445))

        ContextMenuAction(
            label = stringResource(R.string.context_menu_note),
            onClick = onAddNote
        )
        HorizontalDivider(color = Color(0xFF2F3445))

        ContextMenuAction(
            label = stringResource(R.string.context_menu_comment),
            onClick = onAddComment
        )

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF2F3445))

        // ── Group 3: Share / Delete ────────────────────────────────
        ContextMenuAction(
            icon = Icons.Default.Share,
            label = stringResource(R.string.context_menu_share),
            onClick = onShare
        )
        HorizontalDivider(color = Color(0xFF2F3445))

        ContextMenuAction(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.context_menu_delete),
            tint = Color(0xFFEF4444),
            onClick = onDelete
        )
    }
}

@Composable
private fun ContextMenuAction(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = Color(0xFFDDE2F8),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
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
