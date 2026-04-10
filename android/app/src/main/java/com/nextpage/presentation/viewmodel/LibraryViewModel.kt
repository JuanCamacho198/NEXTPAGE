package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
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
    val readingMinutesByBook: Map<String, Long> = emptyMap()
)

sealed interface LibraryImportEvent {
    data class Success(val title: String) : LibraryImportEvent
    data class Failure(val message: String) : LibraryImportEvent
}

sealed interface LibraryUiEvent {
    data class Success(val message: String) : LibraryUiEvent
    data class Failure(val message: String) : LibraryUiEvent
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val importEpubBookUseCase: ImportEpubBookUseCase,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    private val mutableImportEvents = MutableSharedFlow<LibraryImportEvent>()
    val importEvents: SharedFlow<LibraryImportEvent> = mutableImportEvents.asSharedFlow()

    private val mutableUiEvents = MutableSharedFlow<LibraryUiEvent>()
    val uiEvents: SharedFlow<LibraryUiEvent> = mutableUiEvents.asSharedFlow()

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
                    mutableUiEvents.emit(LibraryUiEvent.Success("Deleted \"${book.title}\""))
                },
                onFailure = { error ->
                    mutableUiEvents.emit(
                        LibraryUiEvent.Failure(
                            error.message ?: "Failed to delete book"
                        )
                    )
                }
            )
        }
    }
}

class LibraryViewModelFactory(
    private val libraryRepository: LibraryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                libraryRepository = libraryRepository,
                importEpubBookUseCase = ImportEpubBookUseCase(libraryRepository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
