package com.nextpage.data.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DefaultPdfParserService(private val context: Context) : PdfParserService {
    override suspend fun extractMetadata(file: File): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            try {
                val title = file.nameWithoutExtension
                val author: String? = null
                val pageCount = renderer.pageCount
                val fileSizeBytes = file.length()
                
                PdfMetadata(
                    title = title,
                    author = author,
                    pageCount = pageCount,
                    fileSizeBytes = fileSizeBytes
                )
            } finally {
                renderer.close()
                fileDescriptor.close()
            }
        }
    }

    override fun getPageCount(file: File): Int {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            val count = renderer.pageCount
            renderer.close()
            fileDescriptor.close()
            count
        } catch (e: Exception) {
            0
        }
    }
}