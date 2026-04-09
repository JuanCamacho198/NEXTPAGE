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
import java.io.InputStream

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false
)

sealed interface LibraryImportEvent {
    data class Success(val title: String) : LibraryImportEvent
    data class Failure(val message: String) : LibraryImportEvent
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
