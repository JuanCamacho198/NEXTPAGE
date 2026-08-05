package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.AuthSession
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingStats
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "Reader",
    val avatarUrl: String? = null,
    val minutesReadToday: Int = 0,
    val sessionsToday: Int = 0,
    val dailyProgressPercent: Float = 0f,
    val currentBook: Book? = null,
    val currentBookProgress: Float = 0f,
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
        // Single combine: all 5 flows merged into one state emission
        viewModelScope.launch {
            combine(
                homeRepository.observeDailyStats()
                    .catch { e -> _uiEvent.tryEmit(UiEvent.ShowSnackbar(e.message ?: "Failed to load daily stats")); emit(ReadingStats()) },
                homeRepository.observeCurrentBook()
                    .catch { e -> _uiEvent.tryEmit(UiEvent.ShowSnackbar(e.message ?: "Failed to load current book")); emit(null) },
                homeRepository.observeCurrentBookProgress()
                    .catch { e -> _uiEvent.tryEmit(UiEvent.ShowSnackbar(e.message ?: "Failed to load progress")); emit(0f) },
                homeRepository.observeRecentBooks(5)
                    .catch { e -> _uiEvent.tryEmit(UiEvent.ShowSnackbar(e.message ?: "Failed to load recent books")); emit(emptyList()) },
                homeRepository.observeBooks()
                    .catch { e -> _uiEvent.tryEmit(UiEvent.ShowSnackbar(e.message ?: "Failed to load books")); emit(emptyList()) }
            ) { stats, book, progress, recent, allBooks ->
                HomeUiState(
                    // Preserve current user identity across combine emissions —
                    // updated reactively via setActiveSession, not via the (removed)
                    // constructor authSession seed.
                    userName = _uiState.value.userName,
                    avatarUrl = _uiState.value.avatarUrl,
                    minutesReadToday = stats.minutesRead,
                    sessionsToday = stats.sessionCount,
                    dailyProgressPercent = stats.dailyProgressPercent,
                    currentBook = book,
                    currentBookProgress = progress,
                    recentBooks = recent,
                    allBooks = allBooks,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.update { newState }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Reactively updates the home avatar identity from [session].
     *
     * Called from the NavHost via a `LaunchedEffect` on the current session's
     * `userId`/`photoUrl`, so the cached ViewModel (keyed by factory, never
     * rebuilt) still picks up a photo that arrives after async session restore.
     *
     * @param session The current auth session, or `null` on logout (no-op —
     *   keeps the last known identity).
     */
    fun setActiveSession(session: AuthSession?) {
        _uiState.update {
            it.copy(
                userName = session?.displayName?.takeIf { name -> name.isNotBlank() } ?: it.userName,
                avatarUrl = session?.photoUrl ?: it.avatarUrl
            )
        }
    }

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
