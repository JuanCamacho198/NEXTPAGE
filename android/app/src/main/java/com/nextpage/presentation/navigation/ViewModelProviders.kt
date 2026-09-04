package com.nextpage.presentation.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.di.AppContainer
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.presentation.debug.DebugViewModel
import com.nextpage.presentation.viewmodel.HighlightsViewModel
import com.nextpage.presentation.viewmodel.HighlightsViewModelFactory
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.presentation.viewmodel.HomeViewModelFactory
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModelFactory
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModelFactory
import com.nextpage.presentation.viewmodel.StatisticsViewModel
import com.nextpage.presentation.viewmodel.StatisticsViewModelFactory

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
    val debug: DebugViewModel
)

/**
 * Creates and remembers all host-scoped ViewModels via AppContainer factories.
 *
 * Preserves exact factory wiring from the 931-line monolith; no behavior change.
 * selectedBookId is still host-owned and passed to Reader via defaultBookId at creation time,
 * then kept in sync via write lambdas (see BookDetail/Reader graphs).
 */
@Composable
internal fun rememberNavHostViewModels(
    appContainer: AppContainer,
    selectedBookId: String
): ViewModelProviders {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(
            libraryRepository = appContainer.libraryRepository,
            syncService = appContainer.syncService,
            appContext = context.applicationContext,
            catalogSync = appContainer.supabaseBookCatalogSync,
            readerRepository = appContainer.readerRepository,
            getBookProgressUseCase = appContainer.getBookProgressUseCase,
            progressReconciler = appContainer.progressReconciler
        )
    )

    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(
            application = application,
            readerRepository = appContainer.readerRepository,
            readingStatsRepository = appContainer.readingStatsRepository,
            readerPreferences = appContainer.readerPreferences,
            defaultBookId = selectedBookId,
            dictionaryRepository = appContainer.dictionaryRepository,
            supabaseProgressSync = appContainer.supabaseProgressSync
        )
    )

    val highlightsViewModel: HighlightsViewModel = viewModel(
        factory = HighlightsViewModelFactory(
            readerRepository = appContainer.readerRepository,
            homeRepository = appContainer.homeRepository,
            supabaseSync = appContainer.supabaseProgressSync
        )
    )

    val statisticsViewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModelFactory(
            appContainer.getStatisticsUseCase
        )
    )

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(
            authRepository = appContainer.authRepository,
            syncOrchestrator = appContainer.syncOrchestrator,
            isAuthConfigured = !appContainer.isAuthConfigError,
            hasAuthWiringIssue = false
        )
    )

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            homeRepository = appContainer.homeRepository,
            getStatisticsUseCase = appContainer.getStatisticsUseCase,
            dailyGoalProvider = appContainer.dailyGoalProvider,
            readerRepository = appContainer.readerRepository,
            getBookProgressUseCase = appContainer.getBookProgressUseCase
        )
    )

    val debugViewModel: DebugViewModel = viewModel(
        factory = DebugViewModel.Factory(appContainer)
    )

    return ViewModelProviders(
        library = libraryViewModel,
        reader = readerViewModel,
        highlights = highlightsViewModel,
        statistics = statisticsViewModel,
        auth = authViewModel,
        home = homeViewModel,
        debug = debugViewModel
    )
}
