package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nextpage.R
import androidx.compose.ui.res.stringResource
import com.nextpage.presentation.theme.NextPageColors
import com.nextpage.ui.icons.NextPageIcons

/**
 * Discover search field: 48dp surface field with a search icon that tints
 * `primary` while a fetch is in flight. While searching, the trailing slot
 * shows a 16dp spinner; otherwise a clear button appears when text is present.
 *
 * Implemented directly on Material 3 [OutlinedTextField] because the app's
 * [com.nextpage.ui.components.atoms.NextPageTextField] atom only accepts an
 * `ImageVector` trailing slot and a fixed icon tint, so it cannot host the
 * spinner or the in-flight `primary` leading tint required by the design.
 */
@Composable
fun DiscoverSearchField(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        singleLine = true,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = NextPageColors.textPrimary),
        placeholder = {
            Text(
                text = stringResource(R.string.discover_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = NextPageColors.textSecondary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = NextPageIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSearching) NextPageColors.primary else NextPageColors.textSecondary
            )
        },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NextPageColors.primary
                )
                query.isNotEmpty() -> IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = NextPageIcons.Close,
                        contentDescription = stringResource(R.string.discover_clear_content_desc),
                        modifier = Modifier.size(16.dp),
                        tint = NextPageColors.textSecondary
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = NextPageColors.surface,
            unfocusedContainerColor = NextPageColors.surface,
            focusedBorderColor = NextPageColors.primary,
            unfocusedBorderColor = NextPageColors.borderSubtle,
            cursorColor = NextPageColors.primary
        )
    )
}
