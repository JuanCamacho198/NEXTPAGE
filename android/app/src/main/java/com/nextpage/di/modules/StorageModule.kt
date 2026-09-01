package com.nextpage.di.modules

import android.content.Context
import coil.ImageLoader
import com.nextpage.data.epub.ZipEpubParserService
import com.nextpage.data.pdf.DefaultPdfParserService
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.presentation.theme.CoilModule

class StorageModule(
    context: Context,
    @Suppress("UNUSED_PARAMETER") databaseModule: DatabaseModule
) {
    val coverStorage: AppInternalCoverStorage = AppInternalCoverStorage(context.applicationContext)

    val coilImageLoader: ImageLoader = CoilModule.imageLoader(context.applicationContext)

    val pdfParserService: DefaultPdfParserService = DefaultPdfParserService(context.applicationContext)

    val epubParserService: ZipEpubParserService = ZipEpubParserService()
}
