package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nextpage.di.AppContainer
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.BookDetailScreen
import com.nextpage.presentation.screen.EditBookMetadataScreen
import com.nextpage.presentation.viewmodel.ReaderViewModel

/**
 * Feature NavGraph for book detail + edit.
 *
 * Holds navArgument bookId and delegates selection writes via [onSelectBook] lambda
 * (host-owned rememberSaveable state). No viewModel() inside composable.
 * Preserves slide transitions verbatim.
 */
fun NavGraphBuilder.bookDetailGraph(
    navController: NavController,
    appContainer: AppContainer,
    readerViewModel: ReaderViewModel,
    contentPadding: PaddingValues,
    onSelectBook: (String, String?, String) -> Unit
) {
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
            contentPadding = contentPadding,
            bookId = bookId,
            libraryRepository = appContainer.libraryRepository,
            onNavigateBack = { navController.popBackStack() },
            onEditBook = { navController.navigate("book_edit/$bookId") },
            onContinueReading = { id, filePath, format ->
                onSelectBook(id, filePath, format)
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
            contentPadding = contentPadding,
            bookId = bookId,
            libraryRepository = appContainer.libraryRepository,
            coverStorage = appContainer.coverStorage,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
