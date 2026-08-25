package com.nextpage.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextpage.R
import com.nextpage.ui.icons.NextPageIcons

sealed class NextPageDestination(
    val route: String,
    @param:StringRes val labelRes: Int = -1,
    val icon: ImageVector? = null
) {
    data object Auth : NextPageDestination("auth", R.string.tab_auth, NextPageIcons.Person)
    data object AuthRegister : NextPageDestination("auth/register")
    data object AuthForgot : NextPageDestination("auth/forgot")
    data object OnboardingGoal : NextPageDestination("onboarding/goal")
    data object Home : NextPageDestination("home", R.string.nav_home, NextPageIcons.Home)
    data object Library : NextPageDestination("library", R.string.nav_library, NextPageIcons.Library)
    data object Reader : NextPageDestination("reader", R.string.tab_reader, NextPageIcons.BookOpen)
    data object Highlights : NextPageDestination("highlights", R.string.nav_highlights, NextPageIcons.Highlights)
    data object Settings : NextPageDestination("settings", R.string.nav_settings, NextPageIcons.Settings)
    data object Statistics : NextPageDestination("statistics", R.string.nav_statistics, NextPageIcons.Statistics)
    data object BookDetail : NextPageDestination("book_detail/{bookId}", R.string.nav_book_detail, NextPageIcons.Book)
    data object BookEdit : NextPageDestination("book_edit/{bookId}")

    // Settings nested destinations
    data object SettingsList : NextPageDestination("settings/list")
    data object SettingsAccount : NextPageDestination("settings/account")
    data object SettingsDataStorage : NextPageDestination("settings/data")
    data object SettingsStorage : NextPageDestination("settings/storage", R.string.settings_storage_title, NextPageIcons.Storage)
    data object SettingsSync : NextPageDestination("settings/sync", R.string.settings_sync_title, NextPageIcons.CloudSync)
    data object SettingsNotifications : NextPageDestination("settings/notifications")
    data object SettingsTheme : NextPageDestination("settings/theme")
    data object SettingsLanguage : NextPageDestination("settings/language")
    data object SettingsPalette : NextPageDestination("settings/palette")
    data object SettingsAbout : NextPageDestination("settings/about")
    data object SettingsStatistics : NextPageDestination("settings/data/statistics")
    data object SettingsDictionary : NextPageDestination("settings/dictionary", R.string.settings_dictionary_label, NextPageIcons.LibraryBooks)
    data object Dictionary : NextPageDestination("settings/dictionary", R.string.settings_dictionary_label, NextPageIcons.LibraryBooks)
    data object SettingsDevices : NextPageDestination("settings/devices")
    data object SettingsDailyGoal : NextPageDestination("settings/daily-goal")
    data object SettingsPerformance : NextPageDestination("settings/performance")
    data object LogViewer : NextPageDestination("settings/log-viewer")
}
