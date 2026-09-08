package com.nextpage.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.data.remote.catalog.CatalogException
import com.nextpage.data.remote.catalog.CatalogProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen-level status for the Discover search flow. */
enum class DiscoverStatus {
    IDLE,
    LOADING,
    LOADING_MORE,
    LOADED,
    EMPTY,
    ERROR,
    OFFLINE
}

/** Detail pane status, independent from the search-list status. */
enum class DiscoverDetailStatus {
    CLOSED,
    LOADING,
    LOADED,
    NOT_FOUND,
    ERROR
}

/** Immutable UI state exposed by [DiscoverViewModel]. */
data class DiscoverUiState(
    val query: String = "",
    val status: DiscoverStatus = DiscoverStatus.IDLE,
    val books: List<CatalogBook> = emptyList(),
    val totalCount: Int = 0,
    val nextPage: Int? = null,
    val activePage: Int = 0,
    val errorCode: CatalogErrorCode? = null,
    val detail: CatalogBook? = null,
    val detailStatus: DiscoverDetailStatus = DiscoverDetailStatus.CLOSED
)

class DiscoverViewModel(
    private val catalogProvider: CatalogProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var lastAttemptedPage = 0
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.trim().isEmpty()) {
            resetToIdle()
        }
    }

    fun searchFirstPage() {
        val query = _uiState.value.query
        if (query.trim().isEmpty()) {
            resetToIdle()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(status = DiscoverStatus.LOADING, errorCode = null)
            }
            lastAttemptedPage = 1
            try {
                val page = catalogProvider.search(query, 1)
                _uiState.update {
                    it.copy(
                        status = if (page.results.isEmpty()) DiscoverStatus.EMPTY else DiscoverStatus.LOADED,
                        books = page.results,
                        totalCount = page.totalCount,
                        nextPage = page.nextPage,
                        activePage = 1
                    )
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val code = codeOf(err)
                _uiState.update { state ->
                    state.copy(status = statusForCode(code), errorCode = code)
                }
            }
        }
    }

    fun loadNextPage() {
        val page = _uiState.value.nextPage
        if (page == null || _uiState.value.status == DiscoverStatus.LOADING ||
            _uiState.value.status == DiscoverStatus.LOADING_MORE
        ) {
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(status = DiscoverStatus.LOADING_MORE, errorCode = null) }
            lastAttemptedPage = page
            try {
                val result = catalogProvider.search(_uiState.value.query, page)
                val merged = ArrayList(_uiState.value.books)
                val seen = HashSet(merged.map { it.id })
                for (book in result.results) {
                    if (seen.add(book.id)) {
                        merged.add(book)
                    }
                }
                _uiState.update {
                    it.copy(
                        status = DiscoverStatus.LOADED,
                        books = merged,
                        totalCount = result.totalCount,
                        nextPage = result.nextPage,
                        activePage = page
                    )
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val code = codeOf(err)
                _uiState.update { state ->
                    state.copy(status = statusForCode(code), errorCode = code)
                }
            }
        }
    }

    fun openDetail(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailStatus = DiscoverDetailStatus.LOADING, detail = null) }
            try {
                val detail = catalogProvider.getDetails(id)
                _uiState.update {
                    it.copy(detailStatus = DiscoverDetailStatus.LOADED, detail = detail)
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val code = codeOf(err)
                _uiState.update {
                    it.copy(
                        detailStatus = if (code == CatalogErrorCode.NOT_FOUND) {
                            DiscoverDetailStatus.NOT_FOUND
                        } else {
                            DiscoverDetailStatus.ERROR
                        },
                        detail = null
                    )
                }
            }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(detail = null, detailStatus = DiscoverDetailStatus.CLOSED) }
    }

    fun retry() {
        if (lastAttemptedPage <= 1) {
            searchFirstPage()
        } else {
            _uiState.update { it.copy(nextPage = lastAttemptedPage) }
            loadNextPage()
        }
    }

    private fun resetToIdle() {
        _uiState.value = DiscoverUiState(query = _uiState.value.query)
    }

    private fun statusForCode(code: CatalogErrorCode): DiscoverStatus = when (code) {
        CatalogErrorCode.NETWORK_ERROR, CatalogErrorCode.RATE_LIMITED -> DiscoverStatus.OFFLINE
        else -> DiscoverStatus.ERROR
    }

    private fun codeOf(err: Throwable): CatalogErrorCode =
        (err as? CatalogException)?.code ?: CatalogErrorCode.UPSTREAM_ERROR

}

class DiscoverViewModelFactory(
    private val catalogProvider: CatalogProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiscoverViewModel::class.java)) {
            return DiscoverViewModel(catalogProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
