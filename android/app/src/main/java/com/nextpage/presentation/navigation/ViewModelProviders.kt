package com.nextpage.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.data.remote.catalog.CatalogFeaturedSort
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.di.AppContainer
import com.nextpage.domain.usecase.DownloadAndImportBookUseCase
import com.nextpage.presentation.debug.DebugViewModel
import com.nextpage.presentation.feature.discover.DiscoverSectionViewModel
import com.nextpage.presentation.feature.discover.DiscoverSectionViewModelFactory
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.presentation.viewmodel.DiscoverViewModel
import com.nextpage.presentation.viewmodel.DiscoverViewModelFactory
import com.nextpage.presentation.viewmodel.HighlightsViewModel
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModelFactory
import com.nextpage.presentation.viewmodel.StatisticsViewModel

/**
 * Holder grouping the host-scoped ViewModels.
 *
 * Mirrors AppContainer.ReaderDependencies facade pattern (PR #1/2):
 * keeps factory wiring host-local without leaking AppContainer module split (PR #4).
 * ViewModels are created once in the host via [rememberNavHostViewModels] and
 * injected into feature NavGraphBuilders — builders never call viewModel() inside composable.
 */
internal data class ViewModelProviders(
    val library: LibraryViewModel,
    val reader: ReaderViewModel,
    val highlights: HighlightsViewModel,
    val statistics: StatisticsViewModel,
    val auth: AuthViewModel,
    val home: HomeViewModel,
    val debug: DebugViewModel,
    val discover: DiscoverViewModel,
)

/**
 * Creates and remembers all host-scoped ViewModels — the single-sourced path.
 *
 * Plain VMs use `hiltViewModel()`; Reader/Discover keep assisted factories.
 * selectedBookId is still host-owned and passed to Reader via defaultBookId at creation time,
 * then kept in sync via write lambdas (see BookDetail/Reader graphs).
 */
@Composable
internal fun rememberNavHostViewModels(
    appContainer: AppContainer,
    selectedBookId: String,
): ViewModelProviders {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application

    val libraryViewModel: LibraryViewModel = hiltViewModel()

    val readerViewModel: ReaderViewModel =
        viewModel(
            factory =
                ReaderViewModelFactory(
                    application = application,
                    readerRepository = appContainer.readerRepository,
                    readingStatsRepository = appContainer.readingStatsRepository,
                    readerPreferences = appContainer.readerPreferences,
                    defaultBookId = selectedBookId,
                    dictionaryRepository = appContainer.dictionaryRepository,
                    libraryRepository = appContainer.libraryRepository,
                    supabaseProgressSync = appContainer.supabaseProgressSync,
                ),
        )

    val highlightsViewModel: HighlightsViewModel = hiltViewModel()

    val statisticsViewModel: StatisticsViewModel = hiltViewModel()

    val authViewModel: AuthViewModel = hiltViewModel()

    val homeViewModel: HomeViewModel = hiltViewModel()

    val debugViewModel: DebugViewModel = hiltViewModel()

    val discoverViewModel: DiscoverViewModel =
        viewModel(
            factory =
                DiscoverViewModelFactory(
                    catalogProvider = appContainer.catalogProvider,
                    connectivityObserver = appContainer.connectivityObserver,
                    downloadAndImportBookUseCase = appContainer.downloadAndImportBookUseCase,
                    registerAddonChangeListener = { listener ->
                        appContainer.addonRegistry.addOnChangedListener(listener)
                    },
                    addonConsent = { addonId -> appContainer.addonRegistry.hasAddonConsent(addonId) },
                    onAddonConsentChange = { addonId, granted ->
                        if (granted) {
                            appContainer.addonRegistry.recordAddonConsent(addonId)
                        } else {
                            appContainer.addonRegistry.revokeAddonConsent(addonId)
                        }
                    },
                    addonResolve = { addonId, book -> appContainer.addonResolveForBook(addonId, book) },
                ),
        )

    return ViewModelProviders(
        library = libraryViewModel,
        reader = readerViewModel,
        highlights = highlightsViewModel,
        statistics = statisticsViewModel,
        auth = authViewModel,
        home = homeViewModel,
        debug = debugViewModel,
        discover = discoverViewModel,
    )
}

/**
 * Resolves the route-scoped [DiscoverSectionViewModel].
 *
 * Section VMs are scoped to the navigation back-stack entry and depend on the
 * route's `sectionTitle`/`sort`/`sourceId` arguments, so they cannot live in the
 * host-scoped [rememberNavHostViewModels] holder. Resolution still stays
 * single-sourced in this file — [com.nextpage.presentation.navigation.feature.discoverGraph]
 * never calls `viewModel()` itself (enforced by NoInlineViewModelResolutionTest).
 */
@Composable
internal fun rememberDiscoverSectionViewModel(
    catalogProvider: CatalogProvider,
    sectionTitle: String,
    sort: CatalogFeaturedSort?,
    sourceId: String?,
    downloadAndImportBookUseCase: DownloadAndImportBookUseCase?,
): DiscoverSectionViewModel =
    viewModel(
        factory =
            DiscoverSectionViewModelFactory(
                catalogProvider = catalogProvider,
                sectionTitle = sectionTitle,
                sort = sort,
                sourceId = sourceId,
                downloadAndImportBookUseCase = downloadAndImportBookUseCase,
            ),
    )
