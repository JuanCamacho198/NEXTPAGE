package com.nextpage.presentation.feature.editmetadata

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.EditBookMetadataUiState
import com.nextpage.presentation.viewmodel.EditBookMetadataViewModel
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.icons.NextPageIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookMetadataScreen(
    contentPadding: PaddingValues,
    bookId: String,
    libraryRepository: LibraryRepository,
    coverStorage: CoverStorage,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EditBookMetadataViewModel = viewModel(
        factory = EditBookMetadataViewModel.Factory(
            bookId = bookId,
            libraryRepository = libraryRepository,
            coverStorage = coverStorage,
            appContext = context.applicationContext,
            onSaved = onNavigateBack
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            if (event is UiEvent.ShowSnackbar) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) viewModel.onCoverSelected(uri, bytes)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_metadata_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    NextPageButton(
                        onClick = onNavigateBack,
                        variant = NextPageButtonVariant.ICON
                    ) {
                        Icon(
                            imageVector = NextPageIcons.ArrowBack,
                            contentDescription = stringResource(R.string.reader_cancel)
                        )
                    }
                },
                actions = {
                    NextPageButton(
                        onClick = viewModel::save,
                        enabled = state.book != null && !state.isSaving,
                        variant = NextPageButtonVariant.ICON
                    ) {
                        Icon(
                            imageVector = NextPageIcons.Check,
                            contentDescription = stringResource(R.string.edit_metadata_save_changes)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                NextPageButton(
                    onClick = viewModel::save,
                    enabled = state.book != null && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.edit_metadata_save_changes))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.book == null -> {
                    Text(
                        text = stringResource(R.string.book_detail_error_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    EditMetadataContent(
                        state = state,
                        onChangeCover = { coverPickerLauncher.launch("image/*") },
                        onTitleChange = { viewModel.onTitleChange(it.take(MAX_SHORT_FIELD)) },
                        onAuthorChange = { viewModel.onAuthorChange(it.take(MAX_SHORT_FIELD)) },
                        onDescriptionChange = { viewModel.onDescriptionChange(it.take(MAX_SYNOPSIS)) },
                        onPublisherChange = { viewModel.onPublisherChange(it.take(MAX_SHORT_FIELD)) },
                        onLanguageChange = viewModel::onLanguageChange,
                        onPublishedDateChange = viewModel::onPublishedDateChange,
                        onGenreAdd = viewModel::onGenreAdd,
                        onGenreRemove = viewModel::onGenreRemove,
                        onTagAdd = viewModel::onTagAdd,
                        onTagRemove = viewModel::onTagRemove
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        EditMetadataContent(
            state = EditBookMetadataUiState(
                book = previewBook,
                isLoading = false,
                title = previewBook.title,
                author = previewBook.author.orEmpty(),
                description = previewBook.description.orEmpty(),
                genres = listOf("Fantasy", "Classics"),
                tags = listOf("favorites", "adventure"),
                language = previewBook.language,
                publisher = previewBook.publisher.orEmpty(),
                publishedDate = previewBook.publishedDate
            ),
            onChangeCover = {},
            onTitleChange = {},
            onAuthorChange = {},
            onDescriptionChange = {},
            onPublisherChange = {},
            onLanguageChange = {},
            onPublishedDateChange = {},
            onGenreAdd = {},
            onGenreRemove = {},
            onTagAdd = {},
            onTagRemove = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditBookMetadataScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        EditMetadataContent(
            state = EditBookMetadataUiState(
                book = previewBook,
                isLoading = false,
                title = previewBook.title,
                author = previewBook.author.orEmpty(),
                description = previewBook.description.orEmpty(),
                genres = listOf("Fantasy"),
                tags = listOf("favorites"),
                language = previewBook.language,
                publisher = previewBook.publisher.orEmpty(),
                publishedDate = previewBook.publishedDate
            ),
            onChangeCover = {},
            onTitleChange = {},
            onAuthorChange = {},
            onDescriptionChange = {},
            onPublisherChange = {},
            onLanguageChange = {},
            onPublishedDateChange = {},
            onGenreAdd = {},
            onGenreRemove = {},
            onTagAdd = {},
            onTagRemove = {}
        )
    }
}
