package com.nextpage.presentation.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.data.remote.catalog.CatalogException
import com.nextpage.data.remote.catalog.CatalogFeaturedSort
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.PagedResult
import com.nextpage.presentation.viewmodel.DiscoverDetailStatus
import com.nextpage.presentation.viewmodel.DiscoverStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the "Ver todo" section list (RESULTADOS layout minus search). */
data class DiscoverSectionUiState(
    val sectionTitle: String = "",
    val status: DiscoverStatus = DiscoverStatus.LOADING,
    val books: List<CatalogBook> = emptyList(),
    val totalCount: Int = 0,
    val nextPage: Int? = null,
    val activePage: Int = 0,
    val errorCode: CatalogErrorCode? = null,
    val detail: CatalogBook? = null,
    val detailStatus: DiscoverDetailStatus = DiscoverDetailStatus.CLOSED
)

/**
 * Backing ViewModel for [DiscoverSectionScreen].
 *
 * Pages through exactly one selector: [sort] (featured rail) or [sourceId]
 * (per-source list). Neither present — or both present — fails closed with an
 * empty page, so a malformed route can never trigger a composite search or a
 * crash. [term] is the per-source query; U3b drives it, v1 passes nothing.
 */
class DiscoverSectionViewModel(
    private val catalogProvider: CatalogProvider,
    sectionTitle: String,
    private val sort: CatalogFeaturedSort?,
    private val sourceId: String?,
    private val term: String = "",
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverSectionUiState(sectionTitle = sectionTitle))
    val uiState: StateFlow<DiscoverSectionUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() = load(page = 1, append = false)

    /** No-op once the list is exhausted: `nextPage == null` stops all fetching. */
    fun loadNextPage() {
        val state = _uiState.value
        val page = state.nextPage ?: return
        if (state.status == DiscoverStatus.LOADING || state.status == DiscoverStatus.LOADING_MORE) return
        load(page = page, append = true)
    }

    fun retry() {
        if (_uiState.value.activePage <= 1) loadFirstPage() else loadNextPage()
    }

    /** Opens the same base detail sheet as the search grid (existing fields only). */
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
                val code = (err as? CatalogException)?.code ?: CatalogErrorCode.UPSTREAM_ERROR
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

    private fun load(page: Int, append: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    status = if (append) DiscoverStatus.LOADING_MORE else DiscoverStatus.LOADING,
                    errorCode = null
                )
            }
            try {
                val result = fetchPage(page)
                _uiState.update { state ->
                    if (append) {
                        val merged = ArrayList(state.books)
                        val seen = HashSet(merged.map { it.id })
                        for (book in result.results) {
                            if (seen.add(book.id)) merged.add(book)
                        }
                        state.copy(
                            status = DiscoverStatus.LOADED,
                            books = merged,
                            totalCount = result.totalCount,
                            nextPage = result.nextPage,
                            activePage = page
                        )
                    } else {
                        state.copy(
                            status = if (result.results.isEmpty()) {
                                DiscoverStatus.EMPTY
                            } else {
                                DiscoverStatus.LOADED
                            },
                            books = result.results,
                            totalCount = result.totalCount,
                            nextPage = result.nextPage,
                            activePage = page
                        )
                    }
                }
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                val code = (err as? CatalogException)?.code ?: CatalogErrorCode.UPSTREAM_ERROR
                _uiState.update { it.copy(status = DiscoverStatus.ERROR, errorCode = code) }
            }
        }
    }

    private suspend fun fetchPage(page: Int): PagedResult = when {
        sourceId != null && sort == null -> catalogProvider.searchSource(sourceId, term, page)
        sort != null && sourceId == null -> catalogProvider.featured(sort, page)
        // Neither or both selectors: fail closed, never a composite-wide search.
        else -> PagedResult(emptyList(), null, 0)
    }
}

class DiscoverSectionViewModelFactory(
    private val catalogProvider: CatalogProvider,
    private val sectionTitle: String,
    private val sort: CatalogFeaturedSort?,
    private val sourceId: String?,
    private val term: String = "",
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiscoverSectionViewModel::class.java)) {
            return DiscoverSectionViewModel(
                catalogProvider = catalogProvider,
                sectionTitle = sectionTitle,
                sort = sort,
                sourceId = sourceId,
                term = term,
                mainDispatcher = mainDispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
