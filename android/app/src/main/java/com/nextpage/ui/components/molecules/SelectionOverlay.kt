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
 * Shared floating-selection overlay used by both the EPUB reader
 * (Readium) and the PDF rendering path inside the reader screen.
 * Renders exactly one of the following near the selection, based
 * on the input flags and the current [ReaderSelectionState]:
 *
 * | State                           | Surface                       |
 * |---------------------------------|-------------------------------|
 * | [ReaderSelectionState.New]      | [TextSelectionMenu]           |
 * | [ReaderSelectionState.Existing] | [FloatingContextMenu]         |
 * | `showColorPickerPopover=true`   | [HighlightColorPickerPopover] |
 * | `showTagInput=true`             | [AnchoredTagInput]            |
 * | `showDefinitionInput=true`      | [AnchoredDefinitionInput]     |
 *
 * When any surface is visible, a transparent tap-away backdrop is
 * rendered behind it; tapping the backdrop invokes
 * [onDismissContextMenu].
 *
 * Coordinate handling: [selectionRect] arrives in **pixels (px)** —
 * it is Readium's viewport-space [android.graphics.RectF] cast to
 * [Rect]. The composable uses it directly for [IntOffset]
 * positioning and only converts dp→px for the anchor gap and
 * header/footer reserves (see [HEADER_RESERVE_DP] and
 * [FOOTER_RESERVE_DP]).
 *
 * Anchoring rules: the menu is placed above the selection if there
 * is enough room (≥ 80dp from the top of the viewport), otherwise
 * it flips below. Horizontally it is centered on the selection
 * and clamped to the viewport edges. The math is in the private
 * `computeAnchor` helper.
 *
 * @param selectionState Current selection state from the reader
 *   ViewModel. `None` means no surface is shown.
 * @param showColorPickerPopover Show the [HighlightColorPickerPopover]
 *   instead of (or on top of) the text-selection menu. Default
 *   `false`.
 * @param showTagInput Show [AnchoredTagInput]. Default `false`.
 * @param tagSuggestions Suggestions to show in the tag input.
 *   Default empty.
 * @param activeTagText Current tag input value. Default `""`.
 * @param showDefinitionInput Show [AnchoredDefinitionInput]. Default
 *   `false`.
 * @param activeDefinitionText Current definition input value.
 *   Default `""`.
 * @param selectionRect Selection bounding box in **pixels**. When
 *   `null`, the composable returns immediately (renders nothing).
 * @param selectedText The currently selected text. Used as the
 *   "word" header in the definition input and to drive default
 *   highlight color resolution. May be `null`.
 * @param highlights All highlights in the current book. Used to
 *   resolve the default highlight color (last highlight's color, or
 *   YELLOW as fallback) when [activeHighlightColor] is null.
 * @param activeHighlightColor Currently active highlight color
 *   (hex). Drives the Palette icon tint. When `null`, falls back to
 *   YELLOW or the last highlight's color.
 * @param customHighlightColors User-customized 5-color palette for
 *   the [HighlightColorPickerPopover]. Default `null` (uses
 *   [DEFAULT_HIGHLIGHT_PRESETS]).
 * @param onColorSelected Invoked with the chosen hex when a color
 *   is picked from the popover.
 * @param onCopy Copy-selection callback (from the text-selection
 *   menu).
 * @param onDismissContextMenu Tap-away backdrop callback.
 * @param onDelete Delete-highlight callback (from the existing-
 *   highlight menu).
 * @param onAddTag Open-tag-input callback.
 * @param onAnnotate Open-annotation-modal callback.
 * @param onShare Share-selection callback.
 * @param onDictionary Open-dictionary-input callback.
 * @param onShowColorPickerPopover Open-color-picker callback
 *   (from the Palette action). Default no-op.
 * @param onDismissColorPickerPopover Close-color-picker callback.
 *   Default no-op.
 * @param onTagTextChanged Tag-input change callback. Default no-op.
 * @param onSaveTag Save-tag callback. Default no-op.
 * @param onDismissTagInput Close-tag-input callback. Default no-op.
 * @param onDefinitionTextChanged Definition-input change callback.
 *   Default no-op.
 * @param onSaveDefinition Save-definition callback. Default no-op.
 * @param onDismissDefinitionInput Close-definition-input callback.
 *   Default no-op.
 * @param modifier Modifier applied to the positioned `Box` of the
 *   menus (not the popover, which uses its own offset).
 *
 * **Visual**: backdrop = transparent tap-away layer (`fillMaxSize`).
 *   Each menu is wrapped in a `Box` with an 8dp padding and
 *   positioned via `Modifier.offset { anchor }` where `anchor` is
 *   computed from the selection rect. The color-picker popover is
 *   offset to (anchorCenterX - 110dp, selectionRect.bottom + 12dp).
 * **Behavior**: branches are mutually exclusive at the top level
 *   (only one menu is shown at a time). Tapping outside any menu
 *   dismisses via the tap-away backdrop. Tapping a menu action
 *   fires the respective callback (caller decides whether to also
 *   close the surface).
 * **Recomposition**: recomposes when any parameter changes. Each
 *   branch tracks its own `menuWidthPx`/`menuHeightPx` via
 *   `onGloballyPositioned` to compute the anchor.
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
            val defaultColor = HighlightColor.YELLOW.hex
            val paletteColors = customHighlightColors
                ?: HighlightColor.defaultHexList()

            TextSelectionMenu(
                paletteColors = paletteColors,
                selectedColor = activeHighlightColor ?: defaultColor,
                onColorSelected = onColorSelected,
                onCopy = onCopy,
                onDictionary = onDictionary,
                onShare = onShare
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
