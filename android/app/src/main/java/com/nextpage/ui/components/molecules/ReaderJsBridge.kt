package com.nextpage.ui.components.molecules

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavaScript interface bridge for communication from WebView to Kotlin.
 *
 * Shared between [PdfWebView] (PDF.js) and previously the old [EpubWebView]
 * (removed in Phase 2 of the Readium migration).
 *
 * Each [JavascriptInterface] method is callable from JS via `NextPageBridge.*`.
 */
class ReaderJsBridge(
    private val tag: String = "ReaderJsBridge",
    private val onTextSelected: (String) -> Unit = {},
    private val onScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onTextSelectionEvent: (text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
    private val onSearchResults: (String) -> Unit = {},
    private val onHighlightTapped: (highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _, _ -> },
    private val onPageChanged: (page: Int) -> Unit = {},
    private val onDocumentLoaded: (totalPages: Int) -> Unit = {},
    private val onNavTap: (Boolean) -> Unit = {},
    private val onSelectionCleared: () -> Unit = {}
) {
    @JavascriptInterface
    fun onTextSelected(text: String) {
        Log.d(tag, "onTextSelected: \"${text.take(50)}\"")
        onTextSelected(text)
    }

    @JavascriptInterface
    fun onScrollChanged(scrollTop: Int, scrollHeight: Int) {
        onScrollChanged(scrollTop, scrollHeight)
    }

    @JavascriptInterface
    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) {
        Log.d(tag, "onTextSelectionEvent: \"${text.take(50)}\" rect=($left,$top,$right,$bottom)")
        onTextSelectionEvent(text, left, top, right, bottom)
    }

    @JavascriptInterface
    fun onSearchResults(jsonResults: String) {
        onSearchResults(jsonResults)
    }

    @JavascriptInterface
    fun onChapterEndReached() {
        // Can be used for auto-advance to next chapter
    }

    @JavascriptInterface
    fun onHighlightTapped(highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) {
        onHighlightTapped(highlightId, text, left, top, right, bottom)
    }

    @JavascriptInterface
    fun onPageChanged(page: Int) {
        onPageChanged(page)
    }

    @JavascriptInterface
    fun onDocumentLoaded(totalPages: Int) {
        onDocumentLoaded(totalPages)
    }

    @JavascriptInterface
    fun onNavTap(isLeft: Boolean) {
        onNavTap(isLeft)
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        Log.d(tag, "onSelectionCleared")
        onSelectionCleared()
    }
}
