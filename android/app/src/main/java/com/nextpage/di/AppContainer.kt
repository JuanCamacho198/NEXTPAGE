package com.nextpage.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.nextpage.BuildConfig
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.data.epub.ZipEpubParserService
import com.nextpage.data.pdf.DefaultPdfParserService
import com.nextpage.data.pdf.PdfContentLoader
import com.nextpage.data.local.AppDatabase
import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.data.repository.ReadingStatsRepositoryImpl
import com.nextpage.data.repository.SupabaseAuthRepository
import com.nextpage.data.remote.supabase.SupabaseClientProvider
import com.nextpage.data.remote.supabase.SupabaseConfigProvider
import com.nextpage.data.remote.supabase.SupabaseInitDiagnostic
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SupabaseStorageSyncRemoteDataSource
import com.nextpage.data.remote.sync.SupabaseSyncService
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.data.session.PreferencesSessionStore
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

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

    private val pdfParserService = DefaultPdfParserService(context.applicationContext)

    private val epubImportStartTime = System.currentTimeMillis()
    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        bookDao = appDatabase.bookDao(),
        readingStatsDao = appDatabase.readingStatsDao(),
        epubParserService = ZipEpubParserService(),
        pdfParserService = pdfParserService,
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

    val readingStatsRepository: ReadingStatsRepository = ReadingStatsRepositoryImpl(
        readingStatsDao = appDatabase.readingStatsDao()
    )

    private val contentLoaderStartTime = System.currentTimeMillis()
    val epubContentLoader: EpubContentLoader = EpubContentLoader(context.applicationContext)
    val pdfContentLoader: PdfContentLoader = PdfContentLoader(context.applicationContext)
    private val contentLoaderInitTime = System.currentTimeMillis() - contentLoaderStartTime
    init {
        Log.d(TAG, "ContentLoaders initialized in ${contentLoaderInitTime}ms")
    }

    private val supabaseConfigProvider = SupabaseConfigProvider()
    private val supabaseConfig = supabaseConfigProvider.get()
    val supabaseClientProvider = SupabaseClientProvider(supabaseConfig)
    val supabaseDiagnostic = supabaseClientProvider.initDiagnostic

    val sessionManager: SessionManager by lazy {
        SupabaseSessionManager(
            client = supabaseClientProvider.client,
            diagnosticError = supabaseDiagnostic.error,
            sessionStore = PreferencesSessionStore(context.applicationContext)
        )
    }

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(
            client = supabaseClientProvider.client,
            sessionManager = sessionManager,
            supabaseUrl = supabaseConfig.url,
            redirectScheme = BuildConfig.AUTH_REDIRECT_SCHEME,
            redirectHost = BuildConfig.AUTH_REDIRECT_HOST,
            redirectPath = BuildConfig.AUTH_REDIRECT_PATH,
            diagnosticError = supabaseDiagnostic.error
        )
    }

    val syncService: SyncService by lazy {
        val client = supabaseClientProvider.client
        val remoteDataSource = client?.let {
            SupabaseStorageSyncRemoteDataSource(
                client = it,
                bucket = BuildConfig.SUPABASE_STORAGE_BOOKS_BUCKET
            )
        }
        SupabaseSyncService(
            outboxDao = appDatabase.syncOutboxDao(),
            bookDao = appDatabase.bookDao(),
            mappingDao = appDatabase.syncFileMappingDao(),
            sessionManager = sessionManager,
            remoteDataSource = remoteDataSource ?: NoopStorageSyncRemoteDataSource,
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            isEnabled = supabaseClientProvider.isConfigured && remoteDataSource != null,
            diagnosticError = supabaseDiagnostic.error
        )
    }

    private val _authCallbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authCallbackEvents: SharedFlow<String> = _authCallbackEvents

    fun submitAuthCallback(uri: String) {
        _authCallbackEvents.tryEmit(uri)
    }

    val isSupabaseConfigError: Boolean
        get() = supabaseDiagnostic.status == SupabaseInitDiagnostic.Status.CONFIG_ERROR

    val isSupabaseWiringError: Boolean
        get() = supabaseDiagnostic.status == SupabaseInitDiagnostic.Status.WIRING_ERROR

    private val totalInitTime = System.currentTimeMillis() - startTime
    init {
        Log.i(TAG, "AppContainer fully initialized in ${totalInitTime}ms")
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
