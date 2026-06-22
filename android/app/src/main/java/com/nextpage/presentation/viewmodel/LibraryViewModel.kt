package com.nextpage.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.sync.SyncService
import com.nextpage.data.remote.sync.SyncState
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.BookStatus
import com.nextpage.domain.model.effectiveStatus
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import com.nextpage.presentation.UiEvent
import com.nextpage.presentation.viewmodel.library.BookActionStateHolder
import com.nextpage.presentation.viewmodel.library.BookFilterStateHolder
import com.nextpage.presentation.viewmodel.library.BookImportStateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val bookToDelete: Book? = null,
    val bookToEdit: Book? = null,
    val bookToShare: Book? = null,
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
    companion object {
        /** Minutes of reading considered "completed". */
        const val READING_TARGET_MINUTES = 300L
    }
}

/**
 * Filters [books] by [statusFilter] using [readingMinutesByBook] for derived status.
 */
private fun filterBooks(
    books: List<Book>,
    statusFilter: String,
    readingMinutesByBook: Map<String, Long>
): List<Book> {
    return when (statusFilter) {
        "reading" -> books.filter {
            val eff = it.effectiveStatus(readingMinutesByBook[it.id] ?: 0L)
            eff == BookStatus.READING
        }
        "pending" -> books.filter {
            val eff = it.effectiveStatus(readingMinutesByBook[it.id] ?: 0L)
            eff == "pending" || eff == BookStatus.PLAN_TO_READ
        }
        "completed" -> books.filter {
            val eff = it.effectiveStatus(readingMinutesByBook[it.id] ?: 0L)
            eff == BookStatus.COMPLETED
        }
        else -> books
    }
}

/**
 * Sorts [books] by [sortBy] criteria.
 */
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

