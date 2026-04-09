package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.ReaderViewModel

@Composable
fun ReaderScreen(
    contentPadding: PaddingValues,
    selectedBookId: String,
    viewModel: ReaderViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(NextPageDimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm),
        horizontalAlignment = Alignment.Start
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        }

        Text(
            text = stringResource(R.string.reader_selected_book, selectedBookId),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = stringResource(R.string.reader_placeholder),
            style = MaterialTheme.typography.bodyMedium
        )

        if (uiState.readingProgress == null) {
            Text(
                text = stringResource(R.string.reader_no_progress),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = stringResource(
                    R.string.reader_progress_cfi,
                    uiState.readingProgress?.cfiLocation.orEmpty()
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.reader_progress_percent,
                    uiState.readingProgress?.percentage ?: 0f
                ),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
