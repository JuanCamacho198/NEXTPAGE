package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.navigation.rememberImportLauncher
import com.nextpage.presentation.feature.home.HomeScreen
import com.nextpage.presentation.viewmodel.HomeViewModel
import com.nextpage.presentation.viewmodel.LibraryViewModel

/**
 * Feature NavGraph for Home.
 *
 * Holds the hoisted [rememberImportLauncher] and delegates import via onImportBook.
 * Receives host VMs and write lambdas; never calls viewModel() inside composable.
 */
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onSelectBook: (String, String?, String) -> Unit,
    onSettingsInitialRoute: (String) -> Unit
) {
    composable(
        route = NextPageDestination.Home.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        val importLauncher = rememberImportLauncher(libraryViewModel)

        HomeScreen(
            contentPadding = contentPadding,
            viewModel = homeViewModel,
            onNavigateToLibrary = {
                navController.navigate(NextPageDestination.Library.route) { launchSingleTop = true }
            },
            onNavigateToHighlights = {
                navController.navigate(NextPageDestination.Highlights.route) { launchSingleTop = true }
            },
            onNavigateToSettings = {
                navController.navigate(NextPageDestination.Settings.route) { launchSingleTop = true }
            },
            onOpenAccount = {
                onSettingsInitialRoute(NextPageDestination.SettingsAccount.route)
                navController.navigate(NextPageDestination.Settings.route) { launchSingleTop = true }
            },
            onNavigateToStatistics = {
                navController.navigate(NextPageDestination.Statistics.route)
            },
            onBookSelected = { bookId, filePath, format ->
                onSelectBook(bookId, filePath, format)
                navController.navigate("book_detail/$bookId")
            },
            onContinueReading = { bookId, filePath, format ->
                onSelectBook(bookId, filePath, format)
                navController.navigate(NextPageDestination.Reader.route) {
                    popUpTo(NextPageDestination.Reader.route) { inclusive = true }
                }
            },
            onImportBook = {
                importLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
            }
        )
    }
}
