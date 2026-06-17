package com.nextpage.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nextpage.R

sealed class NextPageDestination(
    val route: String,
    @StringRes val labelRes: Int = -1,
    @DrawableRes val iconRes: Int = -1
) {
    data object Auth : NextPageDestination("auth", R.string.tab_auth, R.drawable.ic_nav_home)
    data object Home : NextPageDestination("home", R.string.nav_home, R.drawable.ic_nav_home)
    data object Library : NextPageDestination("library", R.string.nav_library, R.drawable.ic_nav_library)
    data object Reader : NextPageDestination("reader", R.string.tab_reader, R.drawable.ic_nav_home)
    data object Highlights : NextPageDestination("highlights", R.string.nav_highlights, R.drawable.ic_nav_highlights)
    data object Settings : NextPageDestination("settings", R.string.nav_settings, R.drawable.ic_nav_settings)
    data object BookDetail : NextPageDestination("book_detail/{bookId}", R.string.nav_book_detail, R.drawable.ic_nav_home)

    // Settings nested destinations
    data object SettingsList : NextPageDestination("settings/list")
    data object SettingsAccount : NextPageDestination("settings/account")
    data object SettingsDataStorage : NextPageDestination("settings/data")
    data object SettingsNotifications : NextPageDestination("settings/notifications")
    data object SettingsTheme : NextPageDestination("settings/theme")
    data object SettingsLanguage : NextPageDestination("settings/language")
    data object SettingsPalette : NextPageDestination("settings/palette")
    data object SettingsAbout : NextPageDestination("settings/about")
    data object SettingsStatistics : NextPageDestination("settings/data/statistics")
}
