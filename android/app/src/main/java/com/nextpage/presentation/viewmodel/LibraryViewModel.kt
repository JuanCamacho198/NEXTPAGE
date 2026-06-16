package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.presentation.UiEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val bookToDelete: Book? = null,
    val totalMinutesRead: Long = 0L,
    val readingMinutesByBook: Map<String, Long> = emptyMap(),
    // ── Sync state ──
    val isSyncing: Boolean = false,
    val isRefreshing: Boolean = false,
    val pendingCount: Int = 0,
    val syncError: String? = null,
    // ── UI State (filters, sort, search) ──
    val statusFilter: String = "all",
    val sortBy: String = "date_added",
    val isGridView: Boolean = true,
    val searchQuery: String = "",
    val debouncedSearchQuery: String = "",
    val showSearch: Boolean = false,
    val showFilterSheet: Boolean = false,
    val filterFormat: String = "all"
) {
    /** Books filtered by status, format, and search query, then sorted. */
    val searchedBooks: List<Book>
        get() {
            val byStatus = filterBooks(books, statusFilter, readingMinutesByBook)
            val byFormat = if (filterFormat == "all") byStatus
                else byStatus.filter { it.format == filterFormat }
            val bySearch = if (debouncedSearchQuery.isBlank()) byFormat
                else byFormat.filter {
                    it.title.contains(debouncedSearchQuery, ignoreCase = true) ||
                    it.author?.contains(debouncedSearchQuery, ignoreCase = true) == true
                }
            return sortBookList(bySearch, sortBy)
        }

    companion object {
        /** Minutes of reading considered "completed". */
        const val READING_TARGET_MINUTES = 300L
    }

    private fun filterBooks(
        books: List<Book>,
        statusFilter: String,
        readingMinutesByBook: Map<String, Long>
    ): List<Book> {
        return when (statusFilter) {
            "reading" -> books.filter {
                (readingMinutesByBook[it.id] ?: 0L) > 0L &&
                (readingMinutesByBook[it.id] ?: 0L) < READING_TARGET_MINUTES
            }
            "pending" -> books.filter {
                (readingMinutesByBook[it.id] ?: 0L) == 0L
            }
            "completed" -> books.filter {
                (readingMinutesByBook[it.id] ?: 0L) >= READING_TARGET_MINUTES
            }
            else -> books
        }
    }

    private fun sortBookList(
        books: List<Book>,
        sortBy: String
    ): List<Book> {
        return when (sortBy) {
            "title" -> books.sortedBy { it.title }
            "author" -> books.sortedBy { it.author ?: "" }
            "last_read" -> books.sortedByDescending { it.updatedAtEpochMillis }
            else -> books // "date_added" — already in insertion order
        }
    }
}

sealed interface LibraryImportEvent {
    data class Success(val title: String) : LibraryImportEvent
    data class Failure(val message: String) : LibraryImportEvent
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val importEpubBookUseCase: ImportEpubBookUseCase,
    private val syncService: SyncService,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    private val mutableImportEvents = MutableSharedFlow<LibraryImportEvent>()
    val importEvents: SharedFlow<LibraryImportEvent> = mutableImportEvents.asSharedFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch(mainDispatcher) {
            libraryRepository.observeLibrary().collect { books ->
                mutableUiState.update {
                    it.copy(
                        books = books,
                        isLoading = false
                    )
                }
            }
        }

        viewModelScope.launch(mainDispatcher) {
            libraryRepository.observeTotalReadingTime().collect { totalMinutes ->
                mutableUiState.update { it.copy(totalMinutesRead = totalMinutes) }
            }
        }

        viewModelScope.launch(mainDispatcher) {
            libraryRepository.observeReadingTimeByBook().collect { readingMinutesByBook ->
                mutableUiState.update { it.copy(readingMinutesByBook = readingMinutesByBook) }
            }
        }

        viewModelScope.launch(mainDispatcher) {
            syncService.syncState.collect { state ->
                mutableUiState.update {
                    it.copy(
                        isSyncing = state is SyncState.Running,
                        syncError = (state as? SyncState.Error)?.message,
                        isRefreshing = if (state !is SyncState.Running) false else it.isRefreshing
                    )
                }
            }
        }

