package com.nextpage.presentation.screen

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.ThemeMode
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.presentation.navigation.NextPageDestination
import com.nextpage.presentation.screen.settings.SettingsAboutScreen
import com.nextpage.presentation.screen.settings.SettingsAccountScreen
import com.nextpage.presentation.screen.settings.SettingsDataStorageScreen
import com.nextpage.presentation.screen.settings.SettingsDevicesScreen
import com.nextpage.presentation.screen.settings.SettingsLanguageScreen
import com.nextpage.presentation.screen.settings.SettingsListScreen
import com.nextpage.presentation.screen.settings.SettingsNotificationsScreen
import com.nextpage.presentation.screen.settings.SettingsPaletteScreen
import com.nextpage.presentation.screen.settings.SettingsStatisticsScreen
import com.nextpage.presentation.screen.settings.SettingsThemeScreen
import com.nextpage.presentation.viewmodel.DictionaryViewModel
import com.nextpage.presentation.viewmodel.SettingsDevicesViewModel
import com.nextpage.presentation.viewmodel.StatisticsViewModel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    authSession: AuthSession?,
    appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onAppThemeModeChanged: (ThemeMode) -> Unit = {},
    onLogout: () -> Unit = {},
    customHighlightColors: List<String>? = null,
    onUpdateCustomHighlightColor: (Int, String) -> Unit = { _, _ -> },
    onAddCustomHighlightColor: () -> Unit = {},
    onDeleteCustomHighlightColor: (Int) -> Unit = {},
    onResetCustomHighlightColors: () -> Unit = {},
    statisticsViewModel: StatisticsViewModel,
    dictionaryRepository: DictionaryRepository? = null
) {
    val nestedNavController = rememberNavController()
    val dictionaryViewModel = remember(dictionaryRepository) {
        dictionaryRepository?.let { DictionaryViewModel(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        NavHost(
            navController = nestedNavController,
            startDestination = NextPageDestination.SettingsList.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(route = NextPageDestination.SettingsList.route) {
                SettingsListScreen(
                    authSession = authSession,
                    appThemeMode = appThemeMode,
                    onNavigateToAccount = {
                        nestedNavController.navigate(NextPageDestination.SettingsAccount.route)
                    },
                    onNavigateToTheme = {
                        nestedNavController.navigate(NextPageDestination.SettingsTheme.route)
                    },
                    onNavigateToLanguage = {
                        nestedNavController.navigate(NextPageDestination.SettingsLanguage.route)
                    },
                    onNavigateToPalette = {
                        nestedNavController.navigate(NextPageDestination.SettingsPalette.route)
                    },
                    onNavigateToDataStorage = {
                        nestedNavController.navigate(NextPageDestination.SettingsDataStorage.route)
                    },
                    onNavigateToNotifications = {
                        nestedNavController.navigate(NextPageDestination.SettingsNotifications.route)
                    },
                    onNavigateToAbout = {
                        nestedNavController.navigate(NextPageDestination.SettingsAbout.route)
                    },
                    onNavigateToDictionary = {
                        nestedNavController.navigate(NextPageDestination.SettingsDictionary.route)
                    },
                    onNavigateToDevices = {
                        nestedNavController.navigate(NextPageDestination.SettingsDevices.route)
                    }
                )
            }

            composable(route = NextPageDestination.SettingsAccount.route) {
                SettingsAccountScreen(
                    authSession = authSession,
                    onLogout = onLogout,
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsTheme.route) {
                SettingsThemeScreen(
                    appThemeMode = appThemeMode,
                    onAppThemeModeChanged = onAppThemeModeChanged,
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsLanguage.route) {
                SettingsLanguageScreen(
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsPalette.route) {
                SettingsPaletteScreen(
                    customHighlightColors = customHighlightColors,
                    onUpdateCustomHighlightColor = onUpdateCustomHighlightColor,
                    onAddCustomHighlightColor = onAddCustomHighlightColor,
                    onDeleteCustomHighlightColor = onDeleteCustomHighlightColor,
                    onResetCustomHighlightColors = onResetCustomHighlightColors,
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsDataStorage.route) {
                SettingsDataStorageScreen(
                    onNavigateToStatistics = {
                        nestedNavController.navigate(NextPageDestination.SettingsStatistics.route)
                    },
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsNotifications.route) {
                SettingsNotificationsScreen(
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsAbout.route) {
                SettingsAboutScreen(
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsStatistics.route) {
                SettingsStatisticsScreen(
                    viewModel = statisticsViewModel,
                    onBack = { nestedNavController.popBackStack() }
                )
            }

            composable(route = NextPageDestination.SettingsDictionary.route) {
                dictionaryViewModel?.let { vm ->
                    DictionaryScreen(
                        viewModel = vm,
                        onNavigateBack = { nestedNavController.popBackStack() }
                    )
                }
            }

            composable(route = NextPageDestination.SettingsDevices.route) {
                val context = LocalContext.current
                val viewModel = remember(authSession?.userId) {
                    authSession?.userId?.let { userId ->
                        SettingsDevicesViewModel(
                            application = context.applicationContext as Application,
                            userId = userId
                        )
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE) {
                            viewModel?.stopHeartbeat()
                        } else if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel?.loadDevices()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Carga inicial al montar la screen (el observer no se dispara retroactivamente)
                LaunchedEffect(viewModel) {
                    viewModel?.loadDevices()
                }

                if (viewModel != null) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    SettingsDevicesScreen(
                        uiState = uiState,
                        onRemove = { id -> viewModel.removeDevice(id) },
                        onBack = { nestedNavController.popBackStack() }
                    )
                }
            }
        }
    }
}
