package com.nextpage.presentation.navigation.feature

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.di.AppContainer
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.ThemeMode
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.SettingsScreen
import com.nextpage.presentation.viewmodel.AuthViewModel
import com.nextpage.presentation.viewmodel.StatisticsViewModel
import com.nextpage.debug.LogViewerScreen

/**
 * Feature NavGraph for Settings + LogViewer.
 *
 * Threads currentSession, settingsInitialRoute, appThemeMode verbatim.
 * Builders receive host VMs, never call viewModel() inside composable.
 * Preserves all route strings, popUpTo inclusive, and theme callbacks.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    appContainer: AppContainer,
    authViewModel: AuthViewModel,
    statisticsViewModel: StatisticsViewModel,
    dictionaryRepository: DictionaryRepository,
    driveAuthHelper: GoogleDriveAuthHelper,
    currentSession: AuthSession?,
    settingsInitialRoute: String?,
    onSettingsInitialRouteConsumed: () -> Unit,
    appThemeMode: ThemeMode,
    onAppThemeModeChanged: (ThemeMode) -> Unit,
    contentPadding: PaddingValues
) {
    composable(
        route = NextPageDestination.Settings.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        SettingsScreen(
            contentPadding = contentPadding,
            authSession = currentSession,
            initialRoute = settingsInitialRoute,
            onInitialRouteConsumed = onSettingsInitialRouteConsumed,
            appThemeMode = appThemeMode,
            onAppThemeModeChanged = onAppThemeModeChanged,
            onLogout = {
                authViewModel.signOut()
                navController.navigate(NextPageDestination.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            },
            customHighlightColors = appContainer.readerPreferences.load().customHighlightColors,
            onUpdateCustomHighlightColor = { index, hex ->
                val prefs = appContainer.readerPreferences
                val current = prefs.load()
                val colors = current.customHighlightColors?.toMutableList()
                    ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                if (index in colors.indices) colors[index] = hex
                prefs.save(current.copy(customHighlightColors = colors))
            },
            onAddCustomHighlightColor = {
                val prefs = appContainer.readerPreferences
                val current = prefs.load()
                val colors = current.customHighlightColors?.toMutableList()
                    ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                if (colors.size < 5) {
                    colors.add(com.nextpage.domain.model.HighlightColor.YELLOW.hex)
                    prefs.save(current.copy(customHighlightColors = colors))
                }
            },
            onDeleteCustomHighlightColor = { index ->
                val prefs = appContainer.readerPreferences
                val current = prefs.load()
                val colors = current.customHighlightColors?.toMutableList()
                    ?: com.nextpage.domain.model.HighlightColor.defaultHexList().toMutableList()
                if (colors.size > 3 && index in colors.indices) {
                    colors.removeAt(index)
                    prefs.save(current.copy(customHighlightColors = colors))
                }
            },
            onResetCustomHighlightColors = {
                val prefs = appContainer.readerPreferences
                val current = prefs.load()
                prefs.save(current.copy(customHighlightColors = null))
            },
            onNavigateToLogViewer = {
                navController.navigate(NextPageDestination.LogViewer.route)
            },
            statisticsViewModel = statisticsViewModel,
            dictionaryRepository = dictionaryRepository,
            driveAuthHelper = driveAuthHelper,
            readingGoalPreferences = appContainer.readingGoalPreferences
        )
    }

    composable(route = NextPageDestination.LogViewer.route) {
        LogViewerScreen(
            onBack = { navController.popBackStack() }
        )
    }
}
