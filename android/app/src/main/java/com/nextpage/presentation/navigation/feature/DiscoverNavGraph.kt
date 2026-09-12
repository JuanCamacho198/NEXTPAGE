package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nextpage.data.remote.catalog.CatalogFeaturedSort
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.presentation.feature.discover.DiscoverRailState
import com.nextpage.presentation.feature.discover.DiscoverScreen
import com.nextpage.presentation.feature.discover.DiscoverSectionScreen
import com.nextpage.presentation.feature.discover.DiscoverSectionViewModel
import com.nextpage.presentation.feature.discover.DiscoverSectionViewModelFactory
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.viewmodel.DiscoverViewModel
import java.net.URLEncoder

/**
 * Feature NavGraph for Discover.
 *
 * Discover is the middle tab of the 5-tab bottom navigation
 * (`bottomNavDestinations`), so the shell is reachable directly from the bar.
 *
 * Two routes live here: the search shell and the "Ver todo" section list. The
 * section route carries the already-localized section title plus exactly one
 * selector (`sort` for a featured rail, `sourceId` for a per-source list), so no
 * resource-id round trip and no ambiguity about what is being paged.
 */
fun NavGraphBuilder.discoverGraph(
    navController: NavController,
    contentPadding: PaddingValues,
    discoverViewModel: DiscoverViewModel,
    catalogProvider: CatalogProvider,
    discoverUserInitial: String? = null,
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
            viewModel = discoverViewModel,
            userInitial = discoverUserInitial,
            onOpenSection = { rail: DiscoverRailState.Loaded, sectionTitle: String ->
                navController.navigate(discoverSectionRoute(sectionTitle, rail.sort, rail.sourceId))
            },
            onNavigateToSettingsAddons = {
                navController.navigate(NextPageDestination.SettingsAddons.route)
            }
        )
    }

    composable(
        route = NextPageDestination.DiscoverSection.route,
        arguments = listOf(
            navArgument("sectionTitle") { type = NavType.StringType; defaultValue = "" },
            navArgument("sort") { type = NavType.StringType; defaultValue = "" },
            navArgument("sourceId") { type = NavType.StringType; defaultValue = "" },
        ),
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) { entry ->
        val args = entry.arguments
        val sectionTitle = args?.getString("sectionTitle").orEmpty()
        val sourceId = args?.getString("sourceId").orEmpty().ifBlank { null }
        val sortRaw = args?.getString("sort").orEmpty()
        val sort = CatalogFeaturedSort.entries.firstOrNull { it.name == sortRaw }

        val sectionViewModel: DiscoverSectionViewModel = viewModel(
            factory = DiscoverSectionViewModelFactory(
                catalogProvider = catalogProvider,
                sectionTitle = sectionTitle,
                sort = sort,
                sourceId = sourceId
            )
        )
        DiscoverSectionScreen(
            contentPadding = contentPadding,
            viewModel = sectionViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

/**
 * Builds the section route from the destination pattern so the query-argument
 * names cannot drift from [NextPageDestination.DiscoverSection]. The title is
 * percent-encoded (`URLEncoder` emits `+` for spaces, which navigation would not
 * decode, so those are rewritten to `%20`); it is already-localized copy.
 */
internal fun discoverSectionRoute(
    sectionTitle: String,
    sort: CatalogFeaturedSort?,
    sourceId: String?
): String = "discover/section" +
    "?sectionTitle=${encodeQueryValue(sectionTitle)}" +
    "&sort=${encodeQueryValue(sort?.name.orEmpty())}" +
    "&sourceId=${encodeQueryValue(sourceId.orEmpty())}"

internal fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
