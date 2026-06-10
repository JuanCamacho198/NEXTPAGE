package com.nextpage.ui.components.molecules

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.nextpage.data.epub.EpubContentLoader

/**
 * Custom [WebViewAssetLoader.PathHandler] that serves EPUB entries (images, CSS, etc.)
 * from the EPUB ZIP file at the URL path `/epub/`.
 *
 * The chapter's directory is included in the URL path so relative image references
 * resolve correctly. For example, a chapter at `OEBPS/chapter1.xhtml` with
 * base URL `https://appassets.androidplatform.net/epub/OEBPS/` will resolve
 * `<img src="Images/cover.jpg">` to `https://appassets.androidplatform.net/epub/OEBPS/Images/cover.jpg`,
 * which this handler translates to `getEntryBytes("OEBPS/Images/cover.jpg")`.
 *
 * CORS headers are included for compatibility with pages loaded via
 * [android.webkit.WebView.loadDataWithBaseURL] (which have a null origin).
 */
class EpubFileHandler : WebViewAssetLoader.PathHandler {

    @Volatile
    private var filePath: String? = null
    @Volatile
    private var contentLoader: EpubContentLoader? = null

    fun setLoader(fp: String?, loader: EpubContentLoader?) {
        filePath = fp
        contentLoader = loader
    }

    override fun handle(path: String): WebResourceResponse? {
        val fp = filePath ?: return null
        val loader = contentLoader ?: return null

        // Path comes as "/epub/OEBPS/Images/cover.jpg" — strip "/epub/"
        if (!path.startsWith("/epub/")) return null
        val entryPath = path.removePrefix("/epub/")

        val result = loader.getEntryBytes(fp, entryPath)
        return result.fold(
            onSuccess = { bytes ->
                val mimeType = when {
                    entryPath.endsWith(".png", ignoreCase = true) -> "image/png"
                    entryPath.endsWith(".jpg", ignoreCase = true) || entryPath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    entryPath.endsWith(".gif", ignoreCase = true) -> "image/gif"
                    entryPath.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                    entryPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    entryPath.endsWith(".css", ignoreCase = true) -> "text/css"
                    entryPath.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
                    entryPath.endsWith(".woff", ignoreCase = true) -> "font/woff"
                    entryPath.endsWith(".ttf", ignoreCase = true) || entryPath.endsWith(".otf", ignoreCase = true) -> "font/ttf"
                    else -> "application/octet-stream"
                }
                val headers = mapOf("Access-Control-Allow-Origin" to "*")
                WebResourceResponse(mimeType, null, 200, "OK", headers, java.io.ByteArrayInputStream(bytes))
            },
            onFailure = { null }
        )
    }
}
