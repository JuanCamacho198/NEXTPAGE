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
import com.nextpage.domain.connectivity.ConnectivityObserver
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.data.connectivity.AndroidConnectivityObserver
import com.nextpage.data.remote.catalog.ANDROID_USER_AGENT
import com.nextpage.data.remote.catalog.CatalogHttpTransport
import com.nextpage.data.remote.catalog.CatalogFileDownloader
import com.nextpage.data.remote.catalog.KtorCatalogFileDownloader
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.CompositeCatalogProvider
import com.nextpage.data.remote.catalog.GutendexCatalogProvider
import com.nextpage.data.remote.catalog.GutendexDataSource
import com.nextpage.data.remote.catalog.KtorCatalogHttpTransport
import com.nextpage.data.remote.catalog.OpenLibraryCatalogProvider
import com.nextpage.data.remote.catalog.OpenLibraryDataSource
import com.nextpage.data.remote.addons.CuratedCatalogProvider
import com.nextpage.data.remote.addons.AddonRegistry
import com.nextpage.data.remote.addons.KtorAddonHttpTransport
import com.nextpage.data.remote.addons.catalogProvidersWithAddons
import com.nextpage.data.remote.catalog.LiveCatalogProvider
import com.nextpage.data.remote.catalog.RebuildingCatalogProvider
import com.nextpage.data.remote.catalog.RoomDiscoverCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
    // Separate OkHttp stack (timeouts, JSON, identified UA, HTTPS-only by
    // constant base URLs + network_security_config) so public catalog
    // traffic never shares the Drive/Supabase client. No user_books or
    // outbox writes pass through here — search/detail only.
    // Engine is OkHttp (not CIO): CIO failed to reach the catalog on real
    // devices while the OkHttp-backed Supabase/Drive clients worked, so the
    // catalog now uses the proven engine.
    val catalogHttpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
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

    // discover-screen U3a: binary catalog downloads reuse the catalog client
    // identity (UA + timeouts) but live on their own streaming port.
    val catalogFileDownloader: CatalogFileDownloader by lazy {
        KtorCatalogFileDownloader(catalogHttpClient)
    }

    /** Internal storage where catalog downloads stage before import. */
    val catalogTempDir: java.io.File by lazy {
        java.io.File(context.filesDir, "catalog")
    }

    val gutendexDataSource: GutendexDataSource by lazy {
        GutendexDataSource(catalogTransport)
    }

    val openLibraryDataSource: OpenLibraryDataSource by lazy {
        OpenLibraryDataSource(catalogTransport)
    }

    val addonRegistry: AddonRegistry by lazy {
        AddonRegistry(
            databaseModule.installedAddonDao,
            KtorAddonHttpTransport(catalogHttpClient)
        )
    }

    // ── addon-registry PR4: live composite ─────────────────────────────
    // The composite rebuilds from installed addon rows whenever the registry
    // mutates (install/enable/disable/uninstall), so addon sources stop or
    // start contributing immediately; rows are re-read after restart (A1/A4).
    val addonTransport: KtorAddonHttpTransport by lazy {
        KtorAddonHttpTransport(catalogHttpClient)
    }

    val rebuildingCatalogProvider: RebuildingCatalogProvider by lazy {
        val provider = RebuildingCatalogProvider {
            CompositeCatalogProvider(
                catalogProvidersWithAddons(
                    builtIns = listOf(
                        GutendexCatalogProvider(gutendexDataSource),
                        OpenLibraryCatalogProvider(openLibraryDataSource)
                    ),
                    curated = CuratedCatalogProvider(context),
                    installedAddons = addonRegistry.listInstalled(),
                    addonTransport = addonTransport
                ),
                cache = RoomDiscoverCache(databaseModule.discoverCacheDao)
            )
        }
        addonRegistry.addOnChangedListener { provider.invalidate() }
        provider
    }

    val catalogProvider: CatalogProvider by lazy { LiveCatalogProvider(rebuildingCatalogProvider) }

    // discover-screen U1: app-lifetime connectivity observer. Network callbacks
    // are process-global, so this singleton has no cleanup hook.
    val connectivityObserver: ConnectivityObserver by lazy {
        AndroidConnectivityObserver(context)
    }
}
