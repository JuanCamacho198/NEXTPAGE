package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.ReadingStats
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.domain.repository.AuthRepository
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

sealed interface HomeUiEvent {
    data class Error(val message: String) : HomeUiEvent()
    data object BookDeleted : HomeUiEvent()
    data class BookImported(val title: String) : HomeUiEvent()
}

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = mutableEvents.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        mutableUiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            combine(
                homeRepository.observeRecentBooks(5),
                homeRepository.observeDailyStats(),
                homeRepository.observeCurrentBook()
            ) { books, stats, currentBook ->
                Triple(books, stats, currentBook)
            }.collect { (books, stats, currentBook) ->
                mutableUiState.update { state ->
                    state.copy(
                        recentBooks = books,
                        currentBook = currentBook,
                        minutesRead = stats.minutesRead,
                        sessions = stats.sessionCount,
                        dailyProgressPercent = stats.dailyProgressPercent,
                        isLoading = false
                    )
                }
            }
        }

        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentSession()
            sessionResult.getOrNull()?.let { session ->
                mutableUiState.update { state ->
                    state.copy(
                        user = User(
                            id = session.userId,
                            displayName = session.displayName ?: "Usuario",
                            email = session.email,
                            photoUrl = session.photoUrl
                        ),
                        greeting = "Hola, ${session.displayName ?: "Usuario"}"
                    )
                }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true) }
            val result = authRepository.startGoogleSignIn()
            result.fold(
                onSuccess = { url ->
                    mutableEvents.emit(HomeUiEvent.OpenBrowser(url))
                },
                onFailure = { error ->
                    mutableUiState.update { it.copy(isLoading = false, error = error.message) }
                    mutableEvents.emit(HomeUiEvent.Error(error.message ?: "Error de autenticación"))
                }
            )
        }
    }

    fun onGoogleAuthCallback(callbackUri: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true) }
            when (val outcome = authRepository.completeGoogleSignIn(callbackUri)) {
                is com.nextpage.domain.repository.GoogleSignInOutcome.Success -> {
                    mutableUiState.update { state ->
                        state.copy(
                            user = User(
                                id = outcome.session.userId,
                                displayName = outcome.session.displayName ?: "Usuario",
                                email = outcome.session.email,
                                photoUrl = outcome.session.photoUrl
                            ),
                            greeting = "Hola, ${outcome.session.displayName ?: "Usuario"}",
                            isLoading = false
                        )
                    }
                }
                com.nextpage.domain.repository.GoogleSignInOutcome.Cancelled -> {
                    mutableUiState.update { it.copy(isLoading = false) }
                }
                is com.nextpage.domain.repository.GoogleSignInOutcome.Failure -> {
                    mutableUiState.update { it.copy(isLoading = false, error = outcome.error.message) }
                    mutableEvents.emit(HomeUiEvent.Error(outcome.error.message ?: "Error de autenticación"))
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            mutableUiState.update { state ->
                state.copy(
                    user = null,
                    greeting = "Hola"
                )
            }
        }
    }

    fun importEpub(
        sourcePath: String,
        fallbackTitle: String?,
        inputStreamProvider: suspend () -> InputStream?
    ) {
        mutableUiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(ioDispatcher) {
            val result = libraryRepository.importBookFromEpub(
                request = BookImportRequest(
                    sourcePath = sourcePath,
                    fallbackTitle = fallbackTitle
                ),
                inputStreamProvider = inputStreamProvider
            )

            mutableUiState.update { it.copy(isLoading = false) }

            result.fold(
                onSuccess = { book ->
                    mutableEvents.emit(HomeUiEvent.BookImported(book.title))
                },
                onFailure = { error ->
                    mutableUiState.update { it.copy(error = error.message) }
                    mutableEvents.emit(HomeUiEvent.Error(error.message ?: "Error al importar"))
                }
            )
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch(ioDispatcher) {
            val result = homeRepository.deleteBook(bookId)
            result.fold(
                onSuccess = {
                    mutableEvents.emit(HomeUiEvent.BookDeleted)
                },
                onFailure = { error ->
                    mutableUiState.update { it.copy(error = error.message) }
                    mutableEvents.emit(HomeUiEvent.Error(error.message ?: "Error al eliminar"))
                }
            )
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
    }

    class Factory(
        private val homeRepository: HomeRepository,
        private val authRepository: AuthRepository,
        private val libraryRepository: LibraryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                homeRepository = homeRepository,
                authRepository = authRepository,
                libraryRepository = libraryRepository
            ) as T
        }
    }
}