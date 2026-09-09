package com.nextpage.data.remote.addons

import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.CatalogSourceInfo
import com.nextpage.data.remote.catalog.CatalogSourceKind
import com.nextpage.data.remote.catalog.PagedResult
import com.nextpage.data.remote.catalog.addonSource
import com.nextpage.data.remote.catalog.catalogError
import com.nextpage.data.remote.catalog.resolveDownloadUrl

/**
 * One [CatalogProvider] per installed addon manifest. Sources are
 * `addon:<addonId>`; browse-only in this change: search returns an empty page
 * without I/O (preserving the composite's zero-addon parity) and getDetails
 * fails stable NOT_FOUND ("not routable"). Mirrors desktop AddonCatalogProvider.
 */
class AddonCatalogProvider(
    manifest: AddonManifest,
    addonId: String
) : CatalogProvider {

    private val source = CatalogSourceInfo(
        sourceId = addonSource(addonId),
        name = manifest.name,
        kind = CatalogSourceKind.ADDON
    )

    override fun listSources(): List<CatalogSourceInfo> = listOf(source)

    /** Browse-only: contributes nothing to composite search, never I/O. */
    override suspend fun search(query: String, page: Int): PagedResult =
        PagedResult(emptyList(), null, 0)

    /** Addon sources are not routable in this change; stable NOT_FOUND, no I/O. */
    override suspend fun getDetails(id: String): CatalogBook {
        throw catalogError(CatalogErrorCode.NOT_FOUND, "addon sources are not routable: $id")
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        resolveDownloadUrl(formats, preferEpub)
}
