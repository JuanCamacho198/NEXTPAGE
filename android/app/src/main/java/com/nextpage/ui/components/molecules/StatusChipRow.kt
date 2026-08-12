package com.nextpage.ui.components.molecules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nextpage.R
import com.nextpage.ui.components.molecules.FilterTab
import com.nextpage.presentation.theme.NextPageTheme

/**
 * Pre-configured [NextPageFilterTabs] instance with the four library
 * status tabs: All, Reading, Pending, Completed. A thin convenience
 * wrapper — no internal state, all of it is hoisted.
 *
 * @param selectedTab Id of the active tab. Must be one of `"all"`,
 *   `"reading"`, `"pending"`, `"completed"`. Unknown ids are passed
 *   through to [NextPageFilterTabs] (no tab will appear selected).
 * @param onTabSelected Invoked with the new tab id when the user
 *   taps a tab.
 * @param modifier Modifier applied to the underlying
 *   [NextPageFilterTabs].
 *
 * **Visual**: identical to [NextPageFilterTabs] — pill-shaped tabs in
 * a horizontally scrolling row, with the active tab using
 * `colorScheme.primary` and the inactive tabs using
 * `colorScheme.surfaceVariant`.
 * **Behavior**: no overflow indicator is shown (`showOverflowIndicator`
 *   defaults to `true` in [NextPageFilterTabs] but with only 4 tabs
 *   there is no overflow on typical screens).
 * **Recomposition**: recomposes when `selectedTab` or `onTabSelected`
 * changes.
 */
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

@Preview(showBackground = true)
@Composable
private fun StatusChipRowDarkPreview() {
    NextPageTheme(darkTheme = true) {
        StatusChipRow(
            selectedTab = "reading",
            onTabSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusChipRowLightPreview() {
    NextPageTheme(darkTheme = false) {
        StatusChipRow(
            selectedTab = "reading",
            onTabSelected = {}
        )
    }
}
