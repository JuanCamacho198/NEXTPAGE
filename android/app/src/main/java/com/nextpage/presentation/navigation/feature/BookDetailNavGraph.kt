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
import com.nextpage.di.AppContainer
import com.nextpage.presentation.feature.bookdetail.BookDetailScreen
import com.nextpage.presentation.feature.editmetadata.EditBookMetadataScreen
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.navigation.ReaderRoute
import com.nextpage.presentation.navigation.rememberBookDetailViewModel
import com.nextpage.presentation.navigation.rememberEditBookMetadataViewModel
import com.nextpage.presentation.viewmodel.ReaderViewModel

/**
 * Feature NavGraph for book detail + edit.
 *
 * Holds navArgument bookId and delegates selection writes via [onSelectBook] lambda
 * (host-owned rememberSaveable state). Resolves its route-scoped ViewModels
 * through the designated provider (ViewModelProviders.kt) and passes them in —
 * no viewModel() inside composable.
 * Preserves slide transitions verbatim.
 */
fun NavGraphBuilder.bookDetailGraph(
    navController: NavController,
    appContainer: AppContainer,
    readerViewModel: ReaderViewModel,
    contentPadding: PaddingValues,
    onSelectBook: (String, String?, String) -> Unit,
) {
    composable(
        route = NextPageDestination.BookDetail.route,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { it } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition = { slideOutHorizontally { -it } + fadeOut() },
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
        val bookDetailViewModel =
            rememberBookDetailViewModel(
                bookId = bookId,
                libraryRepository = appContainer.libraryRepository,
            )
        BookDetailScreen(
            contentPadding = contentPadding,
            viewModel = bookDetailViewModel,
            onNavigateBack = { navController.popBackStack() },
            onEditBook = { navController.navigate("book_edit/$bookId") },
            onContinueReading = { id, filePath, format ->
                onSelectBook(id, filePath, format)
                navController.navigate(ReaderRoute(id, filePath, format)) {
                    popUpTo<ReaderRoute> { inclusive = true }
                }
            },
        )
    }

    composable(
        route = NextPageDestination.BookEdit.route,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { it } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition = { slideOutHorizontally { -it } + fadeOut() },
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
        val editBookMetadataViewModel =
            rememberEditBookMetadataViewModel(
                bookId = bookId,
                libraryRepository = appContainer.libraryRepository,
                coverStorage = appContainer.coverStorage,
                onSaved = { navController.popBackStack() },
            )
        EditBookMetadataScreen(
            contentPadding = contentPadding,
            viewModel = editBookMetadataViewModel,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
