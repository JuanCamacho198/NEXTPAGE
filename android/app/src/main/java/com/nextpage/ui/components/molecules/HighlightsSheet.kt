package com.nextpage.ui.components.molecules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor

/**
 * Modal bottom sheet that lists all highlights for the current book
 * with a horizontal color-filter chip row. Tapping a highlight
 * invokes [onHighlightSelected]; the sheet does NOT auto-dismiss
 * (the parent typically navigates instead).
 *
 * Visual design is locked to the dark reader theme (background
 * `#161F33`, text `#DDE2F8`, accent `#ADC6FF`) — this sheet is
 * intended to be shown on top of the dark reading surface.
 *
 * @param highlights All highlights for the book, in the order they
 *   should be displayed. Filtered in-memory by the active color
 *   filter; not re-queried.
 * @param onHighlightSelected Invoked with the tapped [Highlight] when
 *   the user taps a row. The sheet stays open; the caller is
 *   expected to close it after navigation completes.
 * @param onDismiss Invoked on swipe-down, scrim-tap, or back-press.
 * @param modifier Modifier applied to the inner `Column`.
 *
 * **Visual**: dark `ModalBottomSheet` (24dp top corners). Drag
 *   handle, "Highlights" `titleLarge` bold header, a horizontal-
 *   scrolling row of color filter `FilterChip`s (one "All" chip
 *   shown as a dashed stroked circle, the rest as filled colored
 *   circles), a `HorizontalDivider`, then a 360dp `LazyColumn` of
 *   `HighlightCard`s. Each card: 4dp left color marker + 2-line
 *   text preview + 1-line note preview.
 * **Behavior**: filter chips update `selectedColorFilter` locally
 *   (no parent round-trip). `filteredHighlights` is recomputed on
 *   each composition. Tapping a chip or highlight does NOT call
 *   [onDismiss] — the parent is in charge. The empty state shows
 *   `R.string.highlights_empty` when no highlights match the filter.
 * **Recomposition**: recomposes when `highlights` or callbacks
 *   change. Internal filter state is `remember`-ed.
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
            HighlightColor.ORANGE -> stringResource(R.string.highlight_filter_orange)
            HighlightColor.BLUE -> stringResource(R.string.highlight_filter_blue)
            HighlightColor.RED -> stringResource(R.string.highlight_filter_red)
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

            // ── Color Filter Chips (colored circles, no text) ──────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorFilterLabels.forEach { (filterValue, label) ->
                    val isSelected = selectedColorFilter == filterValue
                    val colorName = label
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedColorFilter = filterValue },
                        label = {
                            ColorFilterCircle(
                                filterValue = filterValue,
                                isSelected = isSelected
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.semantics {
                            contentDescription = colorName
                        }
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
        HighlightColor.fromHex(colorStr)?.hex ?: HighlightColor.YELLOW.hex
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
                text = highlight.textContent.replace("\\n", " ").replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDDE2F8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =                 highlight.note?.take(60) ?: "",
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

@Composable
private fun ColorFilterCircle(
    filterValue: String?,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color.White else Color(0xFF4A5568)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (filterValue == null) {
            // "All" — dashed stroked circle (no fill)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(
                    width = borderWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                )
                drawCircle(
                    color = borderColor,
                    radius = size.minDimension / 2f - borderWidth.toPx() / 2f,
                    style = stroke
                )
            }
        } else {
            // Specific color — filled circle with border
            val fillColor = parseColorHex(filterValue)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(width = borderWidth, color = borderColor, shape = CircleShape)
            )
        }
    }
}