sealed interface LibraryImportEvent {
    data class Success(val title: String) : LibraryImportEvent
    data class Failure(val message: String) : LibraryImportEvent
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val importEpubBookUseCase: ImportEpubBookUseCase,
    private val syncService: SyncService,
    private val coverStorage: CoverStorage,
    private val appContext: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    /** Memoized searched/filtered/sorted books derived from [mutableUiState]. */
    val searchedBooks: StateFlow<List<Book>> = combine(
        mutableUiState.map { it.books },
        mutableUiState.map { it.statusFilter },
        mutableUiState.map { it.filterFormat },
        mutableUiState.map { it.debouncedSearchQuery },
        mutableUiState.map { it.sortBy },
        mutableUiState.map { it.readingMinutesByBook }
    ) { books, statusFilter, filterFormat, searchQuery, sortBy, readingMinutesByBook ->
        val byStatus = filterBooks(books, statusFilter, readingMinutesByBook)
        val byFormat = if (filterFormat == "all") byStatus
            else byStatus.filter { it.format == filterFormat }
        val bySearch = if (searchQuery.isBlank()) byFormat
            else byFormat.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.author?.contains(searchQuery, ignoreCase = true) == true
            }
        sortBookList(bySearch, sortBy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val mutableImportEvents = MutableSharedFlow<LibraryImportEvent>(extraBufferCapacity = 1)
    val importEvents: SharedFlow<LibraryImportEvent> = mutableImportEvents.asSharedFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // ── Holders ────────────────────────────────────────────────────────

    private val bookFilterStateHolder = BookFilterStateHolder(
        scope = viewModelScope,
        mainDispatcher = mainDispatcher,
        onStateChanged = { filter ->
            mutableUiState.update { current ->
                current.copy(
                    statusFilter = filter.statusFilter,
                    sortBy = filter.sortBy,
                    isGridView = filter.isGridView,
                    searchQuery = filter.searchQuery,
                    debouncedSearchQuery = filter.debouncedSearchQuery,
                    showSearch = filter.showSearch,
                    showFilterSheet = filter.showFilterSheet,
                    filterFormat = filter.filterFormat
                )
            }
        }
    )

    private val bookImportStateHolder = BookImportStateHolder(
        importEpubBookUseCase = importEpubBookUseCase,
        libraryRepository = libraryRepository,
        scope = viewModelScope,
        onImportEvent = { mutableImportEvents.tryEmit(it) },
        mainDispatcher = mainDispatcher,
        onStateChanged = { importState ->
            mutableUiState.update { it.copy(isImporting = importState.isImporting) }
        }
    )

    private val bookActionStateHolder = BookActionStateHolder(
        libraryRepository = libraryRepository,
        coverStorage = coverStorage,
        appContext = appContext,
        scope = viewModelScope,
        onUiEvent = { _uiEvent.tryEmit(it) },
        mainDispatcher = mainDispatcher,
        onStateChanged = { action ->
            mutableUiState.update { current ->
                current.copy(
                    bookToDelete = action.bookToDelete,
                    bookToEdit = action.bookToEdit,
                    bookToShare = action.bookToShare
                )
            }
        }
    )

    init {
        // ── Lifecycle observations (Room flows) ──
        viewModelScope.launch(mainDispatcher) {
            libraryRepository.observeLibrary().collect { books ->
                mutableUiState.update { it.copy(books = books, isLoading = false) }
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

        // ── Sync service observations ──
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

    // ── Delegation: Filter / Sort / Search ─────────────────────────────

    fun onStatusFilterChanged(filter: String) = bookFilterStateHolder.onStatusFilterChanged(filter)
    fun onSortByChanged(sort: String) = bookFilterStateHolder.onSortByChanged(sort)
    fun onToggleView() = bookFilterStateHolder.onToggleView()
    fun onToggleSearch() = bookFilterStateHolder.onToggleSearch()
    fun onSearchQueryChanged(query: String) = bookFilterStateHolder.onSearchQueryChanged(query)
    fun onToggleFilterSheet() = bookFilterStateHolder.onToggleFilterSheet()
    fun onFilterFormatChanged(format: String) = bookFilterStateHolder.onFilterFormatChanged(format)

    // ── Delegation: Import ─────────────────────────────────────────────

    fun importBookFromEpub(
        sourcePath: String,
        fallbackTitle: String?,
        inputStreamProvider: suspend () -> InputStream?
    ) = bookImportStateHolder.importBookFromEpub(sourcePath, fallbackTitle, inputStreamProvider)

    fun importPdfBook(
        sourcePath: String,
        fallbackTitle: String?,
        pdfFile: File
    ) = bookImportStateHolder.importPdfBook(sourcePath, fallbackTitle, pdfFile)

    // ── Delegation: Actions (Delete / Edit / Share / Status) ──────────

    fun requestDeleteBook(book: Book) = bookActionStateHolder.requestDeleteBook(book)
    fun dismissDeleteDialog() = bookActionStateHolder.dismissDeleteDialog()
    fun confirmDeleteBook() = bookActionStateHolder.confirmDeleteBook()
    fun requestEditBook(book: Book) = bookActionStateHolder.requestEditBook(book)
    fun dismissEditDialog() = bookActionStateHolder.dismissEditDialog()
    fun confirmEditBook(
        book: Book,
        title: String,
        author: String?,
        description: String?,
        coverBytes: ByteArray?
    ) = bookActionStateHolder.confirmEditBook(book, title, author, description, coverBytes)
    fun onMenuMarkCompleted(book: Book) = bookActionStateHolder.onMenuMarkCompleted(book)
    fun onMenuMarkPlanToRead(book: Book) = bookActionStateHolder.onMenuMarkPlanToRead(book)
    fun onMenuShare(book: Book) = bookActionStateHolder.onMenuShare(book)

    // ── Sync (stays in ViewModel — cross-cutting concern) ──────────────

    fun onPullToRefresh() {
        mutableUiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(mainDispatcher) {
            syncService.schedulePull()
        }
    }
}

class LibraryViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val syncService: SyncService,
    private val coverStorage: CoverStorage,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                libraryRepository = libraryRepository,
                importEpubBookUseCase = ImportEpubBookUseCase(libraryRepository),
                syncService = syncService,
                coverStorage = coverStorage,
                appContext = appContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
