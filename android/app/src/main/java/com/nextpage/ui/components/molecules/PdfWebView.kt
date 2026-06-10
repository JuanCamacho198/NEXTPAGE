package com.nextpage.ui.components.molecules

import android.annotation.SuppressLint
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PdfWebView renders a PDF document using PDF.js in a WebView with a
 * selectable text layer, search support, and highlight capabilities.
 *
 * Architecture mirrors [EpubWebView]:
 * - PDF.js assets are served via [WebViewAssetLoader] from `assets/pdfjs/`
 * - The PDF file is served via a custom [PdfFileHandler] over the same asset
 *   domain — PDF.js loads it with `fetch()` instead of inline Base64.
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

    val pdfHandler = remember { PdfFileHandler() }

    val jsBridge = remember(onTextSelectionEvent, onSearchResults, onHighlightTapped, onPageChanged, onDocumentLoaded) {
        ReaderJsBridge(
            onTextSelectionEvent = onTextSelectionEvent,
            onSearchResults = onSearchResults,
            onHighlightTapped = onHighlightTapped,
            onPageChanged = onPageChanged,
            onDocumentLoaded = onDocumentLoaded
        )
    }

    // Load PDF when WebView finishes loading index.html
    LaunchedEffect(filePath, pageLoaded) {
        if (!pageLoaded) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        val file = File(filePath)
        if (!file.exists()) return@LaunchedEffect

        // Tell the handler which file to serve, then tell JS to fetch it
        pdfHandler.setFile(file)
        view.evaluateJavascript("loadPdfFromUrl('https://appassets.androidplatform.net/pdf/current.pdf')", null)
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
            // Safe JSON passing via JSON.stringify to avoid injection
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

                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/pdfjs/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .addPathHandler("/pdf/", pdfHandler)
                    .build()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        // Only allow requests to our asset domain
                        if (request.url.host != "appassets.androidplatform.net") return null
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                    }
                }

                addJavascriptInterface(jsBridge, "NextPageBridge")

                // Load HTML from assets string — WebViewAssetLoader cannot intercept
                // the initial main-frame navigation in modern Chromium WebViews,
                // so loadUrl("https://appassets.androidplatform.net/...") triggers
                // a real HTTPS request → ERR_INVALID_RESPONSE.
                // loadDataWithBaseURL provides a virtual base URL so relative
                // subresource paths (pdf.js, pdf.worker.js) are intercepted correctly.
                val html = ctx.assets.open("pdfjs/index.html")
                    .bufferedReader()
                    .use { it.readText() }
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/pdfjs/",
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
