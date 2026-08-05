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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.nextpage.di.AppContainer
import com.nextpage.domain.usecase.GetStatisticsUseCase
import com.nextpage.presentation.screen.AuthScreen
import com.nextpage.presentation.screen.BookDetailScreen
import com.nextpage.presentation.screen.HighlightsScreen
import com.nextpage.presentation.screen.HomeScreen
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.screen.SettingsScreen
import com.nextpage.presentation.screen.StatisticsScreen
import com.nextpage.presentation.viewmodel.library.BookImportState
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
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.debug.LogViewerScreen
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.debug.DebugPanel
import com.nextpage.presentation.debug.DebugViewModel
import com.nextpage.debug.DebugPrefs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable

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

    var selectedBookId by rememberSaveable { mutableStateOf("") }
    var selectedBookFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBookFormat by rememberSaveable { mutableStateOf("epub") }

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(
            libraryRepository = appContainer.libraryRepository,
            syncService = appContainer.syncService,
            coverStorage = appContainer.coverStorage,
            appContext = context.applicationContext,
            catalogSync = appContainer.supabaseBookCatalogSync
        )
    )

    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(
            application = context.applicationContext as android.app.Application,
            readerRepository = appContainer.readerRepository,
            readingStatsRepository = appContainer.readingStatsRepository,
            readerPreferences = appContainer.readerPreferences,
            defaultBookId = selectedBookId,
            dictionaryRepository = appContainer.dictionaryRepository
        )
    )

    val highlightsViewModel: HighlightsViewModel = viewModel(
        factory = HighlightsViewModelFactory(
            readerRepository = appContainer.readerRepository,
            homeRepository = appContainer.homeRepository
        )
    )

    val statisticsViewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModelFactory(
            GetStatisticsUseCase(
                readingStatsRepository = appContainer.readingStatsRepository,
                homeRepository = appContainer.homeRepository
            )
        )
    )

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(
            authRepository = appContainer.authRepository,
            syncService = appContainer.syncService,
            supabaseProgressSync = appContainer.supabaseProgressSync,
            supabaseBookCatalogSync = appContainer.supabaseBookCatalogSync,
            isAuthConfigured = !appContainer.isAuthConfigError,
            hasAuthWiringIssue = false
        )
    )

    val authState by authViewModel.uiState.collectAsState()
    val isAuthenticated = authState.currentSession != null
    val isCheckingSession = authState.isCheckingSession

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            homeRepository = appContainer.homeRepository
        )
    )

    // Reactively push the restored/later auth session into the cached Home VM
    // so the avatar photo updates after async session restore (the VM keyed by
    // factory is never rebuilt, so the constructor seed alone would stay null).
    LaunchedEffect(authState.currentSession?.userId, authState.currentSession?.photoUrl) {
        homeViewModel.setActiveSession(authState.currentSession)
    }

    val debugViewModel: DebugViewModel = viewModel(
        factory = DebugViewModel.Factory(appContainer)
    )

    var showDebugSheet by remember { mutableStateOf(false) }

    // ── Global Error/UI Event Collection ───────────────────────────
    listOf(
        libraryViewModel.uiEvent,
        readerViewModel.uiEvent,
        highlightsViewModel.uiEvent,
        statisticsViewModel.uiEvent,
        homeViewModel.uiEvent,
        authViewModel.uiEvent
    ).forEach { flow ->
        LaunchedEffect(flow) {
            flow.collect { event ->
                when (event) {
                    is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                    is UiEvent.ShowToast -> android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                    is UiEvent.ShareText -> {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.text)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(com.nextpage.R.string.context_menu_share)
                            )
                        )
                    }
                    is UiEvent.ShareFile -> {
                        val file = java.io.File(event.filePath)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(com.nextpage.R.string.library_share_chooser_title)
                            )
                        )
                    }
                    is UiEvent.CopyToClipboard -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("highlight", event.text))
                        snackbarHostState.showSnackbar(context.getString(com.nextpage.R.string.highlights_snackbar_copied))
                    }
                    is UiEvent.OpenBookAtLocation -> {
                        scope.launch {
                            val book = appContainer.libraryRepository.getBookById(event.bookId)
                            if (book != null) {
                                selectedBookId = book.id
                                selectedBookFilePath = book.filePath
                                selectedBookFormat = book.format
                                readerViewModel.navigateToCfiAfterLoad(event.cfiRange)
                                navController.navigate(NextPageDestination.Reader.route) {
                                    launchSingleTop = true
                                }
                            } else {
                                snackbarHostState.showSnackbar(context.getString(com.nextpage.R.string.book_not_found))
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(libraryViewModel.importEvents) {
        libraryViewModel.importEvents.collect { event ->
            val message = when (event) {
                is com.nextpage.presentation.viewmodel.LibraryImportEvent.Success -> 
                    context.getString(com.nextpage.R.string.library_import_success, event.title)
                is com.nextpage.presentation.viewmodel.LibraryImportEvent.Failure -> 
                    context.getString(com.nextpage.R.string.library_import_failure, event.message)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(appContainer.syncService) {
        appContainer.syncService.syncState.collect { state ->
            if (state is com.nextpage.data.remote.sync.SyncState.Error) {
                snackbarHostState.showSnackbar("Sync error: ${state.message}")
            }
        }
    }

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

    val startDestination = if (!isAuthenticated) {
        NextPageDestination.Auth.route
    } else {
        NextPageDestination.Home.route
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCheckingSession) {
            // Splash screen while session is being restored — prevents white
            // flash between Auth and Home when startDestination is unknown.
            CircularProgressIndicator()
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
                            BottomNavItem(dest.route, dest.labelRes, dest.iconRes)
                        }
                        NextPageBottomNavBar(
                            destinations = bottomNavItems,
                            currentRoute = currentRoute,
                            onTabSelected = { route ->
                                navController.navigate(route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
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
                    composable(
                        route = NextPageDestination.Auth.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    AuthScreen(
                        viewModel = authViewModel,
                        onAuthenticated = {
                            navController.navigate(NextPageDestination.Home.route) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        },
                        onContinueLocal = {
                            authViewModel.continueLocally()
                            navController.navigate(NextPageDestination.Home.route) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = NextPageDestination.Home.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri: Uri? ->
                            if (uri == null) return@rememberLauncherForActivityResult

                            scope.launch {
                                runCatching {
                                    val fileName = getContentDisplayName(context, uri)
                                        ?: uri.lastPathSegment
                                        ?: "imported_book"
                                    val mimeType = context.contentResolver.getType(uri)

                                    if (fileName.endsWith(".pdf", true) || mimeType == "application/pdf") {
                                        val pdfDir = File(context.filesDir, "pdfs")
                                        if (!pdfDir.exists()) pdfDir.mkdirs()
                                        val pdfFile = File(pdfDir, fileName)
                                        withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(uri)?.use { input ->
                                                pdfFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                        libraryViewModel.importPdfBook(
                                            sourcePath = pdfFile.absolutePath,
                                            fallbackTitle = fileName.removeSuffix(".pdf"),
                                            pdfFile = pdfFile
                                        )
                                    } else {
                                        val epubDir = File(context.filesDir, "epubs")
                                        if (!epubDir.exists()) epubDir.mkdirs()
                                        val epubFile = File(epubDir, fileName)
                                        withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(uri)?.use { input ->
                                                epubFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                        libraryViewModel.importBookFromEpub(
                                            sourcePath = epubFile.absolutePath,
                                            fallbackTitle = fileName.removeSuffix(".epub"),
                                            inputStreamProvider = {
                                                epubFile.inputStream()
                                            }
                                        )
                                    }
                                }.onFailure { error ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Import failed: ${error.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )

                    HomeScreen(
                        contentPadding = innerPadding,
                        viewModel = homeViewModel,
                        onNavigateToLibrary = {
                            navController.navigate(NextPageDestination.Library.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToHighlights = {
                            navController.navigate(NextPageDestination.Highlights.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToSettings = {
                            navController.navigate(NextPageDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToStatistics = {
                            navController.navigate(NextPageDestination.Statistics.route)
                        },
                        onBookSelected = { bookId, filePath, format ->
                            selectedBookId = bookId
                            selectedBookFilePath = filePath
                            selectedBookFormat = format
                            navController.navigate("book_detail/$bookId")
                        },
                        onContinueReading = { bookId, filePath, format ->
                            selectedBookId = bookId
                            selectedBookFilePath = filePath
                            selectedBookFormat = format
                            navController.navigate(NextPageDestination.Reader.route) {
                                popUpTo(NextPageDestination.Reader.route) { inclusive = true }
                            }
                        },
                        onImportBook = {
                            importLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
                        }
                    )
                }

                composable(
                    route = NextPageDestination.BookDetail.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() },
                    popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { -it } + fadeOut() }
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    BookDetailScreen(
                        contentPadding = innerPadding,
                        bookId = bookId,
                        libraryRepository = appContainer.libraryRepository,
                        onNavigateBack = { navController.popBackStack() },
                        onContinueReading = { id, filePath, format ->
                            selectedBookId = id
                            selectedBookFilePath = filePath
                            selectedBookFormat = format
                            navController.navigate(NextPageDestination.Reader.route) {
                                popUpTo(NextPageDestination.Reader.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = NextPageDestination.Library.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    LibraryScreen(
                        contentPadding = innerPadding,
                        viewModel = libraryViewModel,
                        onBookSelected = { bookId, filePath, format ->
                            selectedBookId = bookId
                            selectedBookFilePath = filePath
                            selectedBookFormat = format
                            navController.navigate("book_detail/$bookId")
                        }
                    )
                }

                composable(
                    route = NextPageDestination.Reader.route,
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() },
                    popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { -it } + fadeOut() }
                ) {
                    ReaderScreen(
                        contentPadding = innerPadding,
                        selectedBookId = selectedBookId,
                        bookFilePath = selectedBookFilePath,
                        bookFormat = selectedBookFormat,
                        viewModel = readerViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NextPageDestination.Highlights.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    HighlightsScreen(
                        contentPadding = innerPadding,
                        viewModel = highlightsViewModel
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
                        viewModel = statisticsViewModel
                    )
                }

                composable(
                    route = NextPageDestination.Settings.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    SettingsScreen(
                        contentPadding = innerPadding,
                        authSession = authState.currentSession,
                        appThemeMode = appThemeMode,
                        onAppThemeModeChanged = onAppThemeModeChanged,
                        onLogout = {
                            authViewModel.signOut()
                            navController.navigate(NextPageDestination.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        customHighlightColors = appContainer.readerPreferences.load().customHighlightColors,
                        onUpdateCustomHighlightColor = { index, hex ->
                            val prefs = appContainer.readerPreferences
                            val current = prefs.load()
                            val colors = current.customHighlightColors?.toMutableList()
                                ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                            if (index in colors.indices) colors[index] = hex
                            prefs.save(current.copy(customHighlightColors = colors))
                        },
                        onAddCustomHighlightColor = {
                            val prefs = appContainer.readerPreferences
                            val current = prefs.load()
                            val colors = current.customHighlightColors?.toMutableList()
                                ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                            if (colors.size < 5) {
                                colors.add(com.nextpage.domain.model.HighlightColor.YELLOW.hex)
                                prefs.save(current.copy(customHighlightColors = colors))
                            }
                        },
                        onDeleteCustomHighlightColor = { index ->
                            val prefs = appContainer.readerPreferences
                            val current = prefs.load()
                            val colors = current.customHighlightColors?.toMutableList()
                                ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                            if (colors.size > 3 && index in colors.indices) {
                                colors.removeAt(index)
                                prefs.save(current.copy(customHighlightColors = colors))
                            }
                        },
                        onResetCustomHighlightColors = {
                            val prefs = appContainer.readerPreferences
                            val current = prefs.load()
                            prefs.save(current.copy(customHighlightColors = null))
                        },
                        onNavigateToLogViewer = {
                            navController.navigate(NextPageDestination.LogViewer.route)
                        },
                        statisticsViewModel = statisticsViewModel,
                        dictionaryRepository = appContainer.dictionaryRepository
                    )
                }

                composable(route = NextPageDestination.LogViewer.route) {
                    LogViewerScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ── Import Overlay (inside wrapper Box, below NavHost) ──
            val importState by libraryViewModel.importState.collectAsState()
            NextPageImportOverlay(
                importState = importState,
                modifier = Modifier.fillMaxSize()
            )
        }

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
                    imageVector = Icons.Default.BugReport,
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
