package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
 * Floating color picker bar that appears near selected text.
 *
 * Design: horizontal row of 5 color circles (32dp) + vertical divider
 * + Copy icon button. Background #161F33FF, cornerRadius 9999, shadow.
 *
 * @param selectedColor The currently active highlight color hex
 * @param onColorSelected Called when a color circle is tapped
 * @param onCopy Called when the Copy button is tapped
 * @param onExpand Called when user wants to see the full context menu
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TextSelectionMenu(
    selectedColor: String = HighlightColor.YELLOW.hex,
    onColorSelected: (String) -> Unit,
    onCopy: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorEntries = HighlightColor.entries

    Row(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(9999.dp))
            .background(
                color = Color(0xFF161F33),
                shape = RoundedCornerShape(9999.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 5 color circles
        colorEntries.forEach { highlightColor ->
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

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .size(24.dp, 1.dp)
                .background(Color(0xFF4A5568))
        )

        // Copy button
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.text_selection_copy),
                tint = Color(0xFFADC6FF),
                modifier = Modifier.size(16.dp)
            )
        }
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
