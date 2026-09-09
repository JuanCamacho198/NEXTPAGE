package com.nextpage.data.remote.catalog

/**
 * Live-catalog seam: rebuilds the ordered composite from fresh registry rows
 * whenever it is invalidated (design A1/A4 wiring — install, enable/disable,
 * and uninstall immediately change the active source set). Mirrors desktop
 * `createRebuildingCatalogProvider`.
 */
class RebuildingCatalogProvider(
    private val build: suspend () -> CatalogProvider
) {
    private var current: CatalogProvider? = null

    /** Current composite, rebuilding from registry rows after invalidate(). */
    suspend fun provider(): CatalogProvider {
        current?.let { return it }
        return build().also { current = it }
    }

    /** Latest built composite, or null before the first build. */
    fun peek(): CatalogProvider? = current

    /** Drop the cached composite; the next provider() rebuilds from fresh rows. */
    fun invalidate() {
        current = null
    }
}

/**
 * CatalogProvider adapter over [RebuildingCatalogProvider]: every call
 * resolves the current composite, so registry mutations are observed
 * immediately without any runBlocking at construction time.
 */
class LiveCatalogProvider(
    private val supplier: RebuildingCatalogProvider
) : CatalogProvider {
    override suspend fun search(query: String, page: Int): PagedResult =
        supplier.provider().search(query, page)

    override suspend fun getDetails(id: String): CatalogBook =
        supplier.provider().getDetails(id)

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)

    override fun listSources(): List<CatalogSourceInfo> =
        supplier.peek()?.listSources() ?: emptyList()
}
