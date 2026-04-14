package com.nextpage.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

class PdfRendererWrapper(private val context: Context) {
    companion object {
        private const val TAG = "PdfRendererWrapper"
    }

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val pageCache = mutableMapOf<Int, Bitmap>()
    private val lock = Any()
    
    fun open(file: File) {
        synchronized(lock) {
            closeLocked()
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
        }
    }
    
    fun getPageCount(): Int = synchronized(lock) {
        pdfRenderer?.pageCount ?: 0
    }
    
    fun renderPage(pageIndex: Int, width: Int): Bitmap? {
        synchronized(lock) {
            val renderer = pdfRenderer ?: return null

            pageCache[pageIndex]?.let { return it }

            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

            val page = renderer.openPage(pageIndex)
            try {
                val aspectRatio = page.width.toFloat() / page.height.toFloat()
                val height = (width / aspectRatio).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                pageCache[pageIndex] = bitmap
                return bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering pageIndex=$pageIndex width=$width: ${e.message}", e)
                throw e
            } finally {
                page.close()
            }
        }
    }
    
    fun close() {
        synchronized(lock) {
            closeLocked()
        }
    }

    private fun closeLocked() {
        pageCache.values.forEach { it.recycle() }
        pageCache.clear()
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
    }
}
