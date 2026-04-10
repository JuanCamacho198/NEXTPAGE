package com.nextpage.presentation.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nextpage.di.AppContainer
import com.nextpage.presentation.screen.AuthScreen
import com.nextpage.presentation.screen.HighlightsScreen
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModelFactory
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModelFactory
import com.nextpage.presentation.viewmodel.HighlightsViewModel
import com.nextpage.presentation.viewmodel.HighlightsViewModelFactory

@Composable
fun NextPageNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    var selectedBookId by remember { mutableStateOf("") }
    var selectedBookFilePath by remember { mutableStateOf<String?>(null) }
    var selectedBookFormat by remember { mutableStateOf("epub") }
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(appContainer.libraryRepository)
    )
    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(
            readerRepository = appContainer.readerRepository,
            readingStatsRepository = appContainer.readingStatsRepository,
            epubContentLoader = appContainer.epubContentLoader,
            pdfContentLoader = appContainer.pdfContentLoader,
            defaultBookId = selectedBookId
        )
    )
    val highlightsViewModel: HighlightsViewModel = viewModel(
        factory = HighlightsViewModelFactory(appContainer.readerRepository)
    )

    val authViewModel: AuthViewModel = remember {
        AuthViewModel(
            authRepository = appContainer.authRepository,
            isSupabaseConfigured = appContainer.supabaseClientProvider.isConfigured
        )
    }
    val authState by authViewModel.uiState.collectAsState()
    val isAuthenticated = authState.currentSession != null

    LaunchedEffect(selectedBookId) {
        if (selectedBookId.isNotBlank()) {
            readerViewModel.restoreProgressForBook(selectedBookId)
        }
    }

    val destinations = listOf(
        NextPageDestination.Library,
        NextPageDestination.Reader,
        NextPageDestination.Highlights
    )

    val startDestination = if (appContainer.supabaseClientProvider.isConfigured && !isAuthenticated) {
        NextPageDestination.Auth.route
    } else {
        NextPageDestination.Library.route
    }

    Scaffold(
        bottomBar = {
            if (isAuthenticated) {
                val currentBackStack = navController.currentBackStackEntryAsState().value
                val currentDestination = currentBackStack?.destination
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination
                                ?.hierarchy
                                ?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            },
                            icon = { Text(text = stringResource(destination.labelRes).take(1)) },
                            label = { Text(text = stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(NextPageDestination.Auth.route) {
                AuthScreen(
                    viewModel = authViewModel,
                    onAuthenticated = {
                        navController.navigate(NextPageDestination.Library.route) {
                            popUpTo(NextPageDestination.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(NextPageDestination.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    viewModel = libraryViewModel,
                    onBookSelected = { bookId, filePath, format ->
                        selectedBookId = bookId
                        selectedBookFilePath = filePath
                        selectedBookFormat = format
                        navController.navigate(NextPageDestination.Reader.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NextPageDestination.Reader.route) {
                ReaderScreen(
                    contentPadding = innerPadding,
                    selectedBookId = selectedBookId,
                    bookFilePath = selectedBookFilePath,
                    bookFormat = selectedBookFormat,
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NextPageDestination.Highlights.route) {
                HighlightsScreen(
                    contentPadding = innerPadding,
                    viewModel = highlightsViewModel
                )
            }
        }
    }
}
