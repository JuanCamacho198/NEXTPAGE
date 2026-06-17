package com.nextpage.ui.components.molecules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nextpage.R
import com.nextpage.ui.components.molecules.FilterTab

@Composable
fun StatusChipRow(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        FilterTab("all", R.string.library_tab_all),
        FilterTab("reading", R.string.library_tab_reading),
        FilterTab("pending", R.string.library_tab_pending),
        FilterTab("completed", R.string.library_tab_completed)
    )

    NextPageFilterTabs(
        tabs = tabs,
        selectedTabId = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}
