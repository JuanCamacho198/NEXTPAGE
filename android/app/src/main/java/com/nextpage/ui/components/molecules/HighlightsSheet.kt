package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor

/**
 * Bottom sheet showing all book highlights with color filter chips.
 *
 * Design: drag handle, header "Resaltados", scrollable color filter chips
 * (Todos / Amarillo / Verde / etc.), highlights list as cards with color
 * marker border + text preview + location. Tap navigates + closes sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsSheet(
    highlights: List<Highlight>,
    onHighlightSelected: (Highlight) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedColorFilter by remember { mutableStateOf<String?>(null) }

    val colorFilterLabels = listOf(
        null to stringResource(R.string.highlight_filter_all)
    ) + HighlightColor.entries.map { color ->
        color.name to when (color) {
            HighlightColor.YELLOW -> stringResource(R.string.highlight_filter_yellow)
            HighlightColor.GREEN -> stringResource(R.string.highlight_filter_green)
            HighlightColor.PINK -> stringResource(R.string.highlight_filter_pink)
            HighlightColor.BLUE -> stringResource(R.string.highlight_filter_blue)
            HighlightColor.PURPLE -> stringResource(R.string.highlight_filter_purple)
        }
    }

    val filteredHighlights = if (selectedColorFilter == null) {
        highlights
    } else {
        highlights.filter { it.color.equals(selectedColorFilter, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161F33),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Drag Handle ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4A5568))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.highlights_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFDDE2F8),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Color Filter Chips ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorFilterLabels.forEach { (filterValue, label) ->
                    val isSelected = selectedColorFilter == filterValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedColorFilter = filterValue },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFF0D1322) else Color(0xFFDDE2F8)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF2F3445),
                            selectedContainerColor = Color(0xFFADC6FF)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color(0xFF2F3445))

            Spacer(modifier = Modifier.height(8.dp))

            // ── Highlights List ────────────────────────────────────
            if (filteredHighlights.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.highlights_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF718096)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredHighlights, key = { it.id }) { highlight ->
                        HighlightCard(
                            highlight = highlight,
                            onClick = { onHighlightSelected(highlight) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(
    highlight: Highlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorHex = highlight.color.let { colorStr ->
        HighlightColor.fromHex(colorStr)?.hex ?: "#FDE047"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color marker border (left edge)
        Box(
            modifier = Modifier
                .size(4.dp, 48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(parseColorHex(colorHex))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlight.textContent,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDDE2F8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = highlight.note?.take(60)?.let { "\"$it\"" } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF718096),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
