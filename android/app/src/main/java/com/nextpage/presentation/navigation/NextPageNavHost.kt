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
import com.nextpage.presentation.screen.AuthScreen
import com.nextpage.presentation.screen.BookDetailScreen
import com.nextpage.presentation.screen.EditBookMetadataScreen
import com.nextpage.presentation.screen.ForgotScreen
import com.nextpage.presentation.screen.HighlightsScreen
import com.nextpage.presentation.screen.HomeScreen
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.screen.OnboardingGoalScreen
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.screen.RegisterScreen
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
import com.nextpage.data.remote.sync.SyncState
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

    // ── Drive connect prompt (one-time, per Google account) ───────────
    // SharedPreferences-backed per-account decline + pure decision gate. The
    // accept path reuses the PR2 singleton helper (same pending PKCE state and
    // authResult channel as the Settings screen).
    val driveConnectPromptPrefs = remember { DriveConnectPromptPrefs(context) }
    val driveAuthHelper = appContainer.googleDriveAuthHelper
    var showDriveConnectPrompt by rememberSaveable { mutableStateOf(false) }
    // True while the browser flow launched BY THIS PROMPT is in flight: the
    // authResult collector below only surfaces feedback for this flow (Settings
    // owns feedback for the flows it launches — avoids double toasts).
    var drivePromptAuthInFlight by remember { mutableStateOf(false) }

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
            homeRepository = appContainer.homeRepository
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
            syncService = appContainer.syncService,
            supabaseProgressSync = appContainer.supabaseProgressSync,
            supabaseBookCatalogSync = appContainer.supabaseBookCatalogSync,
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

    // Reactively push the restored/later auth session into the cached Home VM
    // so the avatar photo updates after async session restore (the VM keyed by
    // factory is never rebuilt, so the constructor seed alone would stay null).
    // Also re-scopes daily stats/streak and recorded reading sessions to the user
    // (REQ-reading-sessions-sync-6, REQ-streak-widget-1).
    LaunchedEffect(authState.currentSession?.userId, authState.currentSession?.photoUrl) {
        homeViewModel.setActiveSession(authState.currentSession)
        appContainer.getStatisticsUseCase.setUserId(authState.currentSession?.userId)
        readerViewModel.setActiveUserId(authState.currentSession?.userId.orEmpty())
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
            when (event) {
                is com.nextpage.presentation.viewmodel.LibraryImportEvent.Success -> {
                    snackbarHostState.showSnackbar(
                        context.getString(com.nextpage.R.string.library_import_success, event.title)
                    )
                    // Auto-push after import: when Drive is authorized, push the
                    // just-imported book to Drive and the Supabase catalog WITHOUT
                    // requiring a manual pull-to-refresh. Idempotent — processed
                    // outbox entries are deleted; failures surface via syncState.
                    scope.launch {
                        appContainer.syncService.schedulePush()
                    }
                    // One-time "connect Google Drive?" prompt (spec drive-import-connect):
                    // only for a Google-signed-in account with Drive still unauthorized
                    // and no prior per-account decline. Pure gate — no Compose state read
                    // needed for the decision itself.
                    val session = authState.currentSession
                    val shouldOfferPrompt = DriveConnectPromptGate.shouldShow(
                        importSucceeded = true,
                        driveEnabled = driveAuthHelper.isAuthorized(),
                        providerIsGoogle = session?.provider == "google",
                        declinedForUser = driveConnectPromptPrefs.declinedForUser(),
                        currentUser = session?.userId
                    )
                    if (shouldOfferPrompt) {
                        showDriveConnectPrompt = true
                    }
                }
                is com.nextpage.presentation.viewmodel.LibraryImportEvent.Failure -> {
                    snackbarHostState.showSnackbar(
                        context.getString(com.nextpage.R.string.library_import_failure, event.message)
                    )
                }
            }
        }
    }

    // Browser launcher for prompt-initiated Drive auth. The redirect outcome
    // arrives through the helper's authResult (MainActivity.onNewIntent → onRedirect);
    // a plain browser-back close without a redirect is a cancellation — silent.
    val driveConnectAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            drivePromptAuthInFlight = false
        }
    }

    // Redirect-driven outcome for prompt-initiated auth (same singleton flow as
    // Settings): Success → clear the per-account decline so a later Settings
    // disconnect MAY re-offer; Failure → actionable toast (only when this prompt
    // started the flow — Settings toasts its own); Canceled → silent.
    LaunchedEffect(driveAuthHelper) {
        driveAuthHelper.authResult.collect { result ->
            if (result != null) {
                when (result) {
                    is DriveAuthResult.Success -> {
                        driveConnectPromptPrefs.clearDeclined()
                        // Re-trigger sync after Drive authorization so any outbox
                        // entries queued before authorization (e.g. an imported book)
                        // are pushed to Drive and the Supabase catalog now that a
                        // token exists. Idempotent: processed entries are deleted.
                        scope.launch {
                            appContainer.syncService.schedulePush()
                        }
                    }
                    is DriveAuthResult.Failure -> {
                        if (drivePromptAuthInFlight) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(com.nextpage.R.string.settings_drive_error_oauth),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    DriveAuthResult.Canceled -> Unit // cancellation is not an error — no toast
                }
                drivePromptAuthInFlight = false
                driveAuthHelper.consumeResult()
            }
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

    // Onboarding goal gating (REQ-daily-reading-goal-2, SCEN-daily-reading-goal-1/2):
    // authenticated users with no stored goal land on onboarding/goal first.
    val hasDailyGoal = appContainer.readingGoalPreferences.load() != null

    val startDestination = when {
        !isAuthenticated -> NextPageDestination.Auth.route
        !hasDailyGoal -> NextPageDestination.OnboardingGoal.route
        else -> NextPageDestination.Home.route
    }

    // ── Password-reset deep link (nextpage://auth/reset-password) ──────
    // Cold-start resolution: if the app was opened from a reset-password
    // email link while signed out, land on the forgot-password screen.
    // (Warm-start / onNewIntent is intentionally not handled — cold start
    // is the documented scope; see design §deep-link.)
    val launchIntent = remember { (context as? com.nextpage.MainActivity)?.intent?.data }
    LaunchedEffect(launchIntent, isCheckingSession) {
        val isResetPasswordLink = launchIntent?.scheme == "nextpage" &&
            launchIntent.path?.contains("reset-password") == true
        if (isResetPasswordLink && !isCheckingSession && !isAuthenticated) {
            navController.navigate(NextPageDestination.AuthForgot.route) {
                launchSingleTop = true
            }
        }
    }

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
                            val destination = if (appContainer.readingGoalPreferences.load() == null) {
                                NextPageDestination.OnboardingGoal.route
                            } else {
                                NextPageDestination.Home.route
                            }
                            navController.navigate(destination) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        },
                        onContinueLocal = {
                            authViewModel.continueLocally()
                            navController.navigate(NextPageDestination.Home.route) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        },
                        onNavigateToRegister = {
                            navController.navigate(NextPageDestination.AuthRegister.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToForgot = {
                            navController.navigate(NextPageDestination.AuthForgot.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = NextPageDestination.AuthRegister.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    RegisterScreen(
                        viewModel = authViewModel,
                        onAuthenticated = {
                            val destination = if (appContainer.readingGoalPreferences.load() == null) {
                                NextPageDestination.OnboardingGoal.route
                            } else {
                                NextPageDestination.Home.route
                            }
                            navController.navigate(destination) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NextPageDestination.AuthForgot.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    ForgotScreen(
                        viewModel = authViewModel,
                        onAuthenticated = {
                            val destination = if (appContainer.readingGoalPreferences.load() == null) {
                                NextPageDestination.OnboardingGoal.route
                            } else {
                                NextPageDestination.Home.route
                            }
                            navController.navigate(destination) {
                                popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NextPageDestination.OnboardingGoal.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) {
                    OnboardingGoalScreen(
                        onSave = { minutes ->
                            appContainer.readingGoalPreferences.save(minutes)
                            navController.navigate(NextPageDestination.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
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
                        onOpenAccount = {
                            settingsInitialRoute = NextPageDestination.SettingsAccount.route
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
                        onEditBook = { navController.navigate("book_edit/$bookId") },
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
                    route = NextPageDestination.BookEdit.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { it } + fadeOut() },
                    popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { -it } + fadeOut() }
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    EditBookMetadataScreen(
                        contentPadding = innerPadding,
                        bookId = bookId,
                        libraryRepository = appContainer.libraryRepository,
                        coverStorage = appContainer.coverStorage,
                        onNavigateBack = { navController.popBackStack() }
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
                        driveAuthHelper = driveAuthHelper,
                        authSession = authState.currentSession,
                        onOpenAccount = {
                            settingsInitialRoute = NextPageDestination.SettingsAccount.route
                            navController.navigate(NextPageDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        },
                        onBookSelected = { bookId, filePath, format ->
                            selectedBookId = bookId
                            selectedBookFilePath = filePath
                            selectedBookFormat = format
                            navController.navigate("book_detail/$bookId")
                        },
                        onEditBook = { bookId ->
                            navController.navigate("book_edit/$bookId")
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
                        initialRoute = settingsInitialRoute,
                        onInitialRouteConsumed = { settingsInitialRoute = null },
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
                        dictionaryRepository = appContainer.dictionaryRepository,
                        driveAuthHelper = appContainer.googleDriveAuthHelper
                    )
                }

                composable(route = NextPageDestination.LogViewer.route) {
                    LogViewerScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ── Import Overlay (inside wrapper Box, below NavHost) ──
            val importState by libraryViewModel.importState.collectAsStateWithLifecycle()

            // Watchdog (defense-in-depth): if the import state is stuck
            // non-Idle past the timeout, force it back to Idle so the overlay
            // can never permanently swallow taps. The holder already returns
            // to Idle on success/failure/exception via try/finally; this
            // covers pathological hangs (e.g. a blocking input stream).
            LaunchedEffect(importState) {
                if (importState !is BookImportState.Idle) {
                    delay(IMPORT_OVERLAY_WATCHDOG_TIMEOUT_MS)
                    libraryViewModel.resetImportState()
                }
            }

            // Compose the overlay only while an import is actually running —
            // an Idle/stuck overlay must not intercept input.
            if (importState !is BookImportState.Idle) {
                NextPageImportOverlay(
                    importState = importState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Drive connect prompt dialog ────────────────────────────────
        // Shown once per Google account after a successful import while Drive is
        // unauthorized. Accept → launch the shared PKCE browser flow; any dismiss
        // (decline, outside-tap, back) persists the per-account decline.
        if (showDriveConnectPrompt) {
            NextPageDialog(
                title = context.getString(com.nextpage.R.string.drive_connect_prompt_title),
                body = context.getString(com.nextpage.R.string.drive_connect_prompt_body),
                confirmText = context.getString(com.nextpage.R.string.drive_connect_prompt_accept),
                dismissText = context.getString(com.nextpage.R.string.drive_connect_prompt_decline),
                icon = NextPageIcons.CloudDownload,
                onConfirm = {
                    showDriveConnectPrompt = false
                    val clientId = com.nextpage.BuildConfig.GOOGLE_OAUTH_ANDROID_CLIENT_ID
                    if (clientId.isBlank()) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(com.nextpage.R.string.settings_drive_error_config),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@NextPageDialog
                    }
                    drivePromptAuthInFlight = true
                    driveConnectAuthLauncher.launch(driveAuthHelper.beginAuth())
                },
                onDismiss = {
                    showDriveConnectPrompt = false
                    val userId = authState.currentSession?.userId
                    DriveConnectPromptGate.markDeclined(userId)?.let {
                        driveConnectPromptPrefs.persistDeclined(it)
                    }
                }
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
