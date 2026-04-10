package com.nextpage.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRendererWrapper(private val context: Context) {
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val pageCache = mutableMapOf<Int, Bitmap>()
    
    suspend fun open(file: File) = withContext(Dispatchers.IO) {
        close()
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)
    }
    
    fun getPageCount(): Int = pdfRenderer?.pageCount ?: 0
    
    suspend fun renderPage(pageIndex: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        
        pageCache[pageIndex]?.let { return@withContext it }
        
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
        
        val page = renderer.openPage(pageIndex)
        val aspectRatio = page.width.toFloat() / page.height.toFloat()
        val height = (width / aspectRatio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        
        pageCache[pageIndex] = bitmap
        bitmap
    }
    
    fun close() {
        pageCache.values.forEach { it.recycle() }
        pageCache.clear()
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
    }
}