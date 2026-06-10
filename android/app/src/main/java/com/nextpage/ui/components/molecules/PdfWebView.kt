package com.nextpage.ui.components.molecules

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.nextpage.domain.model.Highlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PdfWebView renders a PDF document using PDF.js in a WebView with a
 * selectable text layer, search support, and highlight capabilities.
 *
 * Architecture:
 * - PDF.js assets are served via [WebViewAssetLoader] from `assets/pdfjs/`
 * - The PDF file data is injected as Base64 chunks through the JavaScript
 *   bridge instead of using fetch() — this avoids all CORS and
 *   WebViewAssetLoader interception issues that occur with loadDataWithBaseURL.
 * - Communication uses [ReaderJsBridge] for text selection, search, and highlights
 * - Page navigation is controlled via JS injection
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PdfWebView(
    filePath: String,
    currentPage: Int,
    searchQuery: String = "",
    highlights: List<Highlight> = emptyList(),
    onPageChanged: (Int) -> Unit = {},
    onDocumentLoaded: (Int) -> Unit = {},
    onTextSelectionEvent: (text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _ -> },
    onSearchResults: (String) -> Unit = {},
    onHighlightTapped: (highlightId: String, text: String, left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf("") }
    var pdfLoaded by remember { mutableStateOf(false) }

    val jsBridge = remember(onTextSelectionEvent, onSearchResults, onHighlightTapped, onPageChanged, onDocumentLoaded) {
        ReaderJsBridge(
            onTextSelectionEvent = onTextSelectionEvent,
            onSearchResults = onSearchResults,
            onHighlightTapped = onHighlightTapped,
            onPageChanged = onPageChanged,
            onDocumentLoaded = onDocumentLoaded
        )
    }

    // Inject PDF data as Base64 chunks when page finishes loading
    LaunchedEffect(filePath, pageLoaded) {
        if (!pageLoaded || pdfLoaded) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        val file = File(filePath)
        if (!file.exists()) return@LaunchedEffect

        pdfLoaded = true

        // Read file and send as chunks via JS bridge
        // Chunk size 512KB keeps each evaluateJavascript call well under the ~2MB limit
        val chunkSize = 512 * 1024
        val fileBytes = withContext(Dispatchers.IO) { file.readBytes() }
        val totalChunks = (fileBytes.size + chunkSize - 1) / chunkSize

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, fileBytes.size)
            val b64 = Base64.encodeToString(
                fileBytes.copyOfRange(start, end),
                Base64.NO_WRAP
            )
            view.evaluateJavascript("addPdfChunk('$b64')", null)
        }
        view.evaluateJavascript("finalizePdf()", null)
    }

    // Sync highlights with JS when page or highlights change
    LaunchedEffect(highlights, currentPage, pageLoaded) {
        val view = webView ?: return@LaunchedEffect
        if (!pageLoaded) return@LaunchedEffect

        val pageHighlights = highlights.filter { hl ->
            hl.cfiRange == "pdfpage:$currentPage"
        }

        if (pageHighlights.isEmpty()) {
            view.evaluateJavascript(
                "pendingHighlights = []; if(typeof restorePageHighlights === 'function') restorePageHighlights(${currentPage + 1}, document.getElementById('textLayerDiv'));",
                null
            )
        } else {
            val jsonArr = JSONArray()
            pageHighlights.forEach { hl ->
                val obj = JSONObject().apply {
                    put("id", hl.id)
                    put("pageNumber", currentPage)
                    put("color", hl.color)
                    put("textSnippet", hl.textContent.trim().replace(Regex("\\s+"), " ").take(50))
                }
                jsonArr.put(obj)
            }
            val jsonStr = jsonArr.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
            view.evaluateJavascript(
                "setPendingHighlights('$jsonStr')",
                null
            )
        }
    }

    // Execute search when search query changes
    LaunchedEffect(searchQuery) {
        val view = webView ?: return@LaunchedEffect
        if (searchQuery.isBlank()) {
            view.evaluateJavascript("clearSearch()", null)
            lastSearchQuery = ""
            return@LaunchedEffect
        }
        if (searchQuery != lastSearchQuery) {
            val escaped = searchQuery.replace("'", "\\'")
            view.evaluateJavascript("executeSearch('$escaped')", null)
            lastSearchQuery = searchQuery
        }
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

                // WebViewAssetLoader only serves pdf.js assets — no /pdf/ handler needed
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/pdfjs/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (request.url.host != "appassets.androidplatform.net") return null
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                    }
                }

                // Suppress the default Android floating selection toolbar
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

                // Load index.html — the PDF data will be injected via JS bridge
                // after the page finishes loading (see LaunchedEffect above).
                val html = ctx.assets.open("pdfjs/index.html")
                    .bufferedReader()
                    .use { it.readText() }
                loadDataWithBaseURL(
                    "http://appassets.androidplatform.net/pdfjs/",
                    html, "text/html", "UTF-8", null
                )
                webView = this
            }
        },
        update = { view ->
            // Sync current page from ViewModel
            val pageNum = currentPage + 1
            view.evaluateJavascript(
                "if (typeof currentPageNumber !== 'undefined' && currentPageNumber !== $pageNum) goToPage($pageNum)",
                null
            )
        }
    )
}
