package com.nextpage.presentation.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nextpage.presentation.screen.HighlightsScreen
import com.nextpage.presentation.screen.LibraryScreen
import com.nextpage.presentation.screen.ReaderScreen

@Composable
fun NextPageNavHost() {
    val navController = rememberNavController()
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
                LibraryScreen(contentPadding = innerPadding)
            }
            composable(NextPageDestination.Reader.route) {
                ReaderScreen(contentPadding = innerPadding)
            }
            composable(NextPageDestination.Highlights.route) {
                HighlightsScreen(contentPadding = innerPadding)
            }
        }
    }
}
