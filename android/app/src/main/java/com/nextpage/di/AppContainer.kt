package com.nextpage.di

import android.content.Context
import android.util.Log
import coil.ImageLoader
import com.nextpage.BuildConfig
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.BookmarkDao
import com.nextpage.data.local.dao.HighlightDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.remote.drive.DriveCoordinator
import com.nextpage.data.remote.drive.DriveOAuthSession
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.remote.supabase.SupabaseBookCatalogDataSource
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.SupabaseProgressDataSource
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.remote.sync.DriveColdBackupService
import com.nextpage.data.remote.sync.OutboxCommit
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.data.session.ReadingGoalPreferences
import com.nextpage.data.session.SessionManager
import com.nextpage.data.sync.SessionGateImpl
import com.nextpage.domain.sync.SessionGate
import com.nextpage.di.modules.DatabaseModule
import com.nextpage.di.modules.NetworkModule
import com.nextpage.di.modules.PreferencesModule
import com.nextpage.di.modules.RepositoryModule
import com.nextpage.di.modules.StorageModule
import com.nextpage.di.modules.UseCaseModule
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.GetBookProgressUseCase
import com.nextpage.domain.usecase.GetStatisticsUseCase
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.data.storage.AppInternalCoverStorage

class AppContainer(context: Context) {
    companion object {
        private const val TAG = "AppContainer"
    }

    private val startTime = System.currentTimeMillis()

    private val databaseModule = DatabaseModule(context.applicationContext)
    private val storageModule = StorageModule(context.applicationContext, databaseModule)
    private val preferencesModule = PreferencesModule(context.applicationContext)
    private val repositoryModule = RepositoryModule(context.applicationContext, databaseModule, storageModule, preferencesModule)
    private val networkModule = NetworkModule(context.applicationContext, databaseModule, preferencesModule)
    private val useCaseModule = UseCaseModule(repositoryModule, databaseModule, preferencesModule)

    // ── Eager delegation via get() — no double init ───────────────────
    val coverStorage: AppInternalCoverStorage get() = storageModule.coverStorage
    val coilImageLoader: ImageLoader get() = storageModule.coilImageLoader
    val libraryRepository: LibraryRepository get() = repositoryModule.libraryRepository
    val readerRepository: ReaderRepository get() = repositoryModule.readerRepository
    val readingStatsRepository: ReadingStatsRepository get() = repositoryModule.readingStatsRepository
    val homeRepository: HomeRepository get() = repositoryModule.homeRepository
    val dictionaryRepository: DictionaryRepository get() = repositoryModule.dictionaryRepository
    val readerPreferences: ReaderPreferences get() = preferencesModule.readerPreferences
    val readingGoalPreferences: ReadingGoalPreferences get() = preferencesModule.readingGoalPreferences
    val dailyGoalProvider: () -> Int get() = preferencesModule.dailyGoalProvider

    // ── Lazy delegation — preserves cold-start partition ───────────────
    val updateReadingProgressUseCase: UpdateReadingProgressUseCase by lazy { useCaseModule.updateReadingProgressUseCase }
    val getStatisticsUseCase: GetStatisticsUseCase by lazy { useCaseModule.getStatisticsUseCase }
    val getBookProgressUseCase: GetBookProgressUseCase by lazy { useCaseModule.getBookProgressUseCase }
    val progressReconciler: com.nextpage.data.sync.ProgressReconciler by lazy { useCaseModule.progressReconciler }
    val driveOAuthSession: DriveOAuthSession by lazy { networkModule.driveOAuthSession }
    val googleDriveAuthHelper: GoogleDriveAuthHelper by lazy { networkModule.googleDriveAuthHelper }
    val driveCoordinator: DriveCoordinator by lazy { networkModule.driveCoordinator }
    val driveRemoteDataSource: StorageSyncRemoteDataSource by lazy { networkModule.driveRemoteDataSource }
    val sessionManager: SessionManager by lazy { networkModule.sessionManager }
    val authRepository: AuthRepository by lazy { networkModule.authRepository }
    val syncService: SyncService by lazy { networkModule.syncService }
    val supabaseProgressDataSource: SupabaseProgressDataSource by lazy { networkModule.supabaseProgressDataSource }
    val supabaseProgressSync: SupabaseProgressSync by lazy { networkModule.supabaseProgressSync }
    val supabaseBookCatalogDataSource: SupabaseBookCatalogDataSource by lazy { networkModule.supabaseBookCatalogDataSource }
    val supabaseBookCatalogSync: SupabaseBookCatalogSync by lazy { networkModule.supabaseBookCatalogSync }
    val driveColdBackupService: DriveColdBackupService by lazy { networkModule.driveColdBackupService }

