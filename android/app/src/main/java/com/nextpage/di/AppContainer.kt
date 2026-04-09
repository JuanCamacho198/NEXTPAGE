package com.nextpage.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.data.epub.ZipEpubParserService
import com.nextpage.data.local.AppDatabase
import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.data.repository.SupabaseAuthRepository
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.remote.supabase.SupabaseConfigProvider
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncServicePlaceholder
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository

class AppContainer(context: Context) {
    companion object {
        private const val TAG = "AppContainer"
    }

    private val startTime = System.currentTimeMillis()

    private val appDatabase: AppDatabase = Room.databaseBuilder(
        context = context.applicationContext,
        klass = AppDatabase::class.java,
        name = "nextpage.db"
    ).fallbackToDestructiveMigration().build()

    private val dbInitTime = System.currentTimeMillis() - startTime
    init {
        Log.d(TAG, "Database initialized in ${dbInitTime}ms")
    }

    private val coverStorage = AppInternalCoverStorage(context.applicationContext)

    private val epubImportStartTime = System.currentTimeMillis()
    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        bookDao = appDatabase.bookDao(),
        epubParserService = ZipEpubParserService(),
        coverStorage = coverStorage
    )
    private val epubImportInitTime = System.currentTimeMillis() - epubImportStartTime
    init {
        Log.d(TAG, "LibraryRepository initialized in ${epubImportInitTime}ms")
    }

    private val readerRepoStartTime = System.currentTimeMillis()
    val readerRepository: ReaderRepository = ReaderRepositoryImpl(
        readingProgressDao = appDatabase.readingProgressDao(),
        highlightDao = appDatabase.highlightDao(),
        bookmarkDao = appDatabase.bookmarkDao()
    )
    private val readerRepoInitTime = System.currentTimeMillis() - readerRepoStartTime
    init {
        Log.d(TAG, "ReaderRepository initialized in ${readerRepoInitTime}ms")
    }

    private val contentLoaderStartTime = System.currentTimeMillis()
    val epubContentLoader: EpubContentLoader = EpubContentLoader(context.applicationContext)
    private val contentLoaderInitTime = System.currentTimeMillis() - contentLoaderStartTime
    init {
        Log.d(TAG, "EpubContentLoader initialized in ${contentLoaderInitTime}ms")
    }

    private val supabaseConfigProvider = SupabaseConfigProvider()
    private val supabaseConfig = supabaseConfigProvider.get()
    val supabaseClientProvider = SupabaseClientProvider(supabaseConfig)

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(supabaseClientProvider.client)
    }

    val syncService: SyncService by lazy {
        SyncServicePlaceholder(
            outboxDao = appDatabase.syncOutboxDao(),
            isConfigured = supabaseClientProvider.isConfigured
        )
    }

    private val totalInitTime = System.currentTimeMillis() - startTime
    init {
        Log.i(TAG, "AppContainer fully initialized in ${totalInitTime}ms")
    }
}
