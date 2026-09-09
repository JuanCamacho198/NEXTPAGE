package com.nextpage.data.remote.addons

import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.data.remote.catalog.CatalogErrorCode
import com.nextpage.data.remote.catalog.CatalogProvider
import com.nextpage.data.remote.catalog.CatalogSourceInfo
import com.nextpage.data.remote.catalog.CatalogSourceKind
import com.nextpage.data.remote.catalog.MAX_PAGE_SIZE
import com.nextpage.data.remote.catalog.PagedResult
import com.nextpage.data.remote.catalog.addonSource
import com.nextpage.data.remote.catalog.catalogError
import com.nextpage.data.remote.catalog.computeNextPage
import com.nextpage.data.remote.catalog.isHttpSuccess
import com.nextpage.data.remote.catalog.RETRY_BASE_DELAY_MS
import com.nextpage.data.remote.catalog.shouldRetryStatus
import kotlinx.coroutines.delay
import com.nextpage.data.remote.catalog.mapHttpStatusToCode
import com.nextpage.data.remote.catalog.resolveDownloadUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One [CatalogProvider] per installed addon manifest. Sources are
 * `addon:<addonId>`. Manifests MAY declare endpoint templates
 * (searchUrl/detailsUrl, validated https by [ManifestValidator]); when
 * present, search/getDetails fetch the addon's own catalog payloads through
 * the platform network layer (KtorAddonHttpTransport over the shared catalog
 * HttpClient) and parse them into CatalogBooks with provider = the addon
 * source id. Endpoint-less manifests stay browse-only: stable empty page /
 * NOT_FOUND, zero I/O. Mirrors desktop AddonCatalogProvider.ts.
 */
class AddonCatalogProvider(
    manifest: AddonManifest,
    addonId: String,
    private val transport: AddonHttpTransport?,
    private val retryDelayMs: Long = RETRY_BASE_DELAY_MS
) : CatalogProvider {

    init {
        val hasEndpoint = manifest.searchUrl != null || manifest.detailsUrl != null
        require(!(hasEndpoint && transport == null)) {
            "addon declares catalog endpoints but no transport is configured"
        }
    }

    private val source = CatalogSourceInfo(
        sourceId = addonSource(addonId),
        name = manifest.name,
        kind = CatalogSourceKind.ADDON
    )

    private val searchUrl: String? = manifest.searchUrl
    private val detailsUrl: String? = manifest.detailsUrl

    private val json = Json { ignoreUnknownKeys = true }

    override fun listSources(): List<CatalogSourceInfo> = listOf(source)

    override suspend fun search(query: String, page: Int): PagedResult {
        val template = searchUrl ?: return BROWSE_ONLY_PAGE
        val url = renderTemplate(
            template,
            mapOf("query" to encodeValue(query), "page" to page.toString())
        )
        return parseSearchPayload(fetchJson(url), source.sourceId, page)
    }

    override suspend fun getDetails(id: String): CatalogBook {
        val prefix = "${source.sourceId}:"
        if (!id.startsWith(prefix) || id.length <= prefix.length) {
            throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog id $id")
        }
        val template = detailsUrl
            ?: throw catalogError(CatalogErrorCode.NOT_FOUND, "addon source has no details endpoint: $id")
        val url = renderTemplate(
            template,
            mapOf("bookId" to encodeValue(id.removePrefix(prefix)))
        )
        return parseAddonBook(fetchJson(url), id, source.sourceId)
    }

    private suspend fun fetchJson(url: String): JsonElement {
        var response = callTransport(url)
        if (shouldRetryStatus(response.status)) {
            if (retryDelayMs > 0) delay(retryDelayMs)
            response = callTransport(url)
        }
        if (!response.status.isHttpSuccess()) {
            throw catalogError(mapHttpStatusToCode(response.status), "addon payload status ${response.status}")
        }
        if (response.body.size > ManifestValidator.MAX_MANIFEST_BYTES) {
            throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "addon payload exceeds size cap")
        }
        return try {
            json.parseToJsonElement(response.body.decodeToString())
        } catch (_: Exception) {
            throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "addon payload is not valid JSON")
        }
    }

    private suspend fun callTransport(url: String): AddonResource {
        val transport = transport
            ?: throw catalogError(CatalogErrorCode.NETWORK_ERROR, "addon transport unavailable")
        return try {
            transport.fetch(url)
        } catch (err: Throwable) {
            if (err is com.nextpage.data.remote.catalog.CatalogException) throw err
            throw catalogError(CatalogErrorCode.NETWORK_ERROR, "addon payload request failed")
        }
    }

    override fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String =
        resolveDownloadUrl(formats, preferEpub)

    companion object {
        /** Parity with desktop encodeURIComponent: spaces are %20, never +. */
        private fun encodeValue(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private val BROWSE_ONLY_PAGE = PagedResult(emptyList(), null, 0)

        /** Substitute `{placeholders}` in a validated endpoint template. */
        internal fun renderTemplate(template: String, params: Map<String, String>): String =
            Regex("\\{(\\w+)\\}").replace(template) { match ->
                params[match.groupValues[1]] ?: match.value
            }

        private fun httpsOrNull(value: JsonElement?): String? =
            ((value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content)
                ?.takeIf { it.startsWith("https://") }

        private fun stringArray(value: JsonElement?, field: String): List<String> {
            if (value == null || value is JsonNull) return emptyList()
            val array = value as? JsonArray
                ?: throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "malformed addon payload: $field must be an array of strings")
            return array.map { element ->
                (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                    ?: throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "malformed addon payload: $field must be strings")
            }
        }

        private fun malformed(detail: String): Nothing =
            throw catalogError(CatalogErrorCode.UPSTREAM_ERROR, "malformed addon payload: $detail")

        private fun parseAddonBook(value: JsonElement, bookId: String, sourceId: String): CatalogBook {
            val obj = value as? JsonObject ?: malformed("book must be an object")
            val rawId = (obj["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            val rawTitle = (obj["title"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            if (rawId.isNullOrEmpty() || rawTitle.isNullOrEmpty()) malformed("book missing id/title")
            return CatalogBook(
                id = bookId,
                provider = sourceId,
                title = rawTitle,
                authors = stringArray(obj["authors"], "authors"),
                coverUrl = httpsOrNull(obj["coverUrl"]),
                languages = stringArray(obj["languages"], "languages"),
                subjects = stringArray(obj["subjects"], "subjects"),
                downloadUrl = httpsOrNull(obj["downloadUrl"])
            )
        }

        internal fun parseSearchPayload(payload: JsonElement, sourceId: String, page: Int): PagedResult {
            val obj = payload as? JsonObject ?: malformed("payload must be an object with a results array")
            val results = obj["results"] as? JsonArray ?: malformed("payload must be an object with a results array")
            var totalCount = results.size
            val declared = obj["totalCount"]
            if (declared != null && declared !is JsonNull) {
                val n = (declared as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: malformed("totalCount must be a non-negative integer")
                if (n < 0) malformed("totalCount must be a non-negative integer")
                totalCount = n
            }
            val books = results.mapIndexed { i, element ->
                val entry = element as? JsonObject ?: malformed("book $i must be an object")
                val rawId = (entry["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                if (rawId.isNullOrEmpty()) malformed("book $i missing id")
                parseAddonBook(entry, "$sourceId:${rawId}", sourceId)
            }
            val clamped = books.take(MAX_PAGE_SIZE)
            return PagedResult(
                results = clamped,
                nextPage = computeNextPage(page, clamped.size, totalCount),
                totalCount = totalCount
            )
        }
    }
}

