package com.nextpage.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import coil.ImageLoader
import com.nextpage.BuildConfig
import com.nextpage.data.epub.ZipEpubParserService
import com.nextpage.data.pdf.DefaultPdfParserService
import com.nextpage.data.local.AppDatabase
import com.nextpage.data.local.AppDatabaseMigrations
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.DictionaryWordDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.local.dao.ReadingStatsDao
import com.nextpage.data.repository.HomeRepositoryImpl
import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.data.repository.ReadingStatsRepositoryImpl
import com.nextpage.data.repository.DictionaryRepositoryImpl
import com.nextpage.data.repository.SupabaseAuthRepository
import com.nextpage.data.remote.google.GoogleDriveConfig
import com.nextpage.data.remote.google.GoogleDriveClientProvider
import com.nextpage.data.remote.google.GoogleDriveInitDiagnostic
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.remote.supabase.SupabaseProgressDataSource
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.GoogleDriveStorageRemoteDataSource
import com.nextpage.data.remote.sync.GoogleDriveSyncService
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.presentation.theme.CoilModule

class AppContainer(context: Context) {
    companion object {
        private const val TAG = "AppContainer"
    }

    private val startTime = System.currentTimeMillis()

    private val appDatabase: AppDatabase = Room.databaseBuilder(
        context = context.applicationContext,
        klass = AppDatabase::class.java,
        name = "nextpage.db"
    ).addMigrations(*AppDatabaseMigrations.ALL).let { builder ->
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        } else {
            builder
        }
    }.build()

    private val dbInitTime = System.currentTimeMillis() - startTime
    init {
        Log.d(TAG, "Database initialized in ${dbInitTime}ms")
    }

    val coverStorage = AppInternalCoverStorage(context.applicationContext)

    val coilImageLoader: ImageLoader = CoilModule.imageLoader(context.applicationContext)

    private val pdfParserService = DefaultPdfParserService(context.applicationContext)

    private val epubImportStartTime = System.currentTimeMillis()
    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        appContext = context.applicationContext,
        bookDao = appDatabase.bookDao(),
        readingStatsDao = appDatabase.readingStatsDao(),
        epubParserService = ZipEpubParserService(),
        pdfParserService = pdfParserService,
        coverStorage = coverStorage,
        readingProgressDao = appDatabase.readingProgressDao()
    )
    private val epubImportInitTime = System.currentTimeMillis() - epubImportStartTime
    init {
        Log.d(TAG, "LibraryRepository initialized in ${epubImportInitTime}ms")
    }

    private val readerRepoStartTime = System.currentTimeMillis()
    val readerRepository: ReaderRepository = ReaderRepositoryImpl(
        readingProgressDao = appDatabase.readingProgressDao(),
        highlightDao = appDatabase.highlightDao(),
        bookmarkDao = appDatabase.bookmarkDao(),
        outboxDao = appDatabase.syncOutboxDao()
    )
    private val readerRepoInitTime = System.currentTimeMillis() - readerRepoStartTime
    init {
        Log.d(TAG, "ReaderRepository initialized in ${readerRepoInitTime}ms")
    }

    val readingStatsRepository: ReadingStatsRepository = ReadingStatsRepositoryImpl(
        readingStatsDao = appDatabase.readingStatsDao(),
        readingSessionDao = appDatabase.readingSessionDao()
    )
    
    val homeRepository: HomeRepository = HomeRepositoryImpl(
        bookDao = appDatabase.bookDao(),
        readingProgressDao = appDatabase.readingProgressDao(),
        readingSessionDao = appDatabase.readingSessionDao()
    )

    val readerPreferences: ReaderPreferences = ReaderPreferences(context.applicationContext)

    val dictionaryRepository: DictionaryRepository = DictionaryRepositoryImpl(
        dao = appDatabase.dictionaryWordDao()
    )

    // ── Supabase Auth ───────────────────────────────────────────────

    // Initialise Supabase client eagerly so GoTrue session is ready.
    // This also checks that SUPABASE_URL and SUPABASE_ANON_KEY are set.
    private val supabaseInit = runCatching { SupabaseClientProvider.client }

    val sessionManager: SessionManager by lazy {
        SupabaseSessionManager()
    }

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(
            context = context.applicationContext,
            sessionManager = sessionManager
        )
    }

    val syncService: SyncService by lazy {
        GoogleDriveSyncService(
            outboxDao = appDatabase.syncOutboxDao(),
            bookDao = appDatabase.bookDao(),
            mappingDao = appDatabase.syncFileMappingDao(),
            readingProgressDao = appDatabase.readingProgressDao(),
            highlightDao = appDatabase.highlightDao(),
            bookmarkDao = appDatabase.bookmarkDao(),
            sessionManager = sessionManager,
            remoteDataSource = NoopStorageSyncRemoteDataSource,
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            isEnabled = false,
            diagnosticError = AppError(
                category = com.nextpage.domain.error.ErrorCategory.CONFIG_ERROR,
                code = "SYNC_NEEDS_DRIVE_REFACTOR",
                message = "Drive sync with provider_token not yet implemented on Android.",
                component = "AppContainer"
            )
        )
    }

    val supabaseProgressDataSource: SupabaseProgressDataSource by lazy {
        SupabaseProgressDataSource()
    }

    val supabaseProgressSync: SupabaseProgressSync by lazy {
        SupabaseProgressSync(
            outboxDao = appDatabase.syncOutboxDao(),
            readingProgressDao = appDatabase.readingProgressDao(),
            bookmarkDao = appDatabase.bookmarkDao(),
            highlightDao = appDatabase.highlightDao(),
            sessionManager = sessionManager,
            dataSource = supabaseProgressDataSource
        )
    }

    // ── Diagnostic properties ───────────────────────────────────────

    val isAuthConfigError: Boolean
        get() = supabaseInit.isFailure

    // ── Init timing ─────────────────────────────────────────────────

    private val totalInitTime = System.currentTimeMillis() - startTime
    init {
        Log.i(TAG, "AppContainer fully initialized in ${totalInitTime}ms")
    }

    // ── Init timing exposure (debug) ────────────────────────────────
    val dbInitTimeMs: Long get() = dbInitTime
    val epubImportInitTimeMs: Long get() = epubImportInitTime
    val readerRepoInitTimeMs: Long get() = readerRepoInitTime
    val totalInitTimeMs: Long get() = totalInitTime

    // ── DAO exposure (debug) ────────────────────────────────────────
    val bookDao: BookDao get() = appDatabase.bookDao()
    val highlightDao: HighlightDao get() = appDatabase.highlightDao()
    val bookmarkDao: BookmarkDao get() = appDatabase.bookmarkDao()
    val readingSessionDao: ReadingSessionDao get() = appDatabase.readingSessionDao()
    val readingProgressDao: ReadingProgressDao get() = appDatabase.readingProgressDao()

    // ── Debug actions ───────────────────────────────────────────────
    fun clearAllData() {
        appDatabase.clearAllTables()
    }

    private data object NoopStorageSyncRemoteDataSource : com.nextpage.data.remote.sync.StorageSyncRemoteDataSource {
        override suspend fun upload(path: String, bytes: ByteArray) {
            throw IllegalStateException("Remote storage is not configured.")
        }

        override suspend fun download(path: String): ByteArray {
            throw IllegalStateException("Remote storage is not configured.")
        }

        override suspend fun list(prefix: String): List<String> {
            throw IllegalStateException("Remote storage is not configured.")
        }
    }
}
