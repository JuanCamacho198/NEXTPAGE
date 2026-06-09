package com.nextpage.data.pdf

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PdfContentLoader provides basic PDF metadata (page count) for the initial load.
 *
 * Rendering, text selection, and search are now handled by [PdfWebView]
 * via PDF.js in a WebView.
 */
class PdfContentLoader(
    private val context: Context
) {
    companion object {
        private const val TAG = "PdfContentLoader"
    }

    private var pdfRendererWrapper: PdfRendererWrapper? = null
    private var currentFile: File? = null
    private val lock = Any()

    suspend fun load(file: File) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (currentFile == file) {
                return@withContext
            }

            if (currentFile != file) {
                Log.d(TAG, "Loading PDF metadata file=${file.absolutePath}")
                pdfRendererWrapper?.close()
                val renderer = PdfRendererWrapper(context)
                renderer.open(file)
                pdfRendererWrapper = renderer
                currentFile = file
            }
        }
    }

    suspend fun getPageCount(): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            pdfRendererWrapper?.getPageCount() ?: 0
        }
    }

    fun close() {
        synchronized(lock) {
            val renderer = pdfRendererWrapper
            pdfRendererWrapper = null
            currentFile = null
            renderer?.close()
        }
    }
}
