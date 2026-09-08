package com.nextpage.data.remote.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

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
    scope: CoroutineScope? = null,
    private val cache: DiscoverCacheStore? = null,
    private val nowEpochSecs: () -> Long = { System.currentTimeMillis() / 1000 }
) : CatalogProvider {

    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val clampedPageSize = clampPageSize(pageSize)

    // App-lifetime singleton scope by default; tests inject a TestScope
    // so the trailing-edge debounce runs on virtual time.
    private val debounceScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val debounced = SearchDebouncer(debounceScope, debounceMs, ::executeSearch)

    /**
     * Debounced entry point: only the latest burst query issues network I/O.
     * Fresh cache entries return immediately without waiting for debounce.
     */
    override suspend fun search(query: String, page: Int): PagedResult {
        if (page < 1) throw catalogError(CatalogErrorCode.INVALID_PAGE, "page must be >= 1, got $page")
        readPageCache(query, page)?.let { return it }
        return debounced.search(query, page)
    }

    private suspend fun executeSearch(query: String, page: Int): PagedResult = coroutineScope {
        // Re-check inside the debounce window: a concurrent caller may have filled it.
        readPageCache(query, page)?.let { return@coroutineScope it }
        val g = async { gutendex.search(query, page, clampedPageSize) }
        val o = async { openLibrary.search(query, page, clampedPageSize) }
        val gutendexResult = g.await()
        val olResult = o.await()
        val results = mergeResults(gutendexResult.books, olResult.books)
        val paged = toPagedResult(results, page, resolveTotalCount(gutendexResult.totalCount, olResult.totalCount))
        cache?.put(pageCacheKey(query, page), cacheJson.encodeToString(PagedResult.serializer(), paged), nowEpochSecs(), PAGE_TTL_S)
        paged
    }

    /**
     * Gutendex detail by numeric id; OL ids are not detail-resolvable (NOT_FOUND).
     * Details are cached 7d; [resolveDownloadUrl] stays a pure I/O-free path.
     */
    override suspend fun getDetails(id: String): CatalogBook {
        if (!id.startsWith(GUTENDEX_ID_PREFIX)) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        val numericId = id.removePrefix(GUTENDEX_ID_PREFIX).toIntOrNull()
        if (numericId == null || numericId < 1) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        if (cache != null) {
            val hit = cache.get(detailCacheKey(id), nowEpochSecs())
            if (hit != null) return cacheJson.decodeFromString(CatalogBook.serializer(), hit)
        }
        val book = gutendex.getById(numericId)
        cache?.put(detailCacheKey(id), cacheJson.encodeToString(CatalogBook.serializer(), book), nowEpochSecs(), DETAIL_TTL_S)
        return book
    }

    private suspend fun readPageCache(query: String, page: Int): PagedResult? {
        val store = cache ?: return null
        val hit = store.get(pageCacheKey(query, page), nowEpochSecs()) ?: return null
        return cacheJson.decodeFromString(PagedResult.serializer(), hit)
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)
}
