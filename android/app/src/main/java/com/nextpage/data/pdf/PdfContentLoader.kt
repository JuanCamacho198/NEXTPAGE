package com.nextpage.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nextpage.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfContentLoader(
    private val context: Context,
    private val searchHelper: PdfSearchHelper = NoopPdfSearchHelper()
) {
    companion object {
        private const val TAG = "PdfContentLoader"
    }

    private var pdfRenderer: PdfRendererWrapper? = null
    private var currentFile: File? = null
    private val lock = Any()

    suspend fun load(file: File) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (currentFile == file) {
                return@withContext
            }

            if (currentFile != file) {
                Log.d(TAG, "Loading PDF file=${file.absolutePath}")
                pdfRenderer?.close()
                val renderer = PdfRendererWrapper(context)
                renderer.open(file)
                pdfRenderer = renderer
                currentFile = file
            }
        }
    }

    suspend fun getPageCount(): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            pdfRenderer?.getPageCount() ?: 0
        }
    }

    suspend fun getPage(pageIndex: Int, width: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                pdfRenderer?.renderPage(pageIndex, width)
            }
        }
    }

    /**
     * Search the loaded PDF for [query].
     * Delegates to [PdfSearchHelper] which may be a stub returning empty results.
     */
    suspend fun searchText(query: String): List<SearchResult> {
        return searchHelper.searchText(query)
    }

    fun close() {
        synchronized(lock) {
            val renderer = pdfRenderer
            pdfRenderer = null
            currentFile = null
            renderer?.close()
        }
    }
}
