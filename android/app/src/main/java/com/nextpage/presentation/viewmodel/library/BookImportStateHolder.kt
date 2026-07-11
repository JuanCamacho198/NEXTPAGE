package com.nextpage.presentation.viewmodel.library

import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.usecase.ImportEpubBookUseCase
import com.nextpage.presentation.viewmodel.LibraryImportEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * Manages book import state for the library.
 *
 * Handles EPUB and PDF import flows, transitioning through
 * [BookImportState.Extracting] → [BookImportState.Analyzing] →
 * [BookImportState.Saving] → [BookImportState.Idle], and emitting
 * success/failure events via the [onImportEvent] callback.
 *
 * @param importEpubBookUseCase  Use case for importing EPUB books
 * @param libraryRepository     Repository for PDF import and general book ops
 * @param scope                 CoroutineScope for import jobs (e.g. viewModelScope)
 * @param onImportEvent         Callback to emit [LibraryImportEvent] (success/failure)
 * @param mainDispatcher        Dispatcher for UI-side state updates (default: [Dispatchers.Main])
 * @param onStateChanged        Callback invoked synchronously after every state mutation
 */
class BookImportStateHolder(
    private val importEpubBookUseCase: ImportEpubBookUseCase,
    private val libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
    private val onImportEvent: (LibraryImportEvent) -> Unit,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onStateChanged: (BookImportState) -> Unit = {}
) {
    private val _state = MutableStateFlow<BookImportState>(BookImportState.Idle)
    val state: StateFlow<BookImportState> = _state.asStateFlow()

    init {
        onStateChanged(_state.value)
    }

    /**
     * Import an EPUB book from a content URI.
     * Transitions: Extracting → Analyzing → Saving → Idle, then emits success/failure.
     */
    fun importBookFromEpub(
        sourcePath: String,
        fallbackTitle: String?,
        inputStreamProvider: suspend () -> InputStream?
    ) {
        _state.update { BookImportState.Extracting(0.3f) }
        onStateChanged(_state.value)

        scope.launch(mainDispatcher) {
            delay(200L)
            _state.update { BookImportState.Analyzing(0.6f) }
            onStateChanged(_state.value)

            delay(200L)
            _state.update { BookImportState.Saving(0.9f) }
            onStateChanged(_state.value)

            val result = importEpubBookUseCase(
                request = BookImportRequest(
                    sourcePath = sourcePath,
                    fallbackTitle = fallbackTitle
                ),
                inputStreamProvider = inputStreamProvider
            )

            _state.update { BookImportState.Idle }
            onStateChanged(_state.value)

            result.fold(
                onSuccess = { book ->
                    onImportEvent(LibraryImportEvent.Success(book.title))
                },
                onFailure = { error ->
                    onImportEvent(
                        LibraryImportEvent.Failure(
                            error.message ?: "Failed to import EPUB"
                        )
                    )
                }
            )
        }
    }

    /**
     * Import a PDF book from a local file.
     * Transitions: Extracting → Analyzing → Saving → Idle, then emits success/failure.
     */
    fun importPdfBook(
        sourcePath: String,
        fallbackTitle: String?,
        pdfFile: File
    ) {
        _state.update { BookImportState.Extracting(0.3f) }
        onStateChanged(_state.value)

        scope.launch(mainDispatcher) {
            delay(200L)
            _state.update { BookImportState.Analyzing(0.6f) }
            onStateChanged(_state.value)

            delay(200L)
            _state.update { BookImportState.Saving(0.9f) }
            onStateChanged(_state.value)

            val result = libraryRepository.importBookFromPdf(
                request = BookImportRequest(
                    sourcePath = sourcePath,
                    fallbackTitle = fallbackTitle
                ),
                file = pdfFile
            )

            _state.update { BookImportState.Idle }
            onStateChanged(_state.value)

            result.fold(
                onSuccess = { book ->
                    onImportEvent(LibraryImportEvent.Success(book.title))
                },
                onFailure = { error ->
                    onImportEvent(
                        LibraryImportEvent.Failure(
                            error.message ?: "Failed to import PDF"
                        )
                    )
                }
            )
        }
    }
}
