package com.nextpage.data.remote.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** U1: one throwing provider inside `executeSearch` still yields survivors' merged results. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompositePartialTest {

    private fun book(id: String) = CatalogBook(
        id = id,
        provider = BUILTIN_GUTENDEX,
        title = "Title $id",
        authors = listOf("Author"),
        coverUrl = null,
        languages = listOf("en"),
        subjects = emptyList(),
        downloadUrl = null
    )

    private fun stub(sourceId: String, books: List<CatalogBook>, totalCount: Int): CatalogProvider =
        object : CatalogProvider {
            override suspend fun search(query: String, page: Int): PagedResult =
                toPagedResult(books, page, totalCount)

            override suspend fun getDetails(id: String): CatalogBook =
                throw catalogError(CatalogErrorCode.NOT_FOUND, "n/a")

            override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
                throw catalogError(CatalogErrorCode.UNAVAILABLE_DOWNLOAD, "n/a")

            override fun listSources(): List<CatalogSourceInfo> =
                listOf(CatalogSourceInfo(sourceId, "Stub", CatalogSourceKind.BUILTIN))
        }

    private fun failing(sourceId: String): CatalogProvider =
        object : CatalogProvider {
            override suspend fun search(query: String, page: Int): PagedResult =
                throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "boom")

            override suspend fun getDetails(id: String): CatalogBook =
                throw catalogError(CatalogErrorCode.NOT_FOUND, "n/a")

            override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
                throw catalogError(CatalogErrorCode.UNAVAILABLE_DOWNLOAD, "n/a")

            override fun listSources(): List<CatalogSourceInfo> =
                listOf(CatalogSourceInfo(sourceId, "Failing", CatalogSourceKind.BUILTIN))
        }

    @Test fun search_yieldsSurvivorResultsWhenOneProviderThrows() = runTest {
        val survivor = book("gutendex:11")
        val catalog = CompositeCatalogProvider(
            listOf(
                stub(BUILTIN_GUTENDEX, listOf(survivor), 1),
                failing("builtin:failing-a")
            ),
            debounceMs = 0L,
            scope = backgroundScope
        )
        val page = catalog.search("alice", 1)
        assertEquals(listOf(survivor), page.results)
        assertEquals(1, page.totalCount)
    }

    @Test fun search_survivesThrowingProviderRegardlessOfOrder() = runTest {
        val survivor = book("gutendex:11")
        val catalog = CompositeCatalogProvider(
            listOf(
                failing("builtin:failing-a"),
                stub(BUILTIN_GUTENDEX, listOf(survivor), 1)
            ),
            debounceMs = 0L,
            scope = backgroundScope
        )
        val page = catalog.search("alice", 1)
        assertEquals(listOf(survivor), page.results)
    }
}
