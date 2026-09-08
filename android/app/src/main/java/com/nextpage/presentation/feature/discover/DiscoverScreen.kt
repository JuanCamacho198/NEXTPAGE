package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nextpage.R

/**
 * PR1 route/nav skeleton. Search grid, covers, and detail arrive in PR2/PR3;
 * this stub only proves the `discover` destination renders inside the NavHost.
 */
@Composable
fun DiscoverScreen(
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.nav_discover),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
