package com.nextpage.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextpage.R

sealed class NextPageDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Auth : NextPageDestination("auth", R.string.tab_auth, Icons.Outlined.Home)
    data object Home : NextPageDestination("home", R.string.nav_home, Icons.Outlined.Home)
    data object Library : NextPageDestination("library", R.string.nav_library, Icons.AutoMirrored.Outlined.LibraryBooks)
    data object Reader : NextPageDestination("reader", R.string.tab_reader, Icons.Outlined.Home)
    data object Highlights : NextPageDestination("highlights", R.string.nav_highlights, Icons.Outlined.Bookmark)
    data object Settings : NextPageDestination("settings", R.string.nav_settings, Icons.Outlined.Settings)
}
