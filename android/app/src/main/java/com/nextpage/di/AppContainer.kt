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
import com.nextpage.data.remote.drive.driveOAuthRedirectUri
import com.nextpage.data.remote.drive.DriveCoordinator
import com.nextpage.data.remote.drive.DriveOAuthSession
import com.nextpage.data.remote.drive.DriveTokenApi
import com.nextpage.data.remote.drive.DriveTokenStore
import com.nextpage.data.remote.drive.EncryptedDriveTokenStore
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.remote.drive.InMemoryDriveTokenStore
import com.nextpage.data.remote.drive.KtorAuthApi
import com.nextpage.data.remote.google.GoogleDriveConfig
import com.nextpage.data.remote.google.GoogleDriveClientProvider
import com.nextpage.data.remote.google.GoogleDriveInitDiagnostic
import com.nextpage.data.remote.supabase.SupabaseBookCatalogDataSource
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.SupabaseProgressDataSource
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.DriveColdBackupService
import com.nextpage.data.remote.sync.GoogleDriveStorageRemoteDataSource
import com.nextpage.data.remote.sync.GoogleDriveSyncService
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.data.session.ReadingGoalPreferences
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.data.storage.AppInternalCoverStorage
import com.nextpage.domain.error.AppError
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.GetStatisticsUseCase
import com.nextpage.presentation.theme.CoilModule
import io.ktor.client.HttpClient

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
            builder.fallbackToDestructiveMigration(dropAllTables = true)
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
        readingProgressDao = appDatabase.readingProgressDao(),
        outboxDao = appDatabase.syncOutboxDao()
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
        bookDao = appDatabase.bookDao(),
        outboxDao = appDatabase.syncOutboxDao()
    )
    private val readerRepoInitTime = System.currentTimeMillis() - readerRepoStartTime
    init {
        Log.d(TAG, "ReaderRepository initialized in ${readerRepoInitTime}ms")
    }

    val readingStatsRepository: ReadingStatsRepository = ReadingStatsRepositoryImpl(
        readingStatsDao = appDatabase.readingStatsDao(),
        readingSessionDao = appDatabase.readingSessionDao(),
        outboxDao = appDatabase.syncOutboxDao()
    )

    // ── Reader Lifecycle Dependencies (PR #1: Facade decomposition) ─────────
    // Groups UpdateReadingProgressUseCase + ReadingStatsRepository + SupabaseProgressSync
    // construction without changing external API or lazy timing (totalInitTime guard).
    internal object ReaderDependencies {
        fun updateReadingProgressUseCase(readerRepository: ReaderRepository) =
            com.nextpage.domain.usecase.UpdateReadingProgressUseCase(readerRepository)
    }

    val updateReadingProgressUseCase: com.nextpage.domain.usecase.UpdateReadingProgressUseCase by lazy {
        ReaderDependencies.updateReadingProgressUseCase(readerRepository)
    }

    val homeRepository: HomeRepository = HomeRepositoryImpl(
        bookDao = appDatabase.bookDao(),
        readingProgressDao = appDatabase.readingProgressDao(),
        readingSessionDao = appDatabase.readingSessionDao()
    )

    /**
     * User's daily reading goal in minutes, falling back to 30 when unset
     * (REQ-daily-reading-goal-1/3). Shared by Home + Statistics so both screens
     * compute progress against the SAME goal.
     */
    val dailyGoalProvider: () -> Int = { readingGoalPreferences.load() ?: 30 }

    /**
     * Single shared streak/statistics source (REQ-streak-widget-1): injected into
     * both the Home and Statistics factories so currentStreak and goalProgress are
     * computed once, with the same user scope.
     */
    val getStatisticsUseCase: GetStatisticsUseCase by lazy {
        GetStatisticsUseCase(
            readingStatsRepository = readingStatsRepository,
            homeRepository = homeRepository,
            dailyGoalProvider = dailyGoalProvider
        )
    }

    val getBookProgressUseCase: com.nextpage.domain.usecase.GetBookProgressUseCase by lazy {
        com.nextpage.domain.usecase.GetBookProgressUseCase(
            readerRepository = readerRepository,
            readingProgressDao = appDatabase.readingProgressDao(),
            bookDao = appDatabase.bookDao()
        )
    }

    val progressReconciler: com.nextpage.data.sync.ProgressReconciler by lazy {
        com.nextpage.data.sync.ProgressReconciler(
            bookDao = appDatabase.bookDao(),
            readingProgressDao = appDatabase.readingProgressDao()
        )
    }

    val readerPreferences: ReaderPreferences = ReaderPreferences(context.applicationContext)

    val readingGoalPreferences: ReadingGoalPreferences =
        ReadingGoalPreferences(context.applicationContext)

    private val driveTokenStore: DriveTokenStore by lazy {
        runCatching { EncryptedDriveTokenStore(context.applicationContext) }
            .getOrElse { InMemoryDriveTokenStore() }
    }

    private val driveTokenApi: DriveTokenApi by lazy {
        KtorAuthApi(HttpClient())
    }

    /**
     * Pure, JVM-testable core of the Drive OAuth authorization-code + PKCE flow.
     * Singleton so the Settings screen and (later) the import prompt share one flow
     * and pending redirect state; reuses [driveTokenStore], [driveTokenApi] and the
     * ANDROID OAuth client ID from BuildConfig (public client, no secret needed).
     */
    val driveOAuthSession: DriveOAuthSession by lazy {
        DriveOAuthSession(
            clientId = BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID,
            redirectUri = driveOAuthRedirectUri(BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID),
            tokenStore = driveTokenStore,
            tokenApi = driveTokenApi
        )
    }

    /**
     * Thin Android layer over [driveOAuthSession]: builds the authorize URL, launches
     * the browser, and receives the Drive OAuth redirect via
     * `MainActivity.onNewIntent`. Singleton by design — the pending PKCE attempt must
     * survive across screens.
     */
    val googleDriveAuthHelper: GoogleDriveAuthHelper by lazy {
        GoogleDriveAuthHelper(
            context = context.applicationContext,
            session = driveOAuthSession
        )
    }

    val driveCoordinator: DriveCoordinator by lazy {
        DriveCoordinator(
            context = context.applicationContext,
            tokenStore = driveTokenStore,
            tokenApi = driveTokenApi,
            clientId = BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID
        )
    }

    /**
     * Real, non-null Drive data source built from the current token.
     * `isEnabled` is driven by `token == null` (see [DriveCoordinator.isEnabled]).
     * Lazy: only created when Drive sync actually runs, never during the
     * Activity's cold-start path.
     */
    val driveRemoteDataSource: StorageSyncRemoteDataSource by lazy {
        driveCoordinator.buildDataSource()
    }

    val dictionaryRepository: DictionaryRepository = DictionaryRepositoryImpl(
        dao = appDatabase.dictionaryWordDao()
    )

    // ── Supabase Auth ───────────────────────────────────────────────

    // The Supabase client is intentionally NOT created here anymore:
    // constructing it cost ~2s on the main thread during cold start
    // (measured via logcat: AppContainer fully initialized in ~1950ms).
    // It is now warmed asynchronously from NextPageApplication.onCreate
    // on Dispatchers.IO and created lazily on first use.

    val sessionManager: SessionManager by lazy {
        SupabaseSessionManager()
    }

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(
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
            remoteDataSource = driveRemoteDataSource,
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            isEnabled = driveCoordinator::isEnabled,
            tokenRefresher = { driveCoordinator.refreshAccessToken() },
            diagnosticError = AppError(
                category = com.nextpage.domain.error.ErrorCategory.CONFIG_ERROR,
                code = "SYNC_DRIVE_NOT_AUTHORIZED",
                message = "Google Drive not authorized. Authorize in Settings → Data & Storage.",
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
            bookDao = appDatabase.bookDao(),
            readingProgressDao = appDatabase.readingProgressDao(),
            bookmarkDao = appDatabase.bookmarkDao(),
            highlightDao = appDatabase.highlightDao(),
            readingSessionDao = appDatabase.readingSessionDao(),
            sessionManager = sessionManager,
            dataSource = supabaseProgressDataSource
        )
    }

    val supabaseBookCatalogDataSource: SupabaseBookCatalogDataSource by lazy {
        SupabaseBookCatalogDataSource()
    }

    val supabaseBookCatalogSync: SupabaseBookCatalogSync by lazy {
        SupabaseBookCatalogSync(
            outboxDao = appDatabase.syncOutboxDao(),
            bookDao = appDatabase.bookDao(),
            sessionManager = sessionManager,
            dataSource = supabaseBookCatalogDataSource,
            remoteDataSource = driveRemoteDataSource,
            driveTokenRefresher = { driveCoordinator.refreshAccessToken() },
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            progressDataSource = supabaseProgressDataSource
        )
    }

    val driveColdBackupService: DriveColdBackupService by lazy {
        DriveColdBackupService(
            remoteDataSource = driveRemoteDataSource,
            bookDao = appDatabase.bookDao(),
            readingProgressDao = appDatabase.readingProgressDao(),
            highlightDao = appDatabase.highlightDao(),
            bookmarkDao = appDatabase.bookmarkDao(),
            readingSessionDao = appDatabase.readingSessionDao(),
            bookCatalogDataSource = supabaseBookCatalogDataSource,
            progressDataSource = supabaseProgressDataSource,
            sessionManager = sessionManager
        )
    }

    // ── Diagnostic properties ───────────────────────────────────────

    val isAuthConfigError: Boolean
        get() = BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()

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
}
