package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.presentation.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class HighlightsUiState(
    val highlights: List<Highlight> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val books: List<Book> = emptyList(),
    val typeFilter: String = "all",
    val bookFilter: String? = null,
    val colorFilter: String? = null,
    val searchQuery: String = "",
    val filteredHighlights: List<Highlight> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val highlightToEdit: Highlight? = null,
    val highlightToDelete: Highlight? = null,
    val editNoteText: String = ""
)

class HighlightsViewModel(
    private val readerRepository: ReaderRepository,
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val typeFilter = MutableStateFlow("all")
    private val bookFilter = MutableStateFlow<String?>(null)
    private val colorFilter = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val _highlightToEdit = MutableStateFlow<Highlight?>(null)
    private val _highlightToDelete = MutableStateFlow<Highlight?>(null)
    private val _editNoteText = MutableStateFlow("")

    val uiState: StateFlow<HighlightsUiState> = combine(
        readerRepository.observeAllHighlights(),
        readerRepository.observeAllBookmarks(),
        homeRepository.observeBooks(),
        typeFilter,
        bookFilter,
        colorFilter,
        searchQuery,
        _highlightToEdit,
        _highlightToDelete,
        _editNoteText
    ) { values ->
        val highlights = values[0] as List<Highlight>
        val bookmarks = values[1] as List<Bookmark>
        val books = values[2] as List<Book>
        val type = values[3] as String
        val book = values[4] as String?
        val color = values[5] as String?
        val query = values[6] as String
        val highlightToEdit = values[7] as Highlight?
        val highlightToDelete = values[8] as Highlight?
        val editNoteText = values[9] as String
        HighlightsUiState(
            highlights = highlights,
            bookmarks = bookmarks,
            books = books,
            typeFilter = type,
            bookFilter = book,
            colorFilter = color,
            searchQuery = query,
            filteredHighlights = applyFilters(highlights, type, book, color, query),
            highlightToEdit = highlightToEdit,
            highlightToDelete = highlightToDelete,
            editNoteText = editNoteText,
            isLoading = false
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HighlightsUiState()
        )

    fun onTypeFilterChanged(filter: String) {
        typeFilter.update { filter }
    }

    fun onBookFilterChanged(bookId: String?) {
        bookFilter.update { bookId }
    }

    fun onColorFilterChanged(color: String?) {
        colorFilter.update { color }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.update { query }
    }

    fun onEditHighlightNote(highlight: Highlight) {
        _highlightToEdit.update { highlight }
        _editNoteText.update { highlight.note ?: "" }
    }

    fun dismissEditHighlight() {
        _highlightToEdit.update { null }
        _editNoteText.update { "" }
    }

    fun onSaveHighlightNote(text: String) {
        val highlight = _highlightToEdit.value ?: return
        val updated = highlight.copy(
            note = text.ifBlank { null },
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            readerRepository.upsertHighlight(updated)
        }
        _highlightToEdit.update { null }
        _editNoteText.update { "" }
    }

    fun onDeleteHighlight(highlight: Highlight) {
        _highlightToDelete.update { highlight }
    }

    fun dismissDeleteHighlightDialog() {
        _highlightToDelete.update { null }
    }

    fun confirmDeleteHighlight() {
        val highlight = _highlightToDelete.value ?: return
        val updated = highlight.copy(
            deletedAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            readerRepository.upsertHighlight(updated)
            _uiEvent.emit(UiEvent.ShowSnackbar("Highlight deleted"))
        }
        _highlightToDelete.update { null }
    }

    private fun applyFilters(
        highlights: List<Highlight>,
        type: String,
        book: String?,
        color: String?,
        query: String
    ): List<Highlight> {
        return highlights.filter { highlight ->
            val matchesType = when (type) {
                "quotes" -> highlight.type == "quote"
                "ideas" -> highlight.type == "idea"
                "passages" -> highlight.type == "passage"
                else -> true
            }
            val matchesBook = book == null || highlight.bookId == book
            val matchesColor = color == null || highlight.color.equals(color, ignoreCase = true)
            val matchesSearch = query.isBlank() ||
                highlight.textContent.contains(query, ignoreCase = true) ||
                highlight.note?.contains(query, ignoreCase = true) == true
            matchesType && matchesBook && matchesColor && matchesSearch
        }
    }
}

class HighlightsViewModelFactory(
    private val readerRepository: ReaderRepository,
    private val homeRepository: HomeRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HighlightsViewModel::class.java)) {
            return HighlightsViewModel(readerRepository, homeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
