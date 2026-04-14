package com.nextpage.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfContentLoader(private val context: Context) {
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

    fun close() {
        synchronized(lock) {
            val renderer = pdfRenderer
            pdfRenderer = null
            currentFile = null
            renderer?.close()
        }
    }
}
