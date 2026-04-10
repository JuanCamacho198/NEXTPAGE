package com.nextpage.data.pdf

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfContentLoader(private val context: Context) {
    private var pdfRenderer: PdfRendererWrapper? = null
    private var currentFile: File? = null

    suspend fun load(file: File) = withContext(Dispatchers.IO) {
        if (currentFile != file) {
            pdfRenderer?.close()
            pdfRenderer = PdfRendererWrapper(context)
            pdfRenderer?.open(file)
            currentFile = file
        }
    }

    fun getPageCount(): Int = pdfRenderer?.getPageCount() ?: 0

    suspend fun getPage(pageIndex: Int, width: Int): Bitmap? {
        return pdfRenderer?.renderPage(pageIndex, width)
    }

    fun close() {
        pdfRenderer?.close()
        pdfRenderer = null
        currentFile = null
    }
}