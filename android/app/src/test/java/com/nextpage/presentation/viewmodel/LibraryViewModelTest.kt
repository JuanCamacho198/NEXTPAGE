package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importBookFromEpub_setsImportingThenEmitsSuccess() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository()
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            mainDispatcher = dispatcher
        )

        var emittedEvent: LibraryImportEvent? = null
        val collectJob = launch {
            viewModel.importEvents.collect { emittedEvent = it }
        }
        advanceUntilIdle()

        viewModel.importBookFromEpub(
            sourcePath = "content://books/success.epub",
            fallbackTitle = "Success",
            inputStreamProvider = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        )

        assertTrue(viewModel.uiState.value.isImporting)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isImporting)
        assertTrue(emittedEvent is LibraryImportEvent.Success)

        collectJob.cancel()
    }

    @Test
    fun importBookFromEpub_emitsFailureEventOnError() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository(importFailure = IllegalStateException("bad epub"))
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            mainDispatcher = dispatcher
        )

        var emittedEvent: LibraryImportEvent? = null
        val collectJob = launch {
            viewModel.importEvents.collect { emittedEvent = it }
        }
        advanceUntilIdle()

        viewModel.importBookFromEpub(
            sourcePath = "content://books/failure.epub",
            fallbackTitle = "Failure",
            inputStreamProvider = { null }
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isImporting)
        assertTrue(emittedEvent is LibraryImportEvent.Failure)

        collectJob.cancel()
    }

    @Test
    fun observeLibrary_transitionsFromLoadingToLoaded() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository()
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            mainDispatcher = dispatcher
        )

        assertTrue(viewModel.uiState.value.isLoading)

        repository.emitBooks(
            listOf(
                Book(
                    id = "book-1",
                    title = "Title",
                    author = "Author",
                    coverPath = null,
                    filePath = "content://books/title.epub",
                    format = "epub",
                    updatedAtEpochMillis = 1L
                )
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.books.size)
    }

    private class FakeLibraryRepository(
        private val importFailure: Throwable? = null
    ) : LibraryRepository {
        private val booksFlow = MutableStateFlow<List<Book>>(emptyList())

        override fun observeLibrary(): Flow<List<Book>> = booksFlow

        override fun observeBookById(bookId: String): Flow<Book?> = MutableStateFlow(null)

        override suspend fun importBookFromEpub(
            request: BookImportRequest,
            inputStreamProvider: suspend () -> InputStream?
        ): Result<Book> {
            importFailure?.let { return Result.failure(it) }

            return Result.success(
                Book(
                    id = "imported-1",
                    title = request.fallbackTitle ?: "Untitled",
                    author = null,
                    coverPath = null,
                    filePath = request.sourcePath,
                    format = "epub",
                    updatedAtEpochMillis = 1L
                )
            )
        }

        override suspend fun importBookFromPdf(
            request: BookImportRequest,
            file: File
        ): Result<Book> {
            importFailure?.let { return Result.failure(it) }

            return Result.success(
                Book(
                    id = "imported-pdf-1",
                    title = request.fallbackTitle ?: "Untitled",
                    author = null,
                    coverPath = null,
                    filePath = request.sourcePath,
                    format = "pdf",
                    updatedAtEpochMillis = 1L
                )
            )
        }

        fun emitBooks(books: List<Book>) {
            booksFlow.value = books
        }
    }
}
