package com.nextpage.di.modules

import android.content.Context
import android.util.Log
import com.nextpage.data.repository.DictionaryRepositoryImpl
import com.nextpage.data.repository.HomeRepositoryImpl
import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.data.repository.ReadingStatsRepositoryImpl
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository

class RepositoryModule(
    context: Context,
    databaseModule: DatabaseModule,
    storageModule: StorageModule,
    @Suppress("UNUSED_PARAMETER") preferencesModule: PreferencesModule
) {
    companion object {
        private const val TAG = "RepositoryModule"
    }

    private val epubImportStartTime = System.currentTimeMillis()
    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        appContext = context.applicationContext,
        bookDao = databaseModule.bookDao,
        readingStatsDao = databaseModule.readingStatsDao,
        epubParserService = storageModule.epubParserService,
        pdfParserService = storageModule.pdfParserService,
        coverStorage = storageModule.coverStorage,
        readingProgressDao = databaseModule.readingProgressDao,
        outboxDao = databaseModule.syncOutboxDao
    )
    val epubImportInitTimeMs: Long = System.currentTimeMillis() - epubImportStartTime

    init {
        Log.d(TAG, "LibraryRepository initialized in ${epubImportInitTimeMs}ms")
    }

    private val readerRepoStartTime = System.currentTimeMillis()
    val readerRepository: ReaderRepository = ReaderRepositoryImpl(
        readingProgressDao = databaseModule.readingProgressDao,
        highlightDao = databaseModule.highlightDao,
        bookmarkDao = databaseModule.bookmarkDao,
        bookDao = databaseModule.bookDao,
        outboxDao = databaseModule.syncOutboxDao
    )
    val readerRepoInitTimeMs: Long = System.currentTimeMillis() - readerRepoStartTime

    init {
        Log.d(TAG, "ReaderRepository initialized in ${readerRepoInitTimeMs}ms")
    }

    val readingStatsRepository: ReadingStatsRepository = ReadingStatsRepositoryImpl(
        readingStatsDao = databaseModule.readingStatsDao,
        readingSessionDao = databaseModule.readingSessionDao,
        outboxDao = databaseModule.syncOutboxDao
    )

    val homeRepository: HomeRepository = HomeRepositoryImpl(
        bookDao = databaseModule.bookDao,
        readingProgressDao = databaseModule.readingProgressDao,
        readingSessionDao = databaseModule.readingSessionDao
    )

    val dictionaryRepository: DictionaryRepository = DictionaryRepositoryImpl(
        dao = databaseModule.dictionaryWordDao
    )
}
