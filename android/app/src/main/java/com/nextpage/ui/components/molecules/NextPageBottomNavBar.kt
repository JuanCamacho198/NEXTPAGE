package com.nextpage.ui.components.molecules

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.presentation.theme.BgSurface
import com.nextpage.presentation.theme.NavBarActive
import com.nextpage.presentation.theme.NavBarInactive
import com.nextpage.presentation.theme.NavBarOverlay
import com.nextpage.presentation.theme.NextPageDimens

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
)

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
        color = BgSurface
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
                val iconTint = if (isSelected) NavBarActive else NavBarInactive

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(dest.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Active overlay background (only when selected)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(
                                if (isSelected) NavBarOverlay else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = dest.iconRes),
                            contentDescription = stringResource(id = dest.labelRes),
                            tint = iconTint,
                            modifier = Modifier
                                .height(NextPageDimens.iconNavBar)
                                .width(IntrinsicSize.Min)
                        )
                    }

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
