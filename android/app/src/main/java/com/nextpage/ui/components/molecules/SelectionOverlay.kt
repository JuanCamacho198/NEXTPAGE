package com.nextpage.ui.components.molecules

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.presentation.viewmodel.reader.ReaderSelectionState

/**
 * Shared floating selection overlay used by both the EPUB reader and
 * the PDF rendering path in [ReaderScreen].
 *
 * Positions the appropriate floating UI near the selection based on
 * [ReaderSelectionState]:
 * - [ReaderSelectionState.New] → [TextSelectionMenu]
 * - [ReaderSelectionState.Existing] → [FloatingContextMenu]
 * - tag input → [AnchoredTagInput]
 * - definition input → [AnchoredDefinitionInput]
 * - colour picker popover → [HighlightColorPickerPopover]
 *
 * A transparent tap-away overlay is rendered behind the menus when
 * any surface is visible — tapping it calls [onDismissContextMenu].
 *
 * Coordinate handling: [selectionRect] arrives in **pixels (px)** — it is
 * Readium's viewport-space [android.graphics.RectF] cast to [Rect]. We
 * therefore use it directly for [IntOffset] positioning and only convert
 * dp→px for the gap and header/footer reserves.
 */
@Composable
fun SelectionOverlay(
    selectionState: ReaderSelectionState,
    showColorPickerPopover: Boolean = false,
    showTagInput: Boolean = false,
    tagSuggestions: List<String> = emptyList(),
    activeTagText: String = "",
    showDefinitionInput: Boolean = false,
    activeDefinitionText: String = "",
    selectionRect: Rect?,
    selectedText: String?,
    highlights: List<Highlight>,
    activeHighlightColor: String?,
    customHighlightColors: List<String>? = null,
    onColorSelected: (String) -> Unit,
    onCopy: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onAnnotate: () -> Unit,
    onShare: () -> Unit,
    onDictionary: () -> Unit,
    onShowColorPickerPopover: () -> Unit = {},
    onDismissColorPickerPopover: () -> Unit = {},
    onTagTextChanged: (String) -> Unit = {},
    onSaveTag: () -> Unit = {},
    onDismissTagInput: () -> Unit = {},
    onDefinitionTextChanged: (String) -> Unit = {},
    onSaveDefinition: () -> Unit = {},
    onDismissDefinitionInput: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val anyMenuVisible = selectionState != ReaderSelectionState.None ||
        showColorPickerPopover ||
        showTagInput ||
        showDefinitionInput

    if (selectionRect == null) return

    val density = LocalDensity.current
    val selectionRectPx = selectionRect
    val viewportWidth = LocalView.current.width
    val viewportHeight = LocalView.current.height

    // ── Tap-away dismiss overlay (behind menus) ──────────────────
    if (anyMenuVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismissContextMenu()
                }
        )
    }

    // ── Text selection menu (new selection) ──────────────────────
    if (selectionState is ReaderSelectionState.New && !showTagInput && !showDefinitionInput && !showColorPickerPopover) {
        var menuWidthPx by remember { mutableIntStateOf(0) }
        var menuHeightPx by remember { mutableIntStateOf(0) }

        val anchor = computeAnchor(
            selectionRectPx = selectionRectPx,
            menuWidthPx = menuWidthPx,
            menuHeightPx = menuHeightPx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            gapDp = 8,
            density = density
        )

        Box(
            modifier = modifier
                .offset { anchor }
                .onGloballyPositioned { coords ->
                    menuWidthPx = coords.size.width
                    menuHeightPx = coords.size.height
                }
                .padding(8.dp)
        ) {
            val defaultColor = selectedText?.let {
                highlights.lastOrNull()?.color?.let { color ->
                    HighlightColor.fromHex(color)?.hex
                } ?: HighlightColor.YELLOW.hex
            } ?: HighlightColor.YELLOW.hex

            TextSelectionMenu(
                selectedColor = activeHighlightColor ?: defaultColor,
                onColorSelected = onShowColorPickerPopover,
                onCopy = onCopy,
                onDictionary = onDictionary,
                onShare = onShare,
                onAnnotate = onAnnotate
            )
        }
    }

    // ── Existing-highlight context menu ──────────────────────────
    if (selectionState is ReaderSelectionState.Existing && !showTagInput && !showDefinitionInput && !showColorPickerPopover) {
        var menuWidthPx by remember { mutableIntStateOf(0) }
        var menuHeightPx by remember { mutableIntStateOf(0) }

        val anchor = computeAnchor(
            selectionRectPx = selectionRectPx,
            menuWidthPx = menuWidthPx,
            menuHeightPx = menuHeightPx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            gapDp = 8,
            density = density
        )

        Box(
            modifier = modifier
                .offset { anchor }
                .onGloballyPositioned { coords ->
                    menuWidthPx = coords.size.width
                    menuHeightPx = coords.size.height
                }
                .padding(8.dp)
        ) {
            FloatingContextMenu(
                selectedColor = activeHighlightColor ?: HighlightColor.YELLOW.hex,
                onColorSelected = onShowColorPickerPopover,
                onCopy = onCopy,
                onAddTag = onAddTag,
                onAnnotate = onAnnotate,
                onShare = onShare,
                onDelete = onDelete
            )
        }
    }

    // ── Anchored tag input ───────────────────────────────────────
    if (showTagInput) {
        var menuWidthPx by remember { mutableIntStateOf(0) }
        var menuHeightPx by remember { mutableIntStateOf(0) }

        val anchor = computeAnchor(
            selectionRectPx = selectionRectPx,
            menuWidthPx = menuWidthPx,
            menuHeightPx = menuHeightPx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            gapDp = 8,
            density = density
        )

        Box(
            modifier = modifier
                .offset { anchor }
                .onGloballyPositioned { coords ->
                    menuWidthPx = coords.size.width
                    menuHeightPx = coords.size.height
                }
                .padding(8.dp)
        ) {
            AnchoredTagInput(
                tag = activeTagText,
                suggestions = tagSuggestions,
                onTagChange = onTagTextChanged,
                onSuggestionClick = { tag ->
                    onTagTextChanged(tag)
                    onSaveTag()
                },
                onSave = onSaveTag,
                onDismiss = onDismissTagInput
            )
        }
    }

    // ── Anchored definition input ────────────────────────────────
    if (showDefinitionInput) {
        var menuWidthPx by remember { mutableIntStateOf(0) }
        var menuHeightPx by remember { mutableIntStateOf(0) }

        val anchor = computeAnchor(
            selectionRectPx = selectionRectPx,
            menuWidthPx = menuWidthPx,
            menuHeightPx = menuHeightPx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            gapDp = 8,
            density = density
        )

        Box(
            modifier = modifier
                .offset { anchor }
                .onGloballyPositioned { coords ->
                    menuWidthPx = coords.size.width
                    menuHeightPx = coords.size.height
                }
                .padding(8.dp)
        ) {
            AnchoredDefinitionInput(
                word = selectedText ?: "",
                definition = activeDefinitionText,
                onDefinitionChange = onDefinitionTextChanged,
                onSave = onSaveDefinition,
                onDismiss = onDismissDefinitionInput
            )
        }
    }

    // ── Colour picker popover ────────────────────────────────────
    if (showColorPickerPopover) {
        val anchorCenterX = selectionRectPx.left + selectionRectPx.width() / 2
        val anchorBelowY = selectionRectPx.bottom + with(density) { 12.dp.toPx() }.toInt()

        HighlightColorPickerPopover(
            customColors = customHighlightColors,
            onColorSelected = { color ->
                onColorSelected(color)
                onDismissColorPickerPopover()
            },
            onDismiss = onDismissColorPickerPopover,
            anchorX = anchorCenterX,
            anchorY = anchorBelowY,
            modifier = Modifier.offset {
                val x = (anchorCenterX - 110.dp.toPx().toInt()).coerceAtLeast(0)
                IntOffset(x, anchorBelowY)
            }
        )
    }
}

