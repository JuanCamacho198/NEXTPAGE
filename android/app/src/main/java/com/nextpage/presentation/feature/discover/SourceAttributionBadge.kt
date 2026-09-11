package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.data.remote.catalog.CatalogSources
import com.nextpage.presentation.theme.NextPageColors

/**
 * fs12 source attribution badge for catalog results.
 *
 * Only addon-sourced books render it: a book whose [provider] is not an
 * `addon:<addonId>` source renders nothing (built-in and curated providers are
 * first-party and need no attribution). The display name comes from
 * [attributionNames] (addonId → name). A cached row whose addon was disabled or
 * uninstalled mid-session is absent from that map and degrades to a generic
 * "Addon" label instead of crashing or showing a raw id.
 */
@Composable
fun SourceAttributionBadge(
    provider: String,
    attributionNames: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val addonId = CatalogSources.addonIdOf(provider) ?: return
    val label = attributionNames[addonId]
        ?: stringResource(R.string.discover_source_addon_fallback)

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
        fontWeight = FontWeight.Medium,
        color = NextPageColors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NextPageColors.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
