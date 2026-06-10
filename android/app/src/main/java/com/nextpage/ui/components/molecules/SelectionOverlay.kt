package com.nextpage.ui.components.molecules

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor

/**
 * Shared floating selection overlay used by both [EpubReaderContent] and
 * the PDF rendering path in [ReaderScreen].
 *
 * Positions the [TextSelectionMenu] (color picker bar) or
 * [FloatingContextMenu] (expanded context menu) anchored above the
 * [selectionRect].
 *
 * Design matches Pencil Node ID cnVL6:
 * - 5 color circles (YELLOW, GREEN, PINK, BLUE, PURPLE)
 * - Copy button
 * - Expandable to full context menu (tag, note, comment, share, delete)
 */
@Composable
fun SelectionOverlay(
    showColorPicker: Boolean,
    showContextMenu: Boolean,
    selectionRect: Rect?,
    selectedText: String?,
    highlights: List<Highlight>,
    onColorSelected: (String) -> Unit,
    onCopy: () -> Unit,
    onShowContextMenu: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onAddTag: () -> Unit,
    onAddNote: () -> Unit,
    onAddComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectionRect == null) return

    // ── Color Picker ───────────────────────────────────────────────
    if (showColorPicker) {
        val defaultColor = selectedText?.let {
            highlights.lastOrNull()?.color?.let { color ->
                HighlightColor.fromHex(color)?.hex
            } ?: HighlightColor.YELLOW.hex
        } ?: HighlightColor.YELLOW.hex

        Box(
            modifier = modifier
                .offset { IntOffset(selectionRect.left, (selectionRect.top - 120).coerceAtLeast(0)) }
                .padding(8.dp)
        ) {
            TextSelectionMenu(
                selectedColor = defaultColor,
                onColorSelected = onColorSelected,
                onCopy = onCopy,
                onExpand = onShowContextMenu
            )
        }
    }

    // ── Expanded Context Menu ──────────────────────────────────────
    if (showContextMenu) {
        Box(
            modifier = modifier
                .offset {
                    IntOffset(
                        selectionRect.left,
                        (selectionRect.top - 300).coerceAtLeast(0)
                    )
                }
                .padding(8.dp)
        ) {
            FloatingContextMenu(
                selectedColor = HighlightColor.YELLOW.hex,
                onColorSelected = onColorSelected,
                onCopy = onCopy,
                onAddTag = onAddTag,
                onAddNote = onAddNote,
                onAddComment = onAddComment,
                onShare = onShare,
                onDelete = onDismissContextMenu,
                onDismiss = onDismissContextMenu
            )
        }
    }
}
