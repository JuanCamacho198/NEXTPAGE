package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.domain.model.AuthSession
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.viewmodel.LibraryViewModel

/**
 * Feature NavGraph for Library (bookshelf).
 *
 * Receives host [libraryViewModel] + [currentSession] threading verbatim.
 * onSelectBook write lambda keeps selectedBook* in host rememberSaveable.
 */
fun NavGraphBuilder.libraryGraph(
    navController: NavController,
    libraryViewModel: LibraryViewModel,
    driveAuthHelper: GoogleDriveAuthHelper,
    currentSession: AuthSession?,
    contentPadding: PaddingValues,
    onSelectBook: (String, String?, String) -> Unit,
    onSettingsInitialRoute: (String) -> Unit
) {
    composable(
        route = NextPageDestination.Library.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        LibraryScreen(
            contentPadding = contentPadding,
            viewModel = libraryViewModel,
            driveAuthHelper = driveAuthHelper,
            authSession = currentSession,
            onOpenAccount = {
                onSettingsInitialRoute(NextPageDestination.SettingsAccount.route)
                navController.navigate(NextPageDestination.Settings.route) { launchSingleTop = true }
            },
            onBookSelected = { bookId, filePath, format ->
                onSelectBook(bookId, filePath, format)
                navController.navigate("book_detail/$bookId")
            },
            onEditBook = { bookId ->
                navController.navigate("book_edit/$bookId")
            }
        )
    }
}
