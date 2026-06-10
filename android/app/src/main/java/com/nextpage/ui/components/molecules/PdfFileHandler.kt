package com.nextpage.ui.components.molecules

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

/**
 * Custom [WebViewAssetLoader.PathHandler] that serves a single PDF file
 * from the filesystem at the URL path `/pdf/current.pdf`.
 *
 * This avoids passing the entire PDF as a Base64 string via
 * [android.webkit.WebView.evaluateJavascript], which has implicit
 * size limits that fail on larger PDFs.
 */
class PdfFileHandler : WebViewAssetLoader.PathHandler {

    @Volatile
    private var pdfFile: File? = null

    fun setFile(file: File?) {
        pdfFile = file
    }

    override fun handle(path: String): WebResourceResponse? {
        if (path != "/pdf/current.pdf") return null
        val file = pdfFile ?: return null
        if (!file.exists()) return null

        return try {
            val headers = mapOf("Access-Control-Allow-Origin" to "*")
            WebResourceResponse("application/pdf", null, 200, "OK", headers, FileInputStream(file))
        } catch (e: Exception) {
            null
        }
    }
}
