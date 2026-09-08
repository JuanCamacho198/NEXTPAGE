package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.presentation.feature.discover.DiscoverScreen
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.viewmodel.DiscoverViewModel

/**
 * Feature NavGraph for Discover (PR1 skeleton).
 *
 * v1 keeps Discover out of `bottomNavDestinations`; entry arrives via
 * Home/Library actions (PR3) with `launchSingleTop` so pop returns to origin.
 * The host-owned DiscoverViewModel wiring arrives with PR2 state.
 */
fun NavGraphBuilder.discoverGraph(
    navController: NavController,
    contentPadding: PaddingValues,
    discoverViewModel: DiscoverViewModel,
) {
    composable(
        route = NextPageDestination.Discover.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) {
        DiscoverScreen(
            contentPadding = contentPadding,
        viewModel = discoverViewModel
        )
    }
}
