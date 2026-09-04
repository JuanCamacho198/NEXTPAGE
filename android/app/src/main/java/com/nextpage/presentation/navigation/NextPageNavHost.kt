package com.nextpage.presentation.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import com.nextpage.ui.components.molecules.BottomNavItem
import com.nextpage.ui.components.molecules.NextPageBottomNavBar
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nextpage.data.remote.drive.DriveAuthResult
import com.nextpage.data.remote.drive.DriveConnectPromptGate
import com.nextpage.data.session.DriveConnectPromptPrefs
import com.nextpage.di.AppContainer
import com.nextpage.presentation.navigation.feature.authGraph
import com.nextpage.presentation.navigation.feature.bookDetailGraph
import com.nextpage.presentation.navigation.feature.homeGraph
import com.nextpage.presentation.navigation.feature.libraryGraph
import com.nextpage.presentation.navigation.feature.onboardingGraph
import com.nextpage.presentation.navigation.feature.readerGraph
import com.nextpage.presentation.navigation.feature.settingsGraph
import com.nextpage.presentation.feature.highlights.HighlightsScreen
import com.nextpage.presentation.feature.bookdetail.BookDetailScreen
import com.nextpage.presentation.feature.editmetadata.EditBookMetadataScreen
import com.nextpage.presentation.screen.OnboardingGoalScreen
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.screen.SettingsScreen
import com.nextpage.presentation.screen.StatisticsScreen
import com.nextpage.presentation.viewmodel.library.BookImportState
import com.nextpage.ui.components.atoms.NextPageDialog
import com.nextpage.ui.components.atoms.NextPageImportOverlay
import com.nextpage.ui.components.atoms.NextPageSnackbar
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.presentation.viewmodel.HomeViewModelFactory
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModelFactory
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModelFactory
import com.nextpage.presentation.util.getContentDisplayName
import com.nextpage.presentation.viewmodel.HighlightsViewModel
import com.nextpage.presentation.viewmodel.HighlightsViewModelFactory
import com.nextpage.presentation.viewmodel.StatisticsViewModel
import com.nextpage.presentation.viewmodel.StatisticsViewModelFactory
import com.nextpage.debug.LogViewerScreen
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.debug.DebugPanel
import com.nextpage.presentation.debug.DebugViewModel
import com.nextpage.debug.DebugPrefs
import com.nextpage.ui.icons.NextPageIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Max time an import may stay non-Idle before the overlay watchdog force-
 * resets it. Generous enough for legitimate large-book imports (the timer
 * restarts on every stage change), yet bounded so a genuinely stuck overlay
 * never blocks the UI forever.
 */
private const val IMPORT_OVERLAY_WATCHDOG_TIMEOUT_MS = 120_000L

