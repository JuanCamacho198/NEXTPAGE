package com.nextpage.ui.components.molecules

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Injects NextPage's reader CSS into the chapter HTML for beautiful rendering.
 * Colors match the design tokens: dark bg (#0B1120), light text (#E2E8F0).
 */
fun readerCss(
    bgColor: String = "#0B1120",
    textColor: String = "#E2E8F0",
    fontSizePx: Int = 20,
    lineHeight: Float = 1.6f
): String {
    return """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            background-color: $bgColor;
            color: $textColor;
            font-size: ${fontSizePx}px;
            line-height: $lineHeight;
            font-family: 'Georgia', 'Noto Serif', serif;
            padding: 20px 16px;
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
private fun wrapHtmlContent(htmlContent: String, css: String): String {
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
 */
class ReaderJsBridge(
    private val onTextSelected: (String) -> Unit,
    private val onScrollChanged: (Int, Int) -> Unit = { _, _ -> }
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
    fun onChapterEndReached() {
        // Can be used for auto-advance to next chapter
    }
}

/**
 * EpubWebView renders EPUB chapter HTML using Android's WebView with
 * NextPage's custom CSS for an optimal reading experience.
 *
 * Features:
 * - Injects CSS for dark theme, font size, line height
 * - JavaScript bridge for text selection and scroll events
 * - Responsive to settings changes via recomposition
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubWebView(
    htmlContent: String,
    bgColor: String = "#0B1120",
    textColor: String = "#E2E8F0",
    fontSizePx: Int = 20,
    lineHeight: Float = 1.6f,
    onTextSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val jsBridge = remember(onTextSelected) {
        ReaderJsBridge(onTextSelected = onTextSelected)
    }

    // Precompute the full HTML + CSS so update only needs to load it
    val renderedHtml = remember(htmlContent, bgColor, textColor, fontSizePx, lineHeight) {
        val css = readerCss(bgColor, textColor, fontSizePx, lineHeight)
        wrapHtmlContent(htmlContent, css)
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

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return false
                    }
                }

                webChromeClient = WebChromeClient()

                addJavascriptInterface(jsBridge, "NextPageBridge")
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, renderedHtml, "text/html", "UTF-8", null)
        }
    )
}
