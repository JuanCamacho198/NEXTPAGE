package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingStats
import com.nextpage.domain.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val userName: String = "Reader",
    val minutesReadToday: Int = 0,
    val sessionsToday: Int = 0,
    val dailyProgressPercent: Float = 0f,
    val currentBook: Book? = null,
    val recentBooks: List<Book> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    init {
        // Collect daily stats
        viewModelScope.launch {
            homeRepository.observeDailyStats().collect { stats ->
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
            homeRepository.observeCurrentBook().collect { book ->
                _uiState.value = _uiState.value.copy(currentBook = book)
            }
        }

        // Collect recent books
        viewModelScope.launch {
            homeRepository.observeRecentBooks(5).collect { books ->
                _uiState.value = _uiState.value.copy(recentBooks = books)
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
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
