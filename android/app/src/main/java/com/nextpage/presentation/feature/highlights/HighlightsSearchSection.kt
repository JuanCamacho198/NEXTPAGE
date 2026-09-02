package com.nextpage.presentation.feature.highlights

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.ui.components.atoms.NextPageTextField
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun HighlightsSearchSection(
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    if (showSearch) {
        NextPageTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = stringResource(R.string.highlights_search),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = NextPageIcons.Close,
            trailingIconContentDescription = stringResource(R.string.reader_settings_close)
        )
    }
}
