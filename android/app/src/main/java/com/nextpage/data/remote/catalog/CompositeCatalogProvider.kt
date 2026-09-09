package com.nextpage.data.remote.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Ordered dynamic composite behind the port.
 * Providers are searched in construction order (built-ins first, curated,
 * then enabled addons in install order); results concat-merge in that order.
 * Page/details caching is per-source with v2 keys carrying the full source
 * string, and reads enforce the existence check (design A1): entries whose
 * source id is not in the active source set at read time are never served.
 * Burst searches are trailing-edge debounced; page < 1 rejects before I/O.
 * Mirrors desktop `CompositeCatalogProvider.ts`.
 */
class CompositeCatalogProvider(
    private val providers: List<CatalogProvider>,
    debounceMs: Long = DEBOUNCE_MS,
    scope: CoroutineScope? = null,
    private val cache: DiscoverCacheStore? = null,
    private val nowEpochSecs: () -> Long = { System.currentTimeMillis() / 1000 }
) : CatalogProvider {

    private val cacheJson = Json { ignoreUnknownKeys = true }

    // App-lifetime singleton scope by default; tests inject a TestScope
    // so the trailing-edge debounce runs on virtual time.
    private val debounceScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val debounced = SearchDebouncer(debounceScope, debounceMs, ::executeSearch)

    /** Sources in provider order, deduped by sourceId (first occurrence wins). */
    override fun listSources(): List<CatalogSourceInfo> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<CatalogSourceInfo>()
        for (provider in searchableProviders()) {
            for (source in provider.listSources()) {
                if (seen.add(source.sourceId)) out.add(source)
            }
        }
        return out
    }

    /**
     * Debounced entry point: only the latest burst query issues network I/O.
     * Fresh cache entries return immediately without waiting for debounce.
     */
    override suspend fun search(query: String, page: Int): PagedResult {
        if (page < 1) throw catalogError(CatalogErrorCode.INVALID_PAGE, "page must be >= 1, got $page")
        readAllProviderPages(query, page)?.let { return it }
        return debounced.search(query, page)
    }

    private suspend fun executeSearch(query: String, page: Int): PagedResult = coroutineScope {
        // Re-check inside the debounce window: a concurrent caller may have filled it.
        readAllProviderPages(query, page)?.let { return@coroutineScope it }
        val active = activeSourceIds()
        val searches = searchableProviders().map { provider ->
            async {
                val cached = readProviderPage(provider, query, page, active)
                if (cached != null) {
                    cached
                } else {
                    val result = provider.search(query, page)
                    cacheProviderPage(provider, query, page, result, active)
                    result
                }
            }
        }
        mergePaged(searches.map { it.await() }, page)
    }

    /**
     * Whole-composite cache pre-read: when every searchable provider has a
     * fresh page entry, the merged page returns without debounce or I/O.
     */
    private suspend fun readAllProviderPages(query: String, page: Int): PagedResult? {
        val cache = cache ?: return null
        val active = activeSourceIds()
        val providers = searchableProviders().filter { it.listSources().size == 1 }
        if (providers.isEmpty()) return null
        val pages = mutableListOf<PagedResult>()
        for (provider in providers) {
            val cached = readProviderPage(provider, query, page, active) ?: return null
            pages.add(cached)
        }
        return mergePaged(pages, page)
    }

    /** Providers without sources (disabled) contribute nothing and are never called. */
    private fun searchableProviders(): List<CatalogProvider> =
        providers.filter { it.listSources().isNotEmpty() }

    private fun activeSourceIds(): Set<String> =
        searchableProviders().flatMap { it.listSources() }.map { it.sourceId }.toSet()

    /** Existence check (design A1): only sources active at read time may hit. */
    private suspend fun readProviderPage(
        provider: CatalogProvider,
        query: String,
        page: Int,
        active: Set<String>
    ): PagedResult? {
        val store = cache ?: return null
        val source = singleSource(provider) ?: return null
        if (source.sourceId !in active) return null
        val hit = store.get(pageCacheKey(source.sourceId, query, page), nowEpochSecs()) ?: return null
        return cacheJson.decodeFromString(PagedResult.serializer(), hit)
    }

    private suspend fun cacheProviderPage(
        provider: CatalogProvider,
        query: String,
        page: Int,
        result: PagedResult,
        active: Set<String>
    ) {
        val store = cache ?: return
        val source = singleSource(provider) ?: return
        if (source.sourceId !in active) return
        store.put(
            pageCacheKey(source.sourceId, query, page),
            cacheJson.encodeToString(PagedResult.serializer(), result),
            nowEpochSecs(),
            PAGE_TTL_S
        )
    }

    /**
     * Ordered merge: left-fold the provider pages — earlier providers win fields,
     * later ones fill cover gaps and append unmatched books (the [Gutendex,
     * OpenLibrary] fold reproduces the legacy hardcoded-pair merge exactly).
     */
    private fun mergePaged(pages: List<PagedResult>, page: Int): PagedResult {
        if (pages.isEmpty()) return toPagedResult(emptyList(), page, 0)
        var results = pages.first().results
        var totalCount = pages.first().totalCount
        for (i in 1 until pages.size) {
            results = mergeResults(results, pages[i].results)
            totalCount = resolveTotalCount(totalCount, pages[i].totalCount)
        }
        return toPagedResult(results, page, totalCount)
    }

    /**
     * Exact-prefix routing over the active source set: `gutendex:`/`openlibrary:`
     * book ids hit the built-ins, `addon:<addonId>:<bookId>` the owning addon.
     * Unroutable ids reject NOT_FOUND without any I/O.
     */
    override suspend fun getDetails(id: String): CatalogBook {
        val route = routeDetails(id)
            ?: throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        val active = activeSourceIds()
        if (cache != null && route.source.sourceId in active) {
            val hit = cache.get(detailCacheKey(route.source.sourceId, id), nowEpochSecs())
            if (hit != null) return cacheJson.decodeFromString(CatalogBook.serializer(), hit)
        }
        val book = route.provider.getDetails(id)
        if (cache != null && route.source.sourceId in active) {
            cache.put(
                detailCacheKey(route.source.sourceId, id),
                cacheJson.encodeToString(CatalogBook.serializer(), book),
                nowEpochSecs(),
                DETAIL_TTL_S
            )
        }
        return book
    }

    private fun routeDetails(id: String): RoutedDetails? {
        var best: RoutedDetails? = null
        var bestPrefix = ""
        for (provider in searchableProviders()) {
            for (source in provider.listSources()) {
                val prefix = bookIdPrefixForSource(source.sourceId) ?: continue
                if (!id.startsWith(prefix) || id.length <= prefix.length) continue
                if (best == null || prefix.length > bestPrefix.length) {
                    best = RoutedDetails(provider, source)
                    bestPrefix = prefix
                }
            }
        }
        return best
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)

    data class RoutedDetails(val provider: CatalogProvider, val source: CatalogSourceInfo)

    companion object {
        /** Providers exposing exactly one source are page-cacheable under that source. */
        fun singleSource(provider: CatalogProvider): CatalogSourceInfo? =
            provider.listSources().singleOrNull()

        /**
         * Book-id prefix owned by a source: built-ins drop the `builtin:` namespace
         * (`builtin:gutendex` -> `gutendex:`); addons use the source id itself
         * (`addon:<id>` -> `addon:<id>:`).
         */
        fun bookIdPrefixForSource(sourceId: String): String? = when {
            sourceId.startsWith("builtin:") -> "${sourceId.removePrefix("builtin:")}:"
            sourceId.startsWith("addon:") -> "$sourceId:"
            else -> null
        }
    }
}
