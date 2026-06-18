package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextpage.domain.model.DictionaryWord
import com.nextpage.domain.repository.DictionaryRepository
import com.nextpage.presentation.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DictionaryUiState(
    val words: List<DictionaryWord> = emptyList(),
    val searchQuery: String = "",
    val filteredWords: List<DictionaryWord> = emptyList(),
    val isLoading: Boolean = true,
    val wordToDelete: DictionaryWord? = null,
    val showAddDialog: Boolean = false,
    val addWordText: String = ""
)

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                dictionaryRepository.observeAll(),
                searchQuery
            ) { words, query ->
                val filtered = if (query.isBlank()) words
                else words.filter { it.word.contains(query, ignoreCase = true) }
                _uiState.update {
                    it.copy(
                        words = words,
                        searchQuery = query,
                        filteredWords = filtered,
                        isLoading = false
                    )
                }
            }.collect { }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.update { query }
    }

    fun onShowAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, addWordText = "") }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, addWordText = "") }
    }

    fun onAddWordTextChanged(text: String) {
        _uiState.update { it.copy(addWordText = text) }
    }

    fun onAddWordConfirm() {
        val text = _uiState.value.addWordText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            if (dictionaryRepository.exists(text)) {
                _uiEvent.emit(UiEvent.ShowSnackbar(
                    "Already in dictionary"
                ))
            } else {
                dictionaryRepository.save(text).fold(
                    onSuccess = {
                        _uiEvent.emit(UiEvent.ShowSnackbar(
                            "\"$text\" added to your dictionary."
                        ))
                    },
                    onFailure = { e ->
                        _uiEvent.emit(UiEvent.ShowSnackbar(
                            e.message ?: "Failed to add word"
                        ))
                    }
                )
            }
            _uiState.update { it.copy(showAddDialog = false, addWordText = "") }
        }
    }

    fun onRequestDeleteWord(word: DictionaryWord) {
        _uiState.update { it.copy(wordToDelete = word) }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(wordToDelete = null) }
    }

    fun onConfirmDeleteWord() {
        val word = _uiState.value.wordToDelete ?: return
        viewModelScope.launch {
            dictionaryRepository.delete(word.id)
            _uiState.update { it.copy(wordToDelete = null) }
        }
    }
}
