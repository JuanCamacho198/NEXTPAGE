package com.nextpage.di.modules

import android.content.Context
import com.nextpage.BuildConfig
import com.nextpage.data.remote.drive.DriveCoordinator
import com.nextpage.data.remote.drive.DriveOAuthSession
import com.nextpage.data.remote.drive.DriveTokenApi
import com.nextpage.data.remote.drive.DriveTokenStore
import com.nextpage.data.remote.drive.EncryptedDriveTokenStore
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.remote.drive.InMemoryDriveTokenStore
import com.nextpage.data.remote.drive.KtorAuthApi
import com.nextpage.data.remote.drive.driveOAuthRedirectUri
import com.nextpage.data.remote.supabase.SupabaseBookCatalogDataSource
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.SupabaseProgressDataSource
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.remote.sync.DriveColdBackupService
import com.nextpage.data.remote.sync.GoogleDriveSyncService
import com.nextpage.data.remote.sync.OutboxCommit
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.repository.SupabaseAuthRepository
import com.nextpage.data.session.SessionManager
import com.nextpage.data.session.SupabaseSessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.data.remote.catalog.ANDROID_USER_AGENT
import com.nextpage.data.remote.catalog.CatalogHttpTransport
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.CompositeCatalogProvider
import com.nextpage.data.remote.catalog.GutendexDataSource
import com.nextpage.data.remote.catalog.KtorCatalogHttpTransport
import com.nextpage.data.remote.catalog.OpenLibraryDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class NetworkModule(
    private val context: Context,
    private val databaseModule: DatabaseModule,
    @Suppress("UNUSED_PARAMETER") private val preferencesModule: PreferencesModule
) {
    val driveTokenStore: DriveTokenStore by lazy {
        runCatching { EncryptedDriveTokenStore(context.applicationContext) }
            .getOrElse { InMemoryDriveTokenStore() }
    }

    val driveTokenApi: DriveTokenApi by lazy {
        KtorAuthApi(HttpClient())
    }

    val driveOAuthSession: DriveOAuthSession by lazy {
        DriveOAuthSession(
            clientId = BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID,
            redirectUri = driveOAuthRedirectUri(BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID),
            tokenStore = driveTokenStore,
            tokenApi = driveTokenApi
        )
    }

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

    val driveRemoteDataSource: StorageSyncRemoteDataSource by lazy {
        driveCoordinator.buildDataSource()
    }

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
            outboxDao = databaseModule.syncOutboxDao,
            bookDao = databaseModule.bookDao,
            mappingDao = databaseModule.syncFileMappingDao,
            readingProgressDao = databaseModule.readingProgressDao,
            highlightDao = databaseModule.highlightDao,
            bookmarkDao = databaseModule.bookmarkDao,
            sessionManager = sessionManager,
            remoteDataSource = driveRemoteDataSource,
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            isEnabled = driveCoordinator::isEnabled,
            tokenRefresher = { driveCoordinator.refreshAccessToken() },
            diagnosticError = AppError(
                category = ErrorCategory.CONFIG_ERROR,
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
            outboxDao = databaseModule.syncOutboxDao,
            bookDao = databaseModule.bookDao,
            readingProgressDao = databaseModule.readingProgressDao,
            bookmarkDao = databaseModule.bookmarkDao,
            highlightDao = databaseModule.highlightDao,
            readingSessionDao = databaseModule.readingSessionDao,
            sessionManager = sessionManager,
            dataSource = supabaseProgressDataSource,
            outboxCommit = outboxCommit
        )
    }

    val supabaseBookCatalogDataSource: SupabaseBookCatalogDataSource by lazy {
        SupabaseBookCatalogDataSource()
    }

    val supabaseBookCatalogSync: SupabaseBookCatalogSync by lazy {
        SupabaseBookCatalogSync(
            outboxDao = databaseModule.syncOutboxDao,
            bookDao = databaseModule.bookDao,
            sessionManager = sessionManager,
            dataSource = supabaseBookCatalogDataSource,
            remoteDataSource = driveRemoteDataSource,
            driveTokenRefresher = { driveCoordinator.refreshAccessToken() },
            localBooksDir = context.applicationContext.filesDir.resolve("books"),
            progressDataSource = supabaseProgressDataSource,
            outboxCommit = outboxCommit
        )
    }

    val driveColdBackupService: DriveColdBackupService by lazy {
        DriveColdBackupService(
            remoteDataSource = driveRemoteDataSource,
            bookDao = databaseModule.bookDao,
            readingProgressDao = databaseModule.readingProgressDao,
            highlightDao = databaseModule.highlightDao,
            bookmarkDao = databaseModule.bookmarkDao,
            readingSessionDao = databaseModule.readingSessionDao,
            bookCatalogDataSource = supabaseBookCatalogDataSource,
            progressDataSource = supabaseProgressDataSource,
            sessionManager = sessionManager
        )
    }

    // ── sync-layer-split PR-1: OutboxCommit helper ────────────────────────
    // Centralised ack/increment/prune policy used by both Supabase syncers.
    // Lives in NetworkModule alongside the per-domain syncers that consume it.
    val outboxCommit: OutboxCommit by lazy { OutboxCommit(databaseModule.syncOutboxDao) }

    // ── discover-catalog PR-2: dedicated catalog client ───────────────
    // Separate CIO stack (timeouts, JSON, identified UA, HTTPS-only by
    // constant base URLs + network_security_config) so public catalog
    // traffic never shares the Drive/Supabase client. No user_books or
    // outbox writes pass through here — search/detail only.
    val catalogHttpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, ANDROID_USER_AGENT)
            }
        }
    }

    val catalogTransport: CatalogHttpTransport by lazy {
        KtorCatalogHttpTransport(catalogHttpClient)
    }

    val gutendexDataSource: GutendexDataSource by lazy {
        GutendexDataSource(catalogTransport)
    }

    val openLibraryDataSource: OpenLibraryDataSource by lazy {
        OpenLibraryDataSource(catalogTransport)
    }

    val catalogProvider: CatalogProvider by lazy {
        CompositeCatalogProvider(gutendexDataSource, openLibraryDataSource)
    }
}
