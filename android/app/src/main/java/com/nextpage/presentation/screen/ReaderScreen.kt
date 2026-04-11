package com.nextpage.presentation.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.data.epub.EpubContentLoader
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    contentPadding: PaddingValues,
    selectedBookId: String,
    bookFilePath: String?,
    bookFormat: String = "epub",
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var showGoToPageDialog by remember { mutableStateOf(false) }
    var goToPageInput by remember { mutableStateOf("") }
    var goToPageError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(selectedBookId) {
        viewModel.onReaderOpened()
        onDispose {
            viewModel.onReaderPaused()
        }
    }

    LaunchedEffect(selectedBookId, bookFilePath, bookFormat) {
        if (!selectedBookId.isBlank() && bookFilePath != null) {
            viewModel.loadBook(selectedBookId, bookFilePath, bookFormat)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.reader_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        when {
                            uiState.totalPdfPages > 0 -> {
                                Text(
                                    text = "Page ${uiState.currentPdfPage + 1} of ${uiState.totalPdfPages}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            uiState.chapters.isNotEmpty() -> {
                                Text(
                                    text = "Chapter ${uiState.currentChapterIndex + 1} of ${uiState.chapters.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.totalPdfPages > 0) {
                        IconButton(onClick = { showGoToPageDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = stringResource(R.string.reader_go_to_page)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.createBookmarkFromCurrentPosition() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.reader_add_bookmark)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    val loadTimeMs = uiState.loadTimeMs
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (loadTimeMs != null) {
                            Spacer(modifier = Modifier.height(NextPageDimens.spacingMd))
                            Text(
                                text = stringResource(R.string.reader_loaded_in, loadTimeMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(NextPageDimens.spacingMd))
                        Button(onClick = {
                            bookFilePath?.let { viewModel.loadBook(selectedBookId, it, bookFormat) }
                        }) {
                            Text(text = stringResource(R.string.reader_retry))
                        }
                    }
                }

                uiState.totalPdfPages > 0 -> {
                    PdfReaderContent(
                        bitmap = uiState.pdfPageBitmap,
                        currentPage = uiState.currentPdfPage,
                        totalPages = uiState.totalPdfPages,
                        onTapZone = { isLeft -> viewModel.onTapZone(isLeft) },
                        onAddHighlight = { text, note ->
                            viewModel.createHighlight(
                                bookId = selectedBookId,
                                cfiRange = "pdfpage:${uiState.currentPdfPage}",
                                textContent = text,
                                note = note
                            )
                        }
                    )
                }

                uiState.chapters.isNotEmpty() -> {
                    ReaderContent(
                        chapterContent = uiState.chapterContent,
                        currentChapterIndex = uiState.currentChapterIndex,
                        totalChapters = uiState.chapters.size,
                        chapters = uiState.chapters,
                        onTapZone = { isLeft -> viewModel.onTapZone(isLeft) },
                        onChapterSelect = { index -> viewModel.goToChapter(index) },
                        onAddHighlight = { text, note ->
                            val cfiRange = "epubcfi(/6/${uiState.currentChapterIndex + 1})"
                            viewModel.createHighlight(
                                bookId = selectedBookId,
                                cfiRange = cfiRange,
                                textContent = text,
                                note = note
                            )
                        }
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.reader_no_content),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        if (showGoToPageDialog && uiState.totalPdfPages > 0) {
            AlertDialog(
                onDismissRequest = {
                    showGoToPageDialog = false
                    goToPageInput = ""
                    goToPageError = null
                },
                title = { Text(text = stringResource(R.string.reader_go_to_page)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = goToPageInput,
                            onValueChange = {
                                goToPageInput = it
                                goToPageError = null
                            },
                            label = { Text(text = stringResource(R.string.reader_go_to_page_input_label)) },
                            singleLine = true,
                            isError = goToPageError != null,
                            supportingText = {
                                val error = goToPageError
                                if (error != null) {
                                    Text(text = error)
                                } else {
                                    Text(
                                        text = stringResource(
                                            R.string.reader_go_to_page_input_hint,
                                            uiState.totalPdfPages
                                        )
                                    )
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val pageNumber = goToPageInput.toIntOrNull()
                            if (pageNumber == null || pageNumber !in 1..uiState.totalPdfPages) {
                                goToPageError =
                                    "Enter a value between 1 and ${uiState.totalPdfPages}"
                            } else {
                                viewModel.goToPdfPage(pageNumber - 1)
                                showGoToPageDialog = false
                                goToPageInput = ""
                                goToPageError = null
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.reader_go))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGoToPageDialog = false
                            goToPageInput = ""
                            goToPageError = null
                        }
                    ) {
                        Text(text = stringResource(R.string.reader_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ReaderContent(
    chapterContent: String,
    currentChapterIndex: Int,
    totalChapters: Int,
    chapters: List<EpubContentLoader.Chapter>,
    onTapZone: (Boolean) -> Unit,
    onChapterSelect: (Int) -> Unit,
    onAddHighlight: (String, String?) -> Unit
) {
    var showHighlightDialog by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = NextPageDimens.spacingLg,
                    vertical = NextPageDimens.spacingMd
                ),
                verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
            ) {
                item {
                    Text(
                        text = chapters.getOrNull(currentChapterIndex)?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = NextPageDimens.spacingMd)
                    )
                }

                item {
                    Text(
                        text = chapterContent.ifEmpty { stringResource(R.string.reader_chapter_loading) },
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(NextPageDimens.spacingXl))
                    ChapterNavigationBar(
                        currentChapter = currentChapterIndex,
                        totalChapters = totalChapters,
                        onPrevious = { if (currentChapterIndex > 0) onChapterSelect(currentChapterIndex - 1) },
                        onNext = { if (currentChapterIndex < totalChapters - 1) onChapterSelect(currentChapterIndex + 1) },
                        onChapterSelect = onChapterSelect,
                        chapters = chapters
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    onClick = {
                        onTapZone(false)
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 80.dp)
                .clickable(
                    onClick = {
                        onTapZone(true)
                    }
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(NextPageDimens.spacingLg)
        ) {
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    selectedText = chapterContent.take(200)
                    showHighlightDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.reader_add_highlight)
                )
            }
        }
    }

    if (showHighlightDialog) {
        HighlightDialog(
            selectedText = selectedText,
            onDismiss = { showHighlightDialog = false },
            onConfirm = { note ->
                onAddHighlight(selectedText, note)
                showHighlightDialog = false
            }
        )
    }
}

@Composable
private fun ChapterNavigationBar(
    currentChapter: Int,
    totalChapters: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapterSelect: (Int) -> Unit,
    chapters: List<EpubContentLoader.Chapter>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NextPageDimens.spacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentChapter > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_previous_chapter)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = "${currentChapter + 1} / $totalChapters",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = chapters.getOrNull(currentChapter)?.title ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNext,
                enabled = currentChapter < totalChapters - 1
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.reader_next_chapter)
                )
            }
        }
    }
}

@Composable
private fun HighlightDialog(
    selectedText: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var note by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reader_add_highlight)) },
        text = {
            Column {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(NextPageDimens.spacingMd))
                androidx.compose.material3.OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.reader_highlight_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note.ifBlank { null }) }) {
                Text(text = stringResource(R.string.reader_save))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.reader_cancel))
            }
        }
    )
}

@Composable
private fun PdfReaderContent(
    bitmap: Bitmap?,
    currentPage: Int,
    totalPages: Int,
    onTapZone: (Boolean) -> Unit,
    onAddHighlight: (String, String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val isLeftZone = offset.x < size.width / 2
                    onTapZone(isLeftZone)
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page_description, currentPage + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
