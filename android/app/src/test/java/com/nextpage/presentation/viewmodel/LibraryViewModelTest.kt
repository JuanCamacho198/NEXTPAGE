package com.nextpage.presentation.viewmodel

import android.content.Context
import com.nextpage.data.remote.supabase.SupabaseBookCatalogSync
import com.nextpage.data.remote.supabase.UserBookRow
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import com.nextpage.presentation.UiEvent
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
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
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
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
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
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

    @Test
    fun confirmDeleteBook_deleteSuccessEmitsSuccessEvent() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository()
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
        )

        var emittedEvent: UiEvent? = null
        val collectJob = launch {
            viewModel.uiEvent.collect { emittedEvent = it }
        }
        advanceUntilIdle()

        val book = Book(
            id = "book-delete-1",
            title = "Delete Me",
            author = "Author",
            coverPath = null,
            filePath = "content://books/delete.epub",
            format = "epub",
            updatedAtEpochMillis = 1L
        )
        viewModel.requestDeleteBook(book)
        viewModel.confirmDeleteBook()
        advanceUntilIdle()

        assertTrue(emittedEvent is UiEvent.ShowSnackbar)
        assertEquals(null, viewModel.uiState.value.bookToDelete)

        collectJob.cancel()
    }

    @Test
    fun confirmDeleteBook_deleteFailureEmitsErrorEvent() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository(deleteFailure = IllegalStateException("delete failed"))
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
        )

        var emittedEvent: UiEvent? = null
        val collectJob = launch {
            viewModel.uiEvent.collect { emittedEvent = it }
        }
        advanceUntilIdle()

        val book = Book(
            id = "book-delete-2",
            title = "Delete Fail",
            author = "Author",
            coverPath = null,
            filePath = "content://books/delete-fail.epub",
            format = "epub",
            updatedAtEpochMillis = 1L
        )
        viewModel.requestDeleteBook(book)
        viewModel.confirmDeleteBook()
        advanceUntilIdle()

        assertTrue(emittedEvent is UiEvent.ShowSnackbar)
        assertEquals(null, viewModel.uiState.value.bookToDelete)

        collectJob.cancel()
    }

    @Test
    fun observeReadingTimeByBook_updatesUiStateMap() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository()
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
        )

        repository.emitReadingMinutesByBook(mapOf("book-1" to 42L, "book-2" to 5L))
        advanceUntilIdle()

        assertEquals(42L, viewModel.uiState.value.readingMinutesByBook["book-1"])
        assertEquals(5L, viewModel.uiState.value.readingMinutesByBook["book-2"])
    }

    @Test
    fun searchedBooks_emitsFilteredBooks_whenSearchQueryChanges() = runTest(StandardTestDispatcher()) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLibraryRepository()
        val viewModel = LibraryViewModel(
            libraryRepository = repository,
            importEpubBookUseCase = ImportEpubBookUseCase(repository),
            syncService = FakeSyncService(),
            coverStorage = mockk<CoverStorage>(),
            appContext = mockk<Context>(),
            mainDispatcher = dispatcher,
            catalogSync = catalogSync()
        )

        // searchedBooks uses WhileSubscribed(5000) — subscribe in background
        // so the upstream combine starts collecting immediately.
        backgroundScope.launch {
            viewModel.searchedBooks.collect()
        }

        repository.emitBooks(
            listOf(
                Book(id = "b1", title = "Kotlin Guide", author = "Author A", coverPath = null, filePath = "/a.epub", format = "epub", updatedAtEpochMillis = 1L),
                Book(id = "b2", title = "Java Guide", author = "Author B", coverPath = null, filePath = "/b.epub", format = "epub", updatedAtEpochMillis = 2L),
                Book(id = "b3", title = "Kotlin Coroutines", author = "Author C", coverPath = null, filePath = "/c.epub", format = "epub", updatedAtEpochMillis = 3L)
            )
        )
        advanceUntilIdle()

        // Initial state — all books returned
        assertEquals(3, viewModel.searchedBooks.value.size)

        // Simulate search query via the filter state holder (triggers debouncedSearchQuery)
        viewModel.onSearchQueryChanged("Kotlin")
        advanceUntilIdle()

        // After debounce + filter: 2 books match "Kotlin"
        assertEquals(2, viewModel.searchedBooks.value.size)
        assertEquals("b1", viewModel.searchedBooks.value[0].id)
        assertEquals("b3", viewModel.searchedBooks.value[1].id)
    }

    private class FakeLibraryRepository(
        private val importFailure: Throwable? = null,
        private val deleteFailure: Throwable? = null
    ) : LibraryRepository {
        private val booksFlow = MutableStateFlow<List<Book>>(emptyList())
        private val readingMinutesByBookFlow = MutableStateFlow<Map<String, Long>>(emptyMap())

        override fun observeLibrary(): Flow<List<Book>> = booksFlow

        override fun observeLibraryPaged(): Flow<androidx.paging.PagingData<Book>> =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())

        override fun observeBookById(bookId: String): Flow<Book?> = MutableStateFlow(null)

        override fun observeProgressForBook(bookId: String): Flow<com.nextpage.domain.model.ReadingProgress?> =
            MutableStateFlow(null)

        override suspend fun updateBookRating(bookId: String, rating: Int?) = Unit

        override fun observeTotalReadingTime(): Flow<Long> = MutableStateFlow(0L)

        override fun observeReadingTimeByBook(): Flow<Map<String, Long>> = readingMinutesByBookFlow

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

        override suspend fun updateBookStatus(bookId: String, status: String?): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun updateBookMetadata(
            bookId: String,
            title: String,
            author: String?,
            description: String?,
            coverPath: String?
        ): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun getBookById(bookId: String): Book? = null

        override suspend fun deleteBook(bookId: String): Result<Unit> {
            deleteFailure?.let { return Result.failure(it) }
            return Result.success(Unit)
        }

        fun emitBooks(books: List<Book>) {
            booksFlow.value = books
        }

        fun emitReadingMinutesByBook(readingMinutesByBook: Map<String, Long>) {
            readingMinutesByBookFlow.value = readingMinutesByBook
        }
    }

    /**
     * Creates a mock [SupabaseBookCatalogSync] for testing.
     *
     * First call to [getDownloadableBooks] returns an empty list (init-block poll).
     * Subsequent calls throw [CancellationException] to silently terminate
     * the `while(true)` polling loop — otherwise `advanceUntilIdle()` would loop
     * forever advancing past the 30s delay.
     */
    private fun catalogSync(): SupabaseBookCatalogSync {
        val sync = mockk<SupabaseBookCatalogSync>(relaxed = false)
        var callCount = 0
        coEvery { sync.getDownloadableBooks() } answers {
            callCount++
            if (callCount >= 2) throw CancellationException("test stop")
            Result.success(emptyList())
        }
        return sync
    }

    private class FakeSyncService : SyncService {
        override val syncState: Flow<SyncState> = MutableStateFlow(SyncState.Idle)
        override val pendingCount: Flow<Int> = MutableStateFlow(0)
        override suspend fun bootstrap(userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun schedulePush(): Result<Unit> = Result.success(Unit)
        override suspend fun schedulePull(): Result<Unit> = Result.success(Unit)
    }
}