@Composable
fun NextPageNavHost(
    appContainer: AppContainer,
    appThemeMode: com.nextpage.domain.model.ThemeMode = com.nextpage.domain.model.ThemeMode.SYSTEM,
    onAppThemeModeChanged: (com.nextpage.domain.model.ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driveConnectPromptPrefs = remember { DriveConnectPromptPrefs(context) }
    val driveAuthHelper = appContainer.googleDriveAuthHelper

    var selectedBookId by rememberSaveable { mutableStateOf("") }
    var selectedBookFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBookFormat by rememberSaveable { mutableStateOf("epub") }

    // One-shot deep-link target for the nested Settings NavHost (e.g. account).
    // Consumed on first composition so a later bottom-nav Settings tap doesn't
    // re-open the stale route.
    var settingsInitialRoute by rememberSaveable { mutableStateOf<String?>(null) }

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
            application = context.applicationContext as android.app.Application,
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

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated = authState.currentSession != null
    val isCheckingSession = authState.isCheckingSession

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            homeRepository = appContainer.homeRepository,
            getStatisticsUseCase = appContainer.getStatisticsUseCase,
            dailyGoalProvider = appContainer.dailyGoalProvider,
            readerRepository = appContainer.readerRepository,
            getBookProgressUseCase = appContainer.getBookProgressUseCase,
            progressReconciler = appContainer.progressReconciler
        )
    )

    SessionSyncEffect(
        session = authState.currentSession,
        homeViewModel = homeViewModel,
        getStatisticsUseCase = appContainer.getStatisticsUseCase,
        readerViewModel = readerViewModel
    )

    val debugViewModel: DebugViewModel = viewModel(
        factory = DebugViewModel.Factory(appContainer)
    )

    var showDebugSheet by remember { mutableStateOf(false) }

    GlobalEventCollector(
        libraryUiEvent = libraryViewModel.uiEvent,
        readerUiEvent = readerViewModel.uiEvent,
        highlightsUiEvent = highlightsViewModel.uiEvent,
        statisticsUiEvent = statisticsViewModel.uiEvent,
        homeUiEvent = homeViewModel.uiEvent,
        authUiEvent = authViewModel.uiEvent,
        navController = navController,
        snackbarHostState = snackbarHostState,
        context = context,
        onOpenBookAtLocation = { event ->
            val book = appContainer.libraryRepository.getBookById(event.bookId)
            if (book != null) {
                selectedBookId = book.id
                selectedBookFilePath = book.filePath
                selectedBookFormat = book.format
                readerViewModel.lifecycleHolder.navigateToCfiAfterLoad(event.cfiRange)
                navController.navigate(NextPageDestination.Reader.route) {
                    launchSingleTop = true
                }
            } else {
                snackbarHostState.showSnackbar(context.getString(com.nextpage.R.string.book_not_found))
            }
        }
    )

    DrivePromptHost(
        driveAuthHelper = driveAuthHelper,
        prefs = driveConnectPromptPrefs,
        authSession = authState.currentSession,
        importEvents = libraryViewModel.importEvents,
        snackbarHostState = snackbarHostState,
        syncService = appContainer.syncService
    )

    // ── Supabase OAuth deep-link handling ────────────────────────────
    // NOTE: Google sign-in now uses native Credential Manager (no browser OAuth).
    // This deep-link handler is kept for backward compatibility with any
    // future OAuth flows that may use browser-based auth, but is currently a no-op
    // for Google sign-in.

    // NOTE: Book loading is handled directly by ReaderScreen via LaunchedEffect(selectedBookId, bookFilePath, bookFormat).
    // No need to pre-load here; restoreProgressForBook is called inside loadBook flow.

    val bottomNavDestinations = listOf(
        NextPageDestination.Home,
        NextPageDestination.Library,
        NextPageDestination.Highlights,
        NextPageDestination.Settings
    )

    // Whitelist de rutas donde el BottomNav debe mostrarse
    val bottomNavRoutes = bottomNavDestinations.map { it.route }.toSet()

    // Onboarding goal gating (REQ-daily-reading-goal-2, SCEN-daily-reading-goal-1/2):
    // authenticated users with no stored goal land on onboarding/goal first.
    // Reactive: bump dailyGoalVersion after save so hasDailyGoal recomputes without needing process restart.
    var dailyGoalVersion by remember { mutableStateOf(0) }
    val hasDailyGoal = remember(dailyGoalVersion) { appContainer.readingGoalPreferences.load() != null }

    val startDestination = when {
        !isAuthenticated -> NextPageDestination.Auth.route
        !hasDailyGoal -> NextPageDestination.OnboardingGoal.route
        else -> NextPageDestination.Home.route
    }

    // ── Password-reset deep link (nextpage://auth/reset-password) ──────
    // Cold-start resolution: if the app was opened from a reset-password
    DeepLinkHandler(
        navController = navController,
        isCheckingSession = isCheckingSession,
        isAuthenticated = isAuthenticated
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCheckingSession) {
            // Fallback while session is being restored. The native splash
            // screen (MainActivity) normally covers this window via
            // setKeepOnScreenCondition; this centered spinner guards against
            // any gap after the splash dismisses before navigation settles.
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    NextPageSnackbar(snackbarData = data)
                }
            },
            bottomBar = {
                if (isAuthenticated) {
                    val currentBackStack = navController.currentBackStackEntryAsState().value
                    val currentRoute = currentBackStack?.destination?.route
                    if (currentRoute != null && currentRoute in bottomNavRoutes) {
                        val bottomNavItems = bottomNavDestinations.map { dest ->
                            BottomNavItem(dest.route, dest.labelRes, checkNotNull(dest.icon))
                        }
                        NextPageBottomNavBar(
                            destinations = bottomNavItems,
                            currentRoute = currentRoute,
                            onTabSelected = { route ->
                                navController.navigateToBottomTab(
                                    route = route,
                                    homeRoute = NextPageDestination.Home.route
                                )
                            }
                        )
                }
            }
        }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    authGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        appContainer = appContainer
                    )

                    onboardingGraph(
                        navController = navController,
                        appContainer = appContainer,
                        onGoalSaved = { dailyGoalVersion++ }
                    )

                homeGraph(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    libraryViewModel = libraryViewModel,
                    contentPadding = innerPadding,
                    onSelectBook = { id, path, format ->
                        selectedBookId = id
                        selectedBookFilePath = path
                        selectedBookFormat = format
                    },
                    onSettingsInitialRoute = { route -> settingsInitialRoute = route }
                )

                bookDetailGraph(
                    navController = navController,
                    appContainer = appContainer,
                    readerViewModel = readerViewModel,
                    contentPadding = innerPadding,
                    onSelectBook = { id, path, format ->
                        selectedBookId = id
                        selectedBookFilePath = path
                        selectedBookFormat = format
                    }
                )

                libraryGraph(
                    navController = navController,
                    libraryViewModel = libraryViewModel,
                    driveAuthHelper = driveAuthHelper,
                    currentSession = authState.currentSession,
                    contentPadding = innerPadding,
                    onSelectBook = { id, path, format ->
                        selectedBookId = id
                        selectedBookFilePath = path
                        selectedBookFormat = format
                    },
                    onSettingsInitialRoute = { route -> settingsInitialRoute = route }
                )

                readerGraph(
                    navController = navController,
                    readerViewModel = readerViewModel,
                    selectedBookId = selectedBookId,
                    selectedBookFilePath = selectedBookFilePath,
                    selectedBookFormat = selectedBookFormat,
                    contentPadding = innerPadding
                )

                composable(
                    route = NextPageDestination.Highlights.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    HighlightsScreen(
                        contentPadding = innerPadding,
                        viewModel = highlightsViewModel,
                        authSession = authState.currentSession,
                        onOpenAccount = {
                            settingsInitialRoute = NextPageDestination.SettingsAccount.route
                            navController.navigate(NextPageDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = NextPageDestination.Statistics.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    StatisticsScreen(
                        contentPadding = innerPadding,
                        viewModel = statisticsViewModel,
                        authSession = authState.currentSession,
                        onOpenAccount = {
                            settingsInitialRoute = NextPageDestination.SettingsAccount.route
                            navController.navigate(NextPageDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                settingsGraph(
                    navController = navController,
                    appContainer = appContainer,
                    authViewModel = authViewModel,
                    statisticsViewModel = statisticsViewModel,
                    dictionaryRepository = appContainer.dictionaryRepository,
                    driveAuthHelper = driveAuthHelper,
                    currentSession = authState.currentSession,
                    settingsInitialRoute = settingsInitialRoute,
                    onSettingsInitialRouteConsumed = { settingsInitialRoute = null },
                    appThemeMode = appThemeMode,
                    onAppThemeModeChanged = onAppThemeModeChanged,
                    contentPadding = innerPadding
                )
            }

            ImportOverlayHost(libraryViewModel = libraryViewModel)
        }

        // DrivePromptHost handles its own dialog; no host-level dialog needed

        // ── Debug FAB ──────────────────────────────────────────────────
        val showDebugFab =
            DebugPrefs.isEnabled(context) &&
            authState.currentSession?.userId?.startsWith("local-") == true

        if (showDebugFab) {
            FloatingActionButton(
                onClick = { showDebugSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Icon(
                    imageVector = NextPageIcons.BugReport,
                    contentDescription = "Debug"
                )
            }
        }

        // ── Debug Panel Sheet ──────────────────────────────────────────
        if (showDebugSheet) {
            DebugPanel(
                viewModel = debugViewModel,
                authViewModel = authViewModel,
                readerViewModel = readerViewModel,
                syncService = appContainer.syncService,
                onDismiss = { showDebugSheet = false }
            )
        }
        }
        }
    }
}
