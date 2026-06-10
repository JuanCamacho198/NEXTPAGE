package com.nextpage.ui.components.molecules

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.nextpage.data.epub.EpubContentLoader

/**
 * Injects NextPage's reader CSS into the chapter HTML for beautiful rendering.
 * Colors match the design tokens.
 */
fun readerCss(
    bgColor: String = "#0D1322",
    textColor: String = "#DDE2F8",
    fontSizePx: Int = 20,
    lineHeight: Float = 1.6f,
    leftMarginPx: Int = 16,
    rightMarginPx: Int = 16
): String {
    return """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            background-color: $bgColor;
            color: $textColor;
            font-size: ${fontSizePx}px;
            line-height: $lineHeight;
            font-family: 'Georgia', 'Noto Serif', serif;
            padding: 20px ${rightMarginPx}px 20px ${leftMarginPx}px;
            max-width: 100%;
            word-wrap: break-word;
            overflow-x: hidden;
        }
        h1, h2, h3, h4, h5, h6 {
            color: $textColor;
            margin-top: 1.2em;
            margin-bottom: 0.5em;
            line-height: 1.3;
        }
        h1 { font-size: 1.6em; }
        h2 { font-size: 1.4em; }
        h3 { font-size: 1.2em; }
        p {
            margin-bottom: 0.8em;
            text-align: justify;
        }
        img {
            max-width: 100% !important;
            height: auto;
            display: block;
            margin: 1em auto;
            border-radius: 4px;
        }
        a { color: #3B82F6; text-decoration: none; }
        a:hover { text-decoration: underline; }
        blockquote {
            border-left: 3px solid #3B82F6;
            padding: 8px 12px;
            margin: 1em 0;
            color: #94A3B8;
            background-color: rgba(59, 130, 246, 0.05);
            border-radius: 0 4px 4px 0;
        }
        blockquote p { margin-bottom: 0; }
        ul, ol {
            padding-left: 1.5em;
            margin-bottom: 0.8em;
        }
        li { margin-bottom: 0.3em; }
        pre, code {
            font-family: 'Courier New', monospace;
            background-color: rgba(255, 255, 255, 0.05);
            border-radius: 3px;
            padding: 0.2em 0.4em;
            font-size: 0.9em;
        }
        pre {
            padding: 1em;
            overflow-x: auto;
            margin-bottom: 0.8em;
        }
        pre code { background: none; padding: 0; }
        hr {
            border: none;
            border-top: 1px solid rgba(255, 255, 255, 0.1);
            margin: 1.5em 0;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 0.8em;
        }
        th, td {
            border: 1px solid rgba(255, 255, 255, 0.1);
            padding: 8px;
            text-align: left;
        }
        th { background-color: rgba(255, 255, 255, 0.05); }
        ::selection {
            background-color: rgba(59, 130, 246, 0.3);
            color: #FFFFFF;
        }
    """.trimIndent()
}

/**
 * Wraps HTML content in a full document with viewport meta and injected CSS.
 */
