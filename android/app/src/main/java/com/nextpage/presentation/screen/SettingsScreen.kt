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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nextpage.data.remote.drive.GoogleDriveAuthHelper
import com.nextpage.data.session.ReadingGoalPreferences
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.HighlightColor
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
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.DictionaryViewModel
import com.nextpage.presentation.viewmodel.SettingsDevicesViewModel
import com.nextpage.presentation.viewmodel.StatisticsViewModel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    authSession: AuthSession?,
    appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialRoute: String? = null,
    onInitialRouteConsumed: () -> Unit = {},
    onAppThemeModeChanged: (ThemeMode) -> Unit = {},
    onLogout: () -> Unit = {},
    customHighlightColors: List<String>? = null,
    onUpdateCustomHighlightColor: (Int, String) -> Unit = { _, _ -> },
    onAddCustomHighlightColor: () -> Unit = {},
    onDeleteCustomHighlightColor: (Int) -> Unit = {},
    onResetCustomHighlightColors: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    statisticsViewModel: StatisticsViewModel,
    dictionaryRepository: DictionaryRepository? = null,
    driveAuthHelper: GoogleDriveAuthHelper? = null,
    readingGoalPreferences: ReadingGoalPreferences? = null
) {
    SettingsScreenContent(
        contentPadding = contentPadding,
        authSession = authSession,
        appThemeMode = appThemeMode,
        initialRoute = initialRoute,
        onInitialRouteConsumed = onInitialRouteConsumed,
        onAppThemeModeChanged = onAppThemeModeChanged,
        onLogout = onLogout,
        customHighlightColors = customHighlightColors,
        onUpdateCustomHighlightColor = onUpdateCustomHighlightColor,
        onAddCustomHighlightColor = onAddCustomHighlightColor,
        onDeleteCustomHighlightColor = onDeleteCustomHighlightColor,
        onResetCustomHighlightColors = onResetCustomHighlightColors,
        onNavigateToLogViewer = onNavigateToLogViewer,
        statisticsViewModel = statisticsViewModel,
        dictionaryRepository = dictionaryRepository,
        driveAuthHelper = driveAuthHelper,
        readingGoalPreferences = readingGoalPreferences
    )
}

@Composable
private fun SettingsScreenContent(
    contentPadding: PaddingValues,
    authSession: AuthSession?,
    appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialRoute: String? = null,
    onInitialRouteConsumed: () -> Unit = {},
    onAppThemeModeChanged: (ThemeMode) -> Unit = {},
    onLogout: () -> Unit = {},
    customHighlightColors: List<String>? = null,
    onUpdateCustomHighlightColor: (Int, String) -> Unit = { _, _ -> },
    onAddCustomHighlightColor: () -> Unit = {},
    onDeleteCustomHighlightColor: (Int) -> Unit = {},
    onResetCustomHighlightColors: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    statisticsViewModel: StatisticsViewModel? = null,
    dictionaryRepository: DictionaryRepository? = null,
    driveAuthHelper: GoogleDriveAuthHelper? = null,
    readingGoalPreferences: ReadingGoalPreferences? = null
) {
    val nestedNavController = rememberNavController()
    val dictionaryViewModel = remember(dictionaryRepository) {
        dictionaryRepository?.let { DictionaryViewModel(it) }
    }

    val start = initialRoute ?: NextPageDestination.SettingsList.route

    // One-shot: consume the deep-link after the NavHost first composes so a
    // later bottom-nav Settings tap uses the default start route instead.
    LaunchedEffect(Unit) {
        onInitialRouteConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        NavHost(
            navController = nestedNavController,
            startDestination = start,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(route = NextPageDestination.SettingsList.route) {
                SettingsListScreen(
                    authSession = authSession,
                    appThemeMode = appThemeMode,
                    readingGoalPreferences = readingGoalPreferences,
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
                    },
                    onNavigateToDailyGoal = {
                        nestedNavController.navigate(NextPageDestination.SettingsDailyGoal.route)
                    },
                    onNavigateToLogViewer = onNavigateToLogViewer
                )
            }

            composable(route = NextPageDestination.SettingsAccount.route) {
                SettingsAccountScreen(
                    authSession = authSession,
                    onLogout = onLogout,
                    // When Account is the start destination, popBackStack returns
                    // false (no List in the stack) — fall back to the Settings list
                    // so the back arrow never gets stuck.
                    onBack = {
                        if (!nestedNavController.popBackStack()) {
                            nestedNavController.navigate(NextPageDestination.SettingsList.route)
                        }
                    }
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
                    driveAuthHelper = driveAuthHelper ?: return@composable,
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
                statisticsViewModel?.let { vm ->
                    SettingsStatisticsScreen(
                        viewModel = vm,
                        onBack = { nestedNavController.popBackStack() }
                    )
                }
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

            composable(route = NextPageDestination.SettingsDailyGoal.route) {
                val prefs = readingGoalPreferences
                if (prefs != null) {
                    val current = prefs.load() ?: 30
                    // Reuse onboarding screen for editing; back button pops to Settings list.
                    OnboardingGoalScreen(
                        initialMinutes = current,
                        onSave = { minutes ->
                            prefs.save(minutes)
                            nestedNavController.popBackStack()
                        },
                        onNavigateBack = { nestedNavController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        SettingsScreenContent(
            contentPadding = PaddingValues(16.dp),
            authSession = AuthSession(
                userId = "local-1",
                email = "reader@nextpage.app",
                displayName = "Reader",
                provider = "email"
            ),
            appThemeMode = ThemeMode.SYSTEM,
            customHighlightColors = HighlightColor.defaultHexList(),
            onAppThemeModeChanged = {},
            onLogout = {},
            onUpdateCustomHighlightColor = { _, _ -> },
            onAddCustomHighlightColor = {},
            onDeleteCustomHighlightColor = {},
            onResetCustomHighlightColors = {},
            onNavigateToLogViewer = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        SettingsScreenContent(
            contentPadding = PaddingValues(16.dp),
            authSession = AuthSession(
                userId = "local-1",
                email = "reader@nextpage.app",
                displayName = "Reader",
                provider = "email"
            ),
            appThemeMode = ThemeMode.SYSTEM,
            customHighlightColors = HighlightColor.defaultHexList(),
            onAppThemeModeChanged = {},
            onLogout = {},
            onUpdateCustomHighlightColor = { _, _ -> },
            onAddCustomHighlightColor = {},
            onDeleteCustomHighlightColor = {},
            onResetCustomHighlightColors = {},
            onNavigateToLogViewer = {}
        )
    }
}
