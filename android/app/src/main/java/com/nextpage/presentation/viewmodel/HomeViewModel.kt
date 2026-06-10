package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.presentation.UiEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "Reader",
    val minutesReadToday: Int = 0,
    val sessionsToday: Int = 0,
    val dailyProgressPercent: Float = 0f,
    val currentBook: Book? = null,
    val recentBooks: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    // Search
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Book> = emptyList(),
    val allBooks: List<Book> = emptyList()
) 

class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private var searchJob: Job? = null

    init {
        // Collect daily stats
        viewModelScope.launch {
            homeRepository.observeDailyStats()
                .catch { e -> _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Failed to load daily stats")) }
                .collect { stats ->
                    _uiState.value = _uiState.value.copy(
                        minutesReadToday = stats.minutesRead,
                        sessionsToday = stats.sessionCount,
                        dailyProgressPercent = stats.dailyProgressPercent,
                        isLoading = false
                    )
                }
        }

        // Collect current book
        viewModelScope.launch {
            homeRepository.observeCurrentBook()
                .catch { e -> _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Failed to load current book")) }
                .collect { book ->
                    _uiState.value = _uiState.value.copy(currentBook = book)
                }
        }

        // Collect recent books
        viewModelScope.launch {
            homeRepository.observeRecentBooks(5)
                .catch { e -> _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Failed to load recent books")) }
                .collect { books ->
                    _uiState.value = _uiState.value.copy(recentBooks = books)
                }
        }

        // Collect all books for search
        viewModelScope.launch {
            homeRepository.observeBooks()
                .catch { e -> _uiEvent.emit(UiEvent.ShowSnackbar(e.message ?: "Failed to load books")) }
                .collect { books ->
                    _uiState.value = _uiState.value.copy(allBooks = books)
                }
        }
    }

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onToggleSearch() {
        _uiState.update { it.copy(
            showSearch = !it.showSearch,
            searchQuery = "",
            searchResults = emptyList()
        ) }
        searchJob?.cancel()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val q = query.lowercase()
            val results = _uiState.value.allBooks.filter {
                it.title.lowercase().contains(q) ||
                    (it.author?.lowercase()?.contains(q) == true)
            }
            _uiState.update { it.copy(searchResults = results) }
        }
    }
}

class HomeViewModelFactory(
    private val homeRepository: HomeRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(homeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