internal fun wrapHtmlContent(htmlContent: String, css: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>$css</style>
        </head>
        <body>$htmlContent</body>
        </html>
    """.trimIndent()
}

/**
 * JavaScript interface bridge for communication from WebView to Kotlin.
 *
 * Enhanced with text selection and search callbacks.
 */
class ReaderJsBridge(
    private val onTextSelected: (String) -> Unit = {},
    private val onScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onTextSelectionEvent: (text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
    private val onSearchResults: (String) -> Unit = {},
    private val onHighlightTapped: (highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _, _ -> },
    private val onPageChanged: (page: Int) -> Unit = {},
    private val onDocumentLoaded: (totalPages: Int) -> Unit = {}
) {
    @JavascriptInterface
    fun onTextSelected(text: String) {
        onTextSelected(text)
    }

    @JavascriptInterface
    fun onScrollChanged(scrollTop: Int, scrollHeight: Int) {
        onScrollChanged(scrollTop, scrollHeight)
    }

    @JavascriptInterface
    fun onTextSelectionEvent(text: String, left: Float, top: Float, right: Float, bottom: Float) {
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
}

/**
 * JavaScript for text search within the WebView.
 * Uses a TreeWalker to scan all text nodes, finds matches,
 * and sends results back via NextPageBridge.onSearchResults().
 */
internal fun injectSearchJs(query: String): String {
    val escapedQuery = query.replace("'", "\\'")
    return """
        (function() {
            const query = '$escapedQuery';
            const results = [];
            const walker = document.createTreeWalker(document.body, 4, null, false);
            let node;
            while (node = walker.nextNode()) {
                const text = node.textContent;
                let idx = text.toLowerCase().indexOf(query.toLowerCase());
                while (idx !== -1) {
                    const range = document.createRange();
                    range.setStart(node, idx);
                    range.setEnd(node, idx + query.length);
                    const rect = range.getBoundingClientRect();
                    results.push(JSON.stringify({
                        text: text.substring(Math.max(0, idx - 30), idx + query.length + 30),
                        offset: idx,
                        page: window.pageYOffset + rect.top,
                        chapterIndex: 0,
                        rect: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom }
                    }));
                    idx = text.indexOf(query.toLowerCase(), idx + 1);
                }
            }
            NextPageBridge.onSearchResults('[' + results.join(',') + ']');
        })();
    """.trimIndent()
}

/**
 * JavaScript for detecting text selection in the WebView.
 * Listens for selectionchange events, debounces at 300ms,
 * and sends selection details via NextPageBridge.onTextSelectionEvent().
 */
internal fun injectSelectionJs(): String {
    return """
        (function() {
            document.addEventListener('selectionchange', function() {
                const sel = window.getSelection();
                if (!sel || sel.isCollapsed || sel.toString().trim() === '') return;
                const range = sel.getRangeAt(0);
                const rect = range.getBoundingClientRect();
                clearTimeout(window._selTimeout);
                window._selTimeout = setTimeout(function() {
                    NextPageBridge.onTextSelectionEvent(
                        sel.toString(),
                        rect.left, rect.top, rect.right, rect.bottom
                    );
                }, 300);
            });
        })();
    """.trimIndent()
}

/**
 * JavaScript for detecting taps on [data-highlight-id] elements.
 * Fires NextPageBridge.onHighlightTapped with the highlight's bounding rect.
 */
internal fun injectHighlightTapJs(): String {
    return """
        (function() {
            document.addEventListener('click', function(e) {
                var target = e.target.closest('[data-highlight-id]');
                if (!target) return;
                e.stopPropagation();
                var rect = target.getBoundingClientRect();
                var id = target.getAttribute('data-highlight-id');
                NextPageBridge.onHighlightTapped(id, target.textContent, rect.left, rect.top, rect.right, rect.bottom);
            });
        })();
    """.trimIndent()
}

/**
 * JavaScript for applying highlight color to selected text in the WebView.
 * Surrounds the current selection with a <span> styled with the given color.
 */
internal fun injectHighlightCss(highlightColor: String, highlightId: String): String {
    return """
        (function() {
            const sel = window.getSelection();
            if (!sel || sel.isCollapsed) return;
            const range = sel.getRangeAt(0);
            const span = document.createElement('span');
            span.style.backgroundColor = '$highlightColor';
            span.style.color = 'inherit';
            span.style.borderRadius = '2px';
            span.setAttribute('data-highlight-id', '$highlightId');
            try {
                range.surroundContents(span);
            } catch(e) {
                // Fallback: wrap contents
                const fragment = range.extractContents();
                span.appendChild(fragment);
                range.insertNode(span);
            }
            sel.removeAllRanges();
        })();
    """.trimIndent()
}

/**
 * EpubWebView renders EPUB chapter HTML using Android's WebView with
 * NextPage's custom CSS for an optimal reading experience.
 *
 * Enhanced features:
 * - Injects CSS for dark theme, font size, line height
 * - JavaScript bridge for text selection and scroll events
 * - Search JS injection for in-book text search
 * - Text selection injection for detecting selected text + position
 * - Highlight CSS injection for applying highlight colors
 * - Responsive to settings changes via recomposition
 *
 * Image handling:
 * EPUB images are served via [WebViewAssetLoader] with a custom [EpubFileHandler].
 * The [chapterHref] determines the base URL so relative image paths resolve correctly.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubWebView(
    htmlContent: String,
    filePath: String? = null,
    epubContentLoader: EpubContentLoader? = null,
    chapterHref: String? = null,
    leftMarginPx: Int = 16,
    rightMarginPx: Int = 16,
    bgColor: String = "#0D1322",
    textColor: String = "#DDE2F8",
    fontSizePx: Int = 20,
    lineHeight: Float = 1.6f,
    onTextSelected: (String) -> Unit = {},
    onTextSelectionEvent: (text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
    onSearchResults: (String) -> Unit = {},
    onHighlightTapped: (highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val jsBridge = remember(onTextSelected, onTextSelectionEvent, onSearchResults, onHighlightTapped) {
        ReaderJsBridge(
            onTextSelected = onTextSelected,
            onTextSelectionEvent = onTextSelectionEvent,
            onSearchResults = onSearchResults,
            onHighlightTapped = onHighlightTapped
        )
    }

    val epubHandler = remember { EpubFileHandler() }

    // Compute the base URL from the chapter's directory so relative image paths resolve
    val chapterDir = remember(chapterHref) {
        chapterHref?.substringBeforeLast("/", "") ?: ""
    }
    val baseUrl = "https://appassets.androidplatform.net/epub/$chapterDir/"

    // Precompute the full HTML + CSS so update only needs to load it
    val renderedHtml = remember(htmlContent, bgColor, textColor, fontSizePx, lineHeight, leftMarginPx, rightMarginPx) {
        val css = readerCss(bgColor, textColor, fontSizePx, lineHeight, leftMarginPx, rightMarginPx)
        wrapHtmlContent(htmlContent, css)
    }

    // Sync handler file path — re-run when filePath or loader changes
    remember(filePath, epubContentLoader) {
        epubHandler.setLoader(filePath, epubContentLoader)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false

                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/epub/", epubHandler)
                    .build()

                webViewClient = object : WebViewClient() {
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (request.url.host != "appassets.androidplatform.net") return null
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }

                webChromeClient = WebChromeClient()

                // Suppress the default Android floating selection toolbar
                // (Copy / Share / Select All) using Callback2 which handles
                // FloatingActionMode on API 23+ (our minSdk 26).
                // Reflection is required because Kotlin 1.9 can't synthesize
                // a property from the deprecated Java getter/setter methods.
                @Suppress("DEPRECATION")
                val suppressionCallback = object : ActionMode.Callback2() {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = false
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                    override fun onGetContentRect(mode: ActionMode, view: View?, outRect: Rect?) {
                        outRect?.set(0, 0, 0, 0)
                    }
                }
                try {
                    val method = WebView::class.java.getMethod(
                        "setCustomSelectionActionModeCallback",
                        ActionMode.Callback::class.java
                    )
                    method.invoke(this, suppressionCallback)
                } catch (_: Exception) {
                    // Fallback: allow default ActionMode
                }

                addJavascriptInterface(jsBridge, "NextPageBridge")
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(baseUrl, renderedHtml, "text/html", "UTF-8", null)
            webView.evaluateJavascript(injectSelectionJs(), null)
            webView.evaluateJavascript(injectHighlightTapJs(), null)
        }
    )
}
