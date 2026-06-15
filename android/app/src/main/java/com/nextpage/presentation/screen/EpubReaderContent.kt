package com.nextpage.presentation.screen

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.ui.components.molecules.EpubWebView
import com.nextpage.ui.components.molecules.SelectionOverlay

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

            // Left tap zone (15% width) — previous page/chapter
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.15f)
                    .align(Alignment.CenterStart)
                    .clickable { onTapZone(true) }
            )

            // Right tap zone (15% width) — next page/chapter
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.15f)
                    .align(Alignment.CenterEnd)
                    .clickable { onTapZone(false) }
            )

        }

        // ── Text Selection Overlay ───────────────────────────────
        SelectionOverlay(
            showColorPicker = showColorPicker,
            showContextMenu = showContextMenu,
            selectionRect = selectionRect,
            selectedText = selectedText,
            highlights = highlights,
            onColorSelected = onColorSelected,
            onCopy = onCopy,
            onShowContextMenu = onShowContextMenu,
            onDismissContextMenu = onDismissContextMenu,
            onAddTag = onAddTag,
            onAddNote = onAddNote,
            onAddComment = onAddComment,
            onShare = onShare
        )
    }
}