        viewModelScope.launch(mainDispatcher) {
            syncService.pendingCount
                .distinctUntilChanged()
                .collect { count ->
                    mutableUiState.update { it.copy(pendingCount = count) }
                }
        }
    }

    fun importBookFromEpub(
        sourcePath: String,
        fallbackTitle: String?,
        inputStreamProvider: suspend () -> InputStream?
    ) {
        mutableUiState.update { it.copy(isImporting = true) }

        viewModelScope.launch(mainDispatcher) {
            val result = importEpubBookUseCase(
                request = BookImportRequest(
                    sourcePath = sourcePath,
                    fallbackTitle = fallbackTitle
                ),
                inputStreamProvider = inputStreamProvider
            )

            mutableUiState.update { it.copy(isImporting = false) }

            result.fold(
                onSuccess = { book ->
                    mutableImportEvents.emit(LibraryImportEvent.Success(book.title))
                },
                onFailure = { error ->
                    mutableImportEvents.emit(
                        LibraryImportEvent.Failure(
                            error.message ?: "Failed to import EPUB"
                        )
                    )
                }
            )
        }
    }

    fun importPdfBook(
        sourcePath: String,
        fallbackTitle: String?,
        pdfFile: File
    ) {
        mutableUiState.update { it.copy(isImporting = true) }

        viewModelScope.launch(mainDispatcher) {
            val result = libraryRepository.importBookFromPdf(
                request = BookImportRequest(
                    sourcePath = sourcePath,
                    fallbackTitle = fallbackTitle
                ),
                file = pdfFile
            )

            mutableUiState.update { it.copy(isImporting = false) }

            result.fold(
                onSuccess = { book ->
                    mutableImportEvents.emit(LibraryImportEvent.Success(book.title))
                },
                onFailure = { error ->
                    mutableImportEvents.emit(
                        LibraryImportEvent.Failure(
                            error.message ?: "Failed to import PDF"
                        )
                    )
                }
            )
        }
    }

    // ── UI State mutations ─────────────────────────────────────────

    private var searchJob: Job? = null

    fun onStatusFilterChanged(filter: String) {
        mutableUiState.update { it.copy(statusFilter = filter) }
    }

    fun onSortByChanged(sort: String) {
        mutableUiState.update { it.copy(sortBy = sort) }
    }

    fun onToggleView() {
        mutableUiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun onToggleSearch() {
        mutableUiState.update {
            it.copy(
                showSearch = !it.showSearch,
                searchQuery = "",
                debouncedSearchQuery = ""
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        mutableUiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            mutableUiState.update { it.copy(debouncedSearchQuery = "") }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            mutableUiState.update { it.copy(debouncedSearchQuery = query) }
        }
    }

    fun onToggleFilterSheet() {
        mutableUiState.update { it.copy(showFilterSheet = !it.showFilterSheet) }
    }

    fun onFilterFormatChanged(format: String) {
        mutableUiState.update { it.copy(filterFormat = format, showFilterSheet = false) }
    }

    // ── Delete ──────────────────────────────────────────────────────

    fun requestDeleteBook(book: Book) {
        mutableUiState.update { it.copy(bookToDelete = book) }
    }

    fun dismissDeleteDialog() {
        mutableUiState.update { it.copy(bookToDelete = null) }
    }

    fun confirmDeleteBook() {
        val book = mutableUiState.value.bookToDelete ?: return

        viewModelScope.launch(mainDispatcher) {
            val result = libraryRepository.deleteBook(book.id)
            mutableUiState.update { it.copy(bookToDelete = null) }

            result.fold(
                onSuccess = {
                    val message = "Deleted \"${book.title}\""
                    _uiEvent.emit(UiEvent.ShowSnackbar(message))
                },
                onFailure = { error ->
                    val message = error.message ?: "Failed to delete book"
                    _uiEvent.emit(UiEvent.ShowSnackbar(message))
                }
            )
        }
    }

    // ── Sync ────────────────────────────────────────────────────────

    fun onPullToRefresh() {
        mutableUiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(mainDispatcher) {
            syncService.schedulePull()
        }
    }
}

class LibraryViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val syncService: SyncService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                libraryRepository = libraryRepository,
                importEpubBookUseCase = ImportEpubBookUseCase(libraryRepository),
                syncService = syncService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
