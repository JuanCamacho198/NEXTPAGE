package com.nextpage.data.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Minimal PdfRenderer wrapper for extracting PDF metadata (page count).
 *
 * Page rendering, text selection, and search are now handled by [PdfWebView]
 * via PDF.js in a WebView. This class only provides the page count
 * needed for initial metadata on book load.
 */
class PdfRendererWrapper(private val context: Context) {

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val lock = Any()

    fun open(file: File) {
        synchronized(lock) {
            if (!file.exists()) {
                throw java.io.FileNotFoundException("PDF file not found: ${file.absolutePath}")
            }
            closeLocked()
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
        }
    }

    fun getPageCount(): Int = synchronized(lock) {
        pdfRenderer?.pageCount ?: 0
    }

    fun close() {
        synchronized(lock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
    }
}
