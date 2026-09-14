package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.viewmodel.ReaderViewModel

/**
 * Feature NavGraph for Reader.
 *
 * Holds no VM creation; receives host [readerViewModel] + selectedBook* values
 * as a backup. The book identity travels as navigation arguments on the
 * Reader destination (see [NextPageDestination.Reader.routeFor]); args win
 * over the host snapshot, which previously arrived blank because the NavHost
 * composition captured [selectedBookId] before the selection write landed.
 * Preserves slide transitions verbatim.
 */
fun NavGraphBuilder.readerGraph(
    navController: NavController,
    readerViewModel: ReaderViewModel,
    selectedBookId: String,
    selectedBookFilePath: String?,
    selectedBookFormat: String,
    contentPadding: PaddingValues,
) {
    composable(
        route = NextPageDestination.Reader.route,
        arguments =
            listOf(
                navArgument(NextPageDestination.Reader.ARG_BOOK_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(NextPageDestination.Reader.ARG_BOOK_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(NextPageDestination.Reader.ARG_BOOK_FORMAT) {
                    type = NavType.StringType
                    defaultValue = "epub"
                },
            ),
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { it } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition = { slideOutHorizontally { -it } + fadeOut() },
    ) { backStackEntry ->
        val argBookId = backStackEntry.arguments?.getString(NextPageDestination.Reader.ARG_BOOK_ID).orEmpty()
        val argBookPath = backStackEntry.arguments?.getString(NextPageDestination.Reader.ARG_BOOK_PATH)
        val argBookFormat = backStackEntry.arguments?.getString(NextPageDestination.Reader.ARG_BOOK_FORMAT) ?: "epub"
        // Args are fresh per destination entry (process-death safe); the host
        // snapshot is kept only as a backup for bare "reader" navigations.
        val hasArgs = argBookId.isNotBlank()
        val effectiveBookId = if (hasArgs) argBookId else selectedBookId
        val effectiveBookPath =
            if (hasArgs) {
                argBookPath?.takeIf { it.isNotBlank() }
            } else {
                selectedBookFilePath
            }
        val effectiveBookFormat = if (hasArgs) argBookFormat else selectedBookFormat
        ReaderScreen(
            contentPadding = contentPadding,
            selectedBookId = effectiveBookId,
            bookFilePath = effectiveBookPath,
            bookFormat = effectiveBookFormat,
            bookIdentitySource = if (hasArgs) "args" else "snapshot",
            viewModel = readerViewModel,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
