package com.nextpage.ui.components.molecules

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.presentation.theme.NextPageColors
import com.nextpage.presentation.theme.NextPageDimens

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

/**
 * Bottom navigation bar with icon+label tabs. Equal-width tabs spread
 * across the available width. The active tab is tinted
 * `NextPageColors.navBarActive`; inactive tabs use
 * `NextPageColors.navBarInactive`. The bar auto-applies
 * `navigationBarsPadding` so it sits above the system nav bar.
 *
 * @param destinations Ordered list of tabs to render. Each item is a
 *   [BottomNavItem] with `route`, `labelRes`, and `icon`.
 * @param currentRoute Currently active route, or `null` if no tab is
 *   active. Matched against each `dest.route` for the active state.
 * @param onTabSelected Invoked with the selected tab's `route` when
 *   the user taps any tab.
 * @param modifier Modifier applied to the outer `Surface`.
 *
 * **Visual**: 64dp tall, `SpaceEvenly` layout, 20dp horizontal
 *   padding, `NextPageColors.surface` background. Each tab shows an
 *   icon (size `NextPageDimens.iconNavBar`) + 4dp gap + a
 *   12sp medium-weight label.
 * **Behavior**: tap a tab → [onTabSelected] with its `route`. No
 *   internal state. Designed to be wired with the navigation graph's
 *   `currentBackStackEntryAsState` for the active route.
 * **Recomposition**: recomposes when `destinations`, `currentRoute`,
 *   or `onTabSelected` change.
 */
@Composable
fun NextPageBottomNavBar(
    destinations: List<BottomNavItem>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = NextPageColors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val isSelected = currentRoute == dest.route
                val iconTint = if (isSelected) NextPageColors.navBarActive else NextPageColors.navBarInactive

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(dest.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = stringResource(id = dest.labelRes),
                        tint = iconTint,
                        modifier = Modifier.size(NextPageDimens.iconNavBar)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(id = dest.labelRes),
                        fontSize = 12.sp,
                        color = iconTint,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
