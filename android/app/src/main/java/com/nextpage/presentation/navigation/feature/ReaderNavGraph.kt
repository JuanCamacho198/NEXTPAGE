package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.ReaderScreen
import com.nextpage.presentation.viewmodel.ReaderViewModel

/**
 * Feature NavGraph for Reader.
 *
 * Holds no VM creation; receives host [readerViewModel] + selectedBook* values.
 * Preserves slide transitions verbatim.
 */
fun NavGraphBuilder.readerGraph(
    navController: NavController,
    readerViewModel: ReaderViewModel,
    selectedBookId: String,
    selectedBookFilePath: String?,
    selectedBookFormat: String,
    contentPadding: PaddingValues
) {
    composable(
        route = NextPageDestination.Reader.route,
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { it } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition = { slideOutHorizontally { -it } + fadeOut() }
    ) {
        ReaderScreen(
            contentPadding = contentPadding,
            selectedBookId = selectedBookId,
            bookFilePath = selectedBookFilePath,
            bookFormat = selectedBookFormat,
            viewModel = readerViewModel,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
