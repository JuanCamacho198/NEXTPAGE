package com.nextpage.ui.components.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R

@Composable
fun StatusChipRow(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            "all" to R.string.library_tab_all,
            "reading" to R.string.library_tab_reading,
            "pending" to R.string.library_tab_pending,
            "completed" to R.string.library_tab_completed
        )
        tabs.forEach { (tabId, labelRes) ->
            val isSelected = selectedTab == tabId
            AssistChip(
                onClick = { onTabSelected(tabId) },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
    }
}
