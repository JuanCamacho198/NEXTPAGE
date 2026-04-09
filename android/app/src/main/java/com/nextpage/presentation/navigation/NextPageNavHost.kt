package com.nextpage.presentation.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nextpage.presentation.screen.HighlightsScreen
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.viewmodel.LibraryViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModelFactory
import com.nextpage.presentation.viewmodel.ReaderViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModelFactory

@Composable
fun NextPageNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    var selectedBookId by remember { mutableStateOf("") }
    var selectedBookFilePath by remember { mutableStateOf<String?>(null) }
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(appContainer.libraryRepository)
    )
    val readerViewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(
            readerRepository = appContainer.readerRepository,
            epubContentLoader = appContainer.epubContentLoader,
            defaultBookId = selectedBookId
        )
    )

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

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NextPageDestination.Library.route
        ) {
            composable(NextPageDestination.Library.route) {
                LibraryScreen(
                    contentPadding = innerPadding,
                    viewModel = libraryViewModel,
                    onBookSelected = { bookId, filePath ->
                        selectedBookId = bookId
                        selectedBookFilePath = filePath
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
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NextPageDestination.Highlights.route) {
                HighlightsScreen(contentPadding = innerPadding)
            }
        }
    }
}
