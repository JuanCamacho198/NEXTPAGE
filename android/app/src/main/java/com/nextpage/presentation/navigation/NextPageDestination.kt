package com.nextpage.presentation.navigation

import androidx.annotation.StringRes
import com.nextpage.R

sealed class NextPageDestination(
    val route: String,
    @StringRes val labelRes: Int
) {
    data object Library : NextPageDestination("library", R.string.tab_library)
    data object Reader : NextPageDestination("reader", R.string.tab_reader)
    data object Highlights : NextPageDestination("highlights", R.string.tab_highlights)
}
