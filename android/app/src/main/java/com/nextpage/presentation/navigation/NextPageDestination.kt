package com.nextpage.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nextpage.R

sealed class NextPageDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    data object Auth : NextPageDestination("auth", R.string.tab_auth, R.drawable.ic_nav_home)
    data object Home : NextPageDestination("home", R.string.nav_home, R.drawable.ic_nav_home)
    data object Library : NextPageDestination("library", R.string.nav_library, R.drawable.ic_nav_library)
    data object Reader : NextPageDestination("reader", R.string.tab_reader, R.drawable.ic_nav_home)
    data object Highlights : NextPageDestination("highlights", R.string.nav_highlights, R.drawable.ic_nav_highlights)
    data object Statistics : NextPageDestination("statistics", R.string.nav_statistics, R.drawable.ic_nav_statistics)
    data object Settings : NextPageDestination("settings", R.string.nav_settings, R.drawable.ic_nav_settings)
    data object BookDetail : NextPageDestination("book_detail/{bookId}", R.string.nav_book_detail, R.drawable.ic_nav_home)
}