    // ── sync-layer-split PR-1 foundations ────────────────────────────────
    // SessionGate + OutboxCommit are the two helpers extracted in PR-1; the
    // SyncOrchestrator that wires them into a per-domain lifecycle arrives in
    // PR-2. `syncState` is a sealed type, not a singleton, so it is not wired
    // here — consumers (DebugViewModel) continue reading per-domain states
    // until the orchestrator exists.
    val sessionGate: SessionGate by lazy { SessionGateImpl(sessionManager) }
    val outboxCommit: OutboxCommit by lazy { OutboxCommit(databaseModule.syncOutboxDao) }

    internal object ReaderDependencies {
        fun updateReadingProgressUseCase(readerRepository: ReaderRepository) =
            UpdateReadingProgressUseCase(readerRepository)
    }

    internal object ReaderInteractionDependencies {
        fun interactionStore(
            state: kotlinx.coroutines.flow.MutableStateFlow<com.nextpage.presentation.viewmodel.reader.ReaderInteractionState>,
            clearEvent: kotlinx.coroutines.flow.MutableSharedFlow<Unit>
        ) = com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore(state, clearEvent)

        fun selectionManager(
            store: com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore,
            scope: kotlinx.coroutines.CoroutineScope,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ) = com.nextpage.presentation.viewmodel.reader.interaction.SelectionManager(store, scope, dispatcher)

        fun highlightManager(
            store: com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore,
            selectionManager: com.nextpage.presentation.viewmodel.reader.interaction.SelectionManager,
            readerRepository: ReaderRepository,
            scope: kotlinx.coroutines.CoroutineScope,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ) = com.nextpage.presentation.viewmodel.reader.interaction.HighlightManager(store, selectionManager, readerRepository, scope, dispatcher)

        fun annotationManager(
            store: com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore,
            selectionManager: com.nextpage.presentation.viewmodel.reader.interaction.SelectionManager,
            readerRepository: ReaderRepository,
            scope: kotlinx.coroutines.CoroutineScope,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ) = com.nextpage.presentation.viewmodel.reader.interaction.AnnotationManager(store, selectionManager, readerRepository, scope, dispatcher)

        fun bookmarkManager(
            store: com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore,
            readerRepository: ReaderRepository,
            scope: kotlinx.coroutines.CoroutineScope,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ) = com.nextpage.presentation.viewmodel.reader.interaction.BookmarkManager(store, readerRepository, scope, dispatcher)

        fun shareDictionaryManager(
            store: com.nextpage.presentation.viewmodel.reader.interaction.InteractionStateStore,
            selectionManager: com.nextpage.presentation.viewmodel.reader.interaction.SelectionManager,
            dictionaryRepository: DictionaryRepository?,
            scope: kotlinx.coroutines.CoroutineScope,
            onEvent: (com.nextpage.presentation.UiEvent) -> Unit,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ) = com.nextpage.presentation.viewmodel.reader.interaction.ShareDictionaryManager(store, selectionManager, dictionaryRepository, scope, onEvent, dispatcher)
    }

    val isAuthConfigError: Boolean
        get() = BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()

    private val totalInitTime = System.currentTimeMillis() - startTime
    init {
        Log.i(TAG, "AppContainer fully initialized in ${totalInitTime}ms")
    }

    val dbInitTimeMs: Long get() = databaseModule.dbInitTimeMs
    val epubImportInitTimeMs: Long get() = repositoryModule.epubImportInitTimeMs
    val readerRepoInitTimeMs: Long get() = repositoryModule.readerRepoInitTimeMs
    val totalInitTimeMs: Long get() = totalInitTime

    val bookDao: BookDao get() = databaseModule.bookDao
    val highlightDao: HighlightDao get() = databaseModule.highlightDao
    val bookmarkDao: BookmarkDao get() = databaseModule.bookmarkDao
    val readingSessionDao: ReadingSessionDao get() = databaseModule.readingSessionDao
    val readingProgressDao: ReadingProgressDao get() = databaseModule.readingProgressDao

    fun clearAllData() {
        databaseModule.clearAllTables()
    }
}
