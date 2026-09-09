package com.nextpage.data.remote.catalog

/**
 * Built-in CatalogProvider adapters over the raw datasources.
 * Gutendex is metadata/download authority; Open Library enriches + cover
 * fallback at the composite level. Book-id formats stay byte-for-byte:
 * `gutendex:<numericId>` / `openlibrary:<key>`. Mirrors desktop
 * `BuiltInCatalogProviders.ts`.
 */
open class GutendexCatalogProvider(
    private val ds: GutendexDataSource
) : CatalogProvider {

    override suspend fun search(query: String, page: Int): PagedResult {
        val result = ds.search(query, page)
        return toPagedResult(result.books, page, result.totalCount)
    }

    /** Gutendex detail by numeric id; malformed ids reject NOT_FOUND. */
    override suspend fun getDetails(id: String): CatalogBook {
        if (!id.startsWith("gutendex:")) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        val numericId = id.removePrefix("gutendex:").toIntOrNull()
        if (numericId == null || numericId < 1) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        return ds.getById(numericId)
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)

    override fun listSources(): List<CatalogSourceInfo> = listOf(
        CatalogSourceInfo(BUILTIN_GUTENDEX, "Gutendex", CatalogSourceKind.BUILTIN)
    )
}

open class OpenLibraryCatalogProvider(
    private val ds: OpenLibraryDataSource
) : CatalogProvider {

    override suspend fun search(query: String, page: Int): PagedResult {
        val result = ds.search(query, page)
        return toPagedResult(result.books, page, result.totalCount)
    }

    /** OL ids are not detail-resolvable (NOT_FOUND — preserved contract). */
    override suspend fun getDetails(id: String): CatalogBook {
        throw catalogError(CatalogErrorCode.NOT_FOUND, "openlibrary ids are not detail-resolvable: $id")
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        com.nextpage.data.remote.catalog.resolveDownloadUrl(formats, preferEpub)

    override fun listSources(): List<CatalogSourceInfo> = listOf(
        CatalogSourceInfo(BUILTIN_OPENLIBRARY, "Open Library", CatalogSourceKind.BUILTIN)
    )
}
