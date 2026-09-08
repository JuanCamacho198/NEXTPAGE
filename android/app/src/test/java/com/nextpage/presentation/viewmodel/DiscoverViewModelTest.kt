package com.nextpage.presentation.viewmodel

import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.data.remote.catalog.CatalogException
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.CatalogSource
import com.nextpage.data.remote.catalog.PagedResult
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCatalogProvider : CatalogProvider {
        val searchCalls = mutableListOf<Pair<String, Int>>()
        val detailCalls = mutableListOf<String>()
        var searchError: CatalogErrorCode? = null
        var detailError: CatalogErrorCode? = null
        var page1Results: List<CatalogBook> = listOf(
            book("gutendex:1342", "Pride and Prejudice"),
            book("openlibrary:OL11W", "Borrow Restricted Title")
        )
        var page1Next: Int? = 2
        var page2Results: List<CatalogBook> = listOf(
            book("gutendex:1342", "Pride and Prejudice"),
            book("gutendex:2001", "Page Two Book")
        )

        override suspend fun search(query: String, page: Int): PagedResult {
            searchCalls.add(query to page)
            searchError?.let { throw CatalogException(it, "injected") }
            return when (page) {
                1 -> PagedResult(page1Results, page1Next, 50)
                2 -> PagedResult(page2Results, null, 50)
                else -> throw CatalogException(CatalogErrorCode.INVALID_PAGE, "unexpected page")
            }
        }

        override suspend fun getDetails(id: String): CatalogBook {
            detailCalls.add(id)
            detailError?.let { throw CatalogException(it, "injected") }
            return book(id, "Detail of $id")
        }

        override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
            formats.entries.firstOrNull()?.key ?: throw CatalogException(
                CatalogErrorCode.UNAVAILABLE_DOWNLOAD,
                "no formats"
            )

        companion object {
            fun book(id: String, title: String) = CatalogBook(
                id = id,
                provider = if (id.startsWith("gutendex:")) CatalogSource.GUTENDEX else CatalogSource.OPENLIBRARY,
                title = title,
                authors = listOf("Author A"),
                coverUrl = "https://covers.openlibrary.org/b/id/6794977-M.jpg",
                languages = listOf("en"),
                subjects = emptyList(),
                downloadUrl = "https://www.gutenberg.org/ebooks/1342"
            )
        }
    }

    @Test
    fun blankQueryResetsToIdleWithoutProviderIO() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        assertTrue(provider.searchCalls.isNotEmpty())
        vm.onQueryChange("   ")
        assertEquals(DiscoverStatus.IDLE, vm.uiState.value.status)
        assertTrue(vm.uiState.value.books.isEmpty())
        val callsAfterBlank = provider.searchCalls.size
        vm.searchFirstPage()
        assertEquals(callsAfterBlank, provider.searchCalls.size)
        assertEquals(DiscoverStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun searchLoadsBooksFromProvider() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        assertEquals(DiscoverStatus.LOADED, vm.uiState.value.status)
        assertEquals(50, vm.uiState.value.totalCount)
        assertEquals(2, vm.uiState.value.books.size)
        assertEquals("pride", provider.searchCalls.single().first)
        assertEquals(1, provider.searchCalls.single().second)
    }

    @Test
    fun loadNextPageAppendsWithDedupeAndGuardsNullNextPage() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        val firstCount = vm.uiState.value.books.size
        vm.loadNextPage()
        assertEquals(DiscoverStatus.LOADED, vm.uiState.value.status)
        assertEquals(2, vm.uiState.value.activePage)
        assertTrue(vm.uiState.value.books.size > firstCount)
        val ids = vm.uiState.value.books.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val callsBefore = provider.searchCalls.size
        vm.loadNextPage()
        assertEquals(callsBefore, provider.searchCalls.size)
    }

    @Test
    fun networkErrorMapsToOfflineAndRetryRecovers() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        provider.searchError = CatalogErrorCode.NETWORK_ERROR
        vm.searchFirstPage()
        assertEquals(DiscoverStatus.OFFLINE, vm.uiState.value.status)
        assertEquals(CatalogErrorCode.NETWORK_ERROR, vm.uiState.value.errorCode)
        provider.searchError = null
        vm.retry()
        assertEquals(DiscoverStatus.LOADED, vm.uiState.value.status)
        assertNull(vm.uiState.value.errorCode)
        assertTrue(vm.uiState.value.books.isNotEmpty())
    }

    @Test
    fun zeroResultsMapToEmptyDistinctFromError() = runTest {
        val provider = FakeCatalogProvider()
        provider.page1Results = emptyList()
        provider.page1Next = null
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("no-such-book-xyz")
        vm.searchFirstPage()
        assertEquals(DiscoverStatus.EMPTY, vm.uiState.value.status)
        assertNull(vm.uiState.value.errorCode)
    }

    @Test
    fun badIdDetailMapsToNotFoundAndDismissPreservesList() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        val preserved = vm.uiState.value.books.size
        provider.detailError = CatalogErrorCode.NOT_FOUND
        vm.openDetail("openlibrary:/works/OL11W")
        assertEquals(DiscoverDetailStatus.NOT_FOUND, vm.uiState.value.detailStatus)
        assertNull(vm.uiState.value.detail)
        vm.dismissDetail()
        assertEquals(DiscoverDetailStatus.CLOSED, vm.uiState.value.detailStatus)
        assertEquals(preserved, vm.uiState.value.books.size)
    }


    @Test
    fun upstreamErrorMapsToErrorAndRateLimitedMapsToOffline() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        provider.searchError = CatalogErrorCode.UPSTREAM_ERROR
        vm.searchFirstPage()
        assertEquals(DiscoverStatus.ERROR, vm.uiState.value.status)
        assertEquals(CatalogErrorCode.UPSTREAM_ERROR, vm.uiState.value.errorCode)
        provider.searchError = CatalogErrorCode.RATE_LIMITED
        vm.retry()
        assertEquals(DiscoverStatus.OFFLINE, vm.uiState.value.status)
        assertEquals(CatalogErrorCode.RATE_LIMITED, vm.uiState.value.errorCode)
    }

    @Test
    fun retryAfterFailedNextPageRefetchesSamePage() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        val page1Calls = provider.searchCalls.size
        provider.searchError = CatalogErrorCode.UPSTREAM_ERROR
        vm.loadNextPage()
        assertEquals(DiscoverStatus.ERROR, vm.uiState.value.status)
        val callsAfterFailure = provider.searchCalls.size
        assertTrue(vm.uiState.value.books.isNotEmpty())
        provider.searchError = null
        vm.retry()
        assertEquals(DiscoverStatus.LOADED, vm.uiState.value.status)
        assertEquals(2, vm.uiState.value.activePage)
        assertTrue(provider.searchCalls.size > callsAfterFailure)
        assertEquals(2, provider.searchCalls.last().second)
        assertEquals(page1Calls, 1)
    }

    @Test
    fun successfulDetailLoadPopulatesDetailWithoutTouchingList() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        val preserved = vm.uiState.value.books.size
        vm.openDetail("gutendex:1342")
        assertEquals(DiscoverDetailStatus.LOADED, vm.uiState.value.detailStatus)
        assertEquals("Detail of gutendex:1342", vm.uiState.value.detail?.title)
        assertEquals(preserved, vm.uiState.value.books.size)
        assertEquals(listOf("gutendex:1342"), provider.detailCalls)
    }
    @Test
    fun invalidPageMapsToErrorWithCodeAndListPreserved() = runTest {
        val provider = FakeCatalogProvider()
        val vm = DiscoverViewModel(provider)
        vm.onQueryChange("pride")
        vm.searchFirstPage()
        assertEquals(DiscoverStatus.LOADED, vm.uiState.value.status)
        val preserved = vm.uiState.value.books.size
        provider.searchError = CatalogErrorCode.INVALID_PAGE
        vm.loadNextPage()
        assertEquals(DiscoverStatus.ERROR, vm.uiState.value.status)
        assertEquals(CatalogErrorCode.INVALID_PAGE, vm.uiState.value.errorCode)
        assertEquals(preserved, vm.uiState.value.books.size)
    }

    @Test
    fun constructorSurfaceTakesOnlyCatalogProvider() {
        val ctor = DiscoverViewModel::class.java.constructors.single()
        assertEquals(1, ctor.parameterTypes.size)
        assertEquals(CatalogProvider::class.java, ctor.parameterTypes[0])
    }
}
