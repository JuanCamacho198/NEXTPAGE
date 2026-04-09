package com.nextpage.di

import android.content.Context
import androidx.room.Room
import com.nextpage.data.epub.ZipEpubParserService
import com.nextpage.data.local.AppDatabase
import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository

class AppContainer(context: Context) {
    private val appDatabase: AppDatabase = Room.databaseBuilder(
        context = context.applicationContext,
        klass = AppDatabase::class.java,
        name = "nextpage.db"
    ).fallbackToDestructiveMigration().build()

    private val coverStorage = AppInternalCoverStorage(context.applicationContext)

    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        bookDao = appDatabase.bookDao(),
        epubParserService = ZipEpubParserService(),
        coverStorage = coverStorage
    )

    val readerRepository: ReaderRepository = ReaderRepositoryImpl(
        readingProgressDao = appDatabase.readingProgressDao()
    )
}
