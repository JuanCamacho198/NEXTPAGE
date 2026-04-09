package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val selectedBookId: String? = null,
    val readingProgress: ReadingProgress? = null,
    val isLoading: Boolean = true
)

class ReaderViewModel(
    private val readerRepository: ReaderRepository,
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase,
    defaultBookId: String?
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ReaderUiState(selectedBookId = defaultBookId)
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var observeProgressJob: Job? = null

    init {
        if (!defaultBookId.isNullOrBlank()) {
            restoreProgressForBook(defaultBookId)
        } else {
            mutableUiState.update { it.copy(isLoading = false) }
        }
    }

    fun restoreProgressForBook(bookId: String) {
        observeProgressJob?.cancel()

        mutableUiState.update {
            it.copy(
                selectedBookId = bookId,
                isLoading = true
            )
        }

        observeProgressJob = viewModelScope.launch {
            readerRepository.observeProgress(bookId).collect { progress ->
                mutableUiState.update {
                    it.copy(
                        readingProgress = progress,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateProgress(bookId: String, cfiLocation: String, percentage: Float) {
        viewModelScope.launch {
            updateReadingProgressUseCase(
                bookId = bookId,
                cfiLocation = cfiLocation,
                percentage = percentage.coerceIn(0f, 100f)
            )
        }
    }
}

class ReaderViewModelFactory(
    private val readerRepository: ReaderRepository,
    private val defaultBookId: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(
                readerRepository = readerRepository,
                updateReadingProgressUseCase = UpdateReadingProgressUseCase(readerRepository),
                defaultBookId = defaultBookId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