/**
 * Computes the [IntOffset] (px) to anchor the floating menu near the
 * selection. Default is above the selection; if there isn't enough room,
 * it flips below. Horizontally clamped so the menu never overflows the
 * right edge and never starts before the left edge.
 */
private fun computeAnchor(
    selectionRectPx: Rect,
    menuWidthPx: Int,
    menuHeightPx: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    gapDp: Int,
    density: Density
): IntOffset {
    val gapPx = with(density) { gapDp.dp.toPx() }.toInt()
    val headerReservePx = with(density) { HEADER_RESERVE_DP.dp.toPx() }.toInt()
    val footerReservePx = with(density) { FOOTER_RESERVE_DP.dp.toPx() }.toInt()

    // ── Vertical: prefer above, flip below if it would tuck under the header,
    // but also guard the bottom so it never overflows the footer/IME. ──
    val aboveTop = (selectionRectPx.top - menuHeightPx - gapPx).coerceAtLeast(0)
    val belowTop = selectionRectPx.bottom + gapPx
    val placeAbove = selectionRectPx.top - menuHeightPx - gapPx >= headerReservePx
    val fitsBelow = belowTop + menuHeightPx <= viewportHeight - footerReservePx
    val y = when {
        placeAbove -> aboveTop
        fitsBelow -> belowTop
        else -> aboveTop // not enough room below either — stay above (clamped)
    }

    // ── Horizontal: center on selection, clamp to viewport ──
    val selectionCenterX = selectionRectPx.left + (selectionRectPx.width() / 2)
    val rawX = selectionCenterX - (menuWidthPx / 2)
    val maxLeft = (viewportWidth - menuWidthPx).coerceAtLeast(0)
    val x = rawX.coerceIn(0, maxLeft)

    return IntOffset(x, y)
}

/** Reserve at the top of the viewport (header + status bar) — don't let
 *  the menu hide behind the chrome. */
private const val HEADER_RESERVE_DP = 80

/** Reserve at the bottom (progress bar + nav) so the menu never tucks
 *  behind the footer when flipped below. */
private const val FOOTER_RESERVE_DP = 72
