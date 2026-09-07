package com.nextpage.data.remote.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val GUTENDEX_ID_PREFIX = "gutendex:"

/**
 * Single hardcoded composite behind the port.
 * Parallel fan-out to both sources; Gutendex wins, OL fills cover gaps.
 * Burst searches are trailing-edge debounced; page < 1 rejects before I/O.
 * Mirrors desktop `CompositeCatalogProvider.ts`.
 */
class CompositeCatalogProvider(
    private val gutendex: GutendexDataSource,
    private val openLibrary: OpenLibraryDataSource,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    debounceMs: Long = DEBOUNCE_MS,
    scope: CoroutineScope? = null
) : CatalogProvider {

    private val clampedPageSize = clampPageSize(pageSize)

    // App-lifetime singleton scope by default; tests inject a TestScope
    // so the trailing-edge debounce runs on virtual time.
    private val debounceScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val debounced = SearchDebouncer(debounceScope, debounceMs, ::executeSearch)

    /** Debounced entry point: only the latest burst query issues network I/O. */
    override suspend fun search(query: String, page: Int): PagedResult {
        if (page < 1) throw catalogError(CatalogErrorCode.INVALID_PAGE, "page must be >= 1, got $page")
        return debounced.search(query, page)
    }

    private suspend fun executeSearch(query: String, page: Int): PagedResult = coroutineScope {
        val g = async { gutendex.search(query, page, clampedPageSize) }
        val o = async { openLibrary.search(query, page, clampedPageSize) }
        val gutendexResult = g.await()
        val olResult = o.await()
        val results = mergeResults(gutendexResult.books, olResult.books)
        toPagedResult(results, page, resolveTotalCount(gutendexResult.totalCount, olResult.totalCount))
    }

    /** Gutendex detail by numeric id; OL ids are not detail-resolvable (NOT_FOUND). */
    override suspend fun getDetails(id: String): CatalogBook {
        if (!id.startsWith(GUTENDEX_ID_PREFIX)) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        val numericId = id.removePrefix(GUTENDEX_ID_PREFIX).toIntOrNull()
        if (numericId == null || numericId < 1) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        return gutendex.getById(numericId)
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)
}
