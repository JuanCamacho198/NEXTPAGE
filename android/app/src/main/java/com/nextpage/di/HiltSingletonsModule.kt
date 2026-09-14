package com.nextpage.di

import android.content.Context
import com.nextpage.data.remote.addons.AddonRegistryLike
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.SupabaseProgressSync
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.remote.sync.SyncOrchestrator
import com.nextpage.data.remote.sync.SyncOrchestratorImpl
import com.nextpage.data.remote.sync.SyncOrchestratorSettleGate
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.sync.SessionGateImpl
import com.nextpage.di.modules.DatabaseModule
import com.nextpage.di.modules.NetworkModule
import com.nextpage.di.modules.PreferencesModule
import com.nextpage.domain.sync.SessionGate
import com.nextpage.domain.sync.SyncSettleGate
import com.nextpage.presentation.navigation.InstallDeepLinkController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * SDD android-tooling-hygiene WS2b slice 5: app-scoped singleton bindings.
 *
 * Exposes [SyncOrchestrator], [SyncSettleGate], and [InstallDeepLinkController]
 * as `@Singleton` Hilt bindings while the manual [AppContainer] keeps serving
 * entry points. Construction is single-sourced through the shared
 * [createSyncOrchestrator]/[createSyncSettleGate]/[createInstallDeepLinkController]
 * factories, which the manual container also delegates to (same pattern as
 * slice 4's `createConnectivityObserver`).
 *
 * Migration bridge (temporary): the data layer is not Hilt-migrated yet, so
 * this module also binds the manual [DatabaseModule]/[PreferencesModule]/
 * [NetworkModule] as `@Singleton` providers. They exist only to satisfy the
 * singleton providers above with shared instances; no `@Inject` consumer
 * exists in this slice, so the Hilt graph is compile-validated but never
 * instantiated at runtime (zero behavior change). Slices 6+ replace the
 * bridge with per-type bindings and flip [AppContainer] to delegate to Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object HiltSingletonsModule {

    @Provides
    @Singleton
    fun provideDatabaseModule(
        @ApplicationContext context: Context
    ): DatabaseModule = DatabaseModule(context.applicationContext)

    @Provides
    @Singleton
    fun providePreferencesModule(
        @ApplicationContext context: Context
    ): PreferencesModule = PreferencesModule(context.applicationContext)

    @Provides
    @Singleton
    fun provideNetworkModule(
        @ApplicationContext context: Context,
        databaseModule: DatabaseModule,
        preferencesModule: PreferencesModule
    ): NetworkModule = NetworkModule(
        context.applicationContext,
        databaseModule,
        preferencesModule
    )

    @Provides
    @Singleton
    fun provideSyncOrchestrator(
        networkModule: NetworkModule,
        databaseModule: DatabaseModule
    ): SyncOrchestrator = createSyncOrchestrator(
        drive = networkModule.syncService,
        catalog = networkModule.supabaseBookCatalogSync,
        progress = networkModule.supabaseProgressSync,
        gate = SessionGateImpl(networkModule.sessionManager),
        outboxDao = databaseModule.syncOutboxDao
    )

    @Provides
    @Singleton
    fun provideSyncSettleGate(
        orchestrator: SyncOrchestrator
    ): SyncSettleGate = createSyncSettleGate(orchestrator)

    @Provides
    @Singleton
    fun provideInstallDeepLinkController(
        networkModule: NetworkModule
    ): InstallDeepLinkController =
        createInstallDeepLinkController(networkModule.addonRegistry)
}

/**
 * Shared factory for the app-lifetime [SyncOrchestrator] singleton.
 *
 * Used by [HiltSingletonsModule.provideSyncOrchestrator] (Hilt graph) and by
 * the manual [AppContainer] graph, so both graphs construct the identical
 * instance type from identical inputs. The default [externalScope] matches
 * the historical manual wiring (SupervisorJob + Default dispatcher).
 */
fun createSyncOrchestrator(
    drive: SyncService,
    catalog: SupabaseBookCatalogSync,
    progress: SupabaseProgressSync,
    gate: SessionGate,
    outboxDao: SyncOutboxDao,
    externalScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
): SyncOrchestrator = SyncOrchestratorImpl(
    drive = drive,
    catalog = catalog,
    progress = progress,
    gate = gate,
    outboxDao = outboxDao,
    externalScope = externalScope,
)

/**
 * Shared factory for the app-lifetime [SyncSettleGate] singleton.
 *
 * Used by [HiltSingletonsModule.provideSyncSettleGate] (Hilt graph) and by
 * the manual [AppContainer] graph.
 */
fun createSyncSettleGate(
    orchestrator: SyncOrchestrator,
    timeoutMillis: Long = SyncOrchestratorSettleGate.DEFAULT_TIMEOUT_MILLIS,
): SyncSettleGate = SyncOrchestratorSettleGate(
    orchestrator = orchestrator,
    timeoutMillis = timeoutMillis,
)

/**
 * Shared factory for the app-lifetime [InstallDeepLinkController] singleton.
 *
 * Used by [HiltSingletonsModule.provideInstallDeepLinkController] (Hilt
 * graph) and by the manual [AppContainer] graph. The default
 * [mainDispatcher] matches the historical manual wiring (Main dispatcher;
 * the controller owns a Main-scoped StateFlow read by the Compose dialog
 * host).
 */
fun createInstallDeepLinkController(
    registry: AddonRegistryLike,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
): InstallDeepLinkController = InstallDeepLinkController(
    registry = registry,
    mainDispatcher = mainDispatcher,
)
