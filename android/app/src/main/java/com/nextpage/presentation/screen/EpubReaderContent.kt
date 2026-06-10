package com.nextpage.presentation.screen

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.HighlightColor
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.ui.components.molecules.EpubWebView
import com.nextpage.ui.components.molecules.FloatingContextMenu
import com.nextpage.ui.components.molecules.TextSelectionMenu

/**
 * EPUB-specific reader content.
 *
 * Renders the HTML content via [EpubWebView], provides tap zones for
 * page navigation, and overlays the text selection color picker and
 * floating context menu when text is selected.
 */
@Composable
fun EpubReaderContent(
    htmlContent: String?,
    settings: ReaderSettings = ReaderSettings(),
    filePath: String? = null,
    epubContentLoader: EpubContentLoader? = null,
    chapterHref: String? = null,
    showColorPicker: Boolean,
    showContextMenu: Boolean,
    selectionRect: Rect?,
    selectedText: String?,
    highlights: List<Highlight>,
    onTapZone: (Boolean) -> Unit,
    onTextSelectionEvent: (text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
    onSearchResults: (String) -> Unit = {},
    onHighlightTapped: (highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _, _ -> },
    onColorSelected: (String) -> Unit,
    onCopy: () -> Unit,
    onShowContextMenu: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onAddTag: () -> Unit = {},
    onAddNote: () -> Unit = {},
    onAddComment: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            if (htmlContent != null) {
                EpubWebView(
                    htmlContent = htmlContent,
                    filePath = filePath,
                    epubContentLoader = epubContentLoader,
                    chapterHref = chapterHref,
                    leftMarginPx = settings.layoutPrefs.leftMargin,
                    rightMarginPx = settings.layoutPrefs.rightMargin,
                    bgColor = settings.theme.bgHex,
                    textColor = settings.theme.textHex,
                    fontSizePx = settings.fontSize.sizePx,
                    lineHeight = settings.lineHeight.value,
                    onTextSelectionEvent = onTextSelectionEvent,
                    onSearchResults = onSearchResults,
                    onHighlightTapped = onHighlightTapped,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFADC6FF))
                }
            }

            // Left tap zone (30% width) — previous page/chapter
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterStart)
                    .clickable { onTapZone(true) }
            )

            // Right tap zone (30% width) — next page/chapter
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterEnd)
                    .clickable { onTapZone(false) }
            )
        }

        // ── Text Selection Overlay ───────────────────────────────
        if (showColorPicker && selectionRect != null) {
            val defaultColor = selectedText?.let {
                highlights.lastOrNull()?.color?.let { color ->
                    HighlightColor.fromHex(color)?.hex
                } ?: HighlightColor.YELLOW.hex
            } ?: HighlightColor.YELLOW.hex

            Box(
                modifier = Modifier
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

        // ── Floating Context Menu ────────────────────────────────
        if (showContextMenu && selectionRect != null) {
            Box(
                modifier = Modifier
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
}
