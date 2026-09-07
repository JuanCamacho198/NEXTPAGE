package com.nextpage.data.remote.catalog

import java.net.URLEncoder
import kotlinx.serialization.json.Json

const val GUTENDEX_BASE_URL = "https://gutendex.com"

/**
 * Gutendex datasource — metadata/download authority (`copyright=false`).
 * Ktor transport + identified UA; PD filtering happens in the mapper.
 */
open class GutendexDataSource(
    private val transport: CatalogHttpTransport,
    private val baseUrl: String = GUTENDEX_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    /** Search PD books; in-copyright records are excluded by the mapper. */
    open suspend fun search(
        query: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): CatalogSearchResult {
        val size = clampPageSize(pageSize)
        val params = "search=${query.encode()}&page=$page"
        val res = transport.getWithRetry("$baseUrl/books/?$params")
        val data = json.decodeFromString<GutendexSearchResponse>(res.body)
        val books = data.results.mapNotNull(::mapGutendexBook).take(size)
        return CatalogSearchResult(books, data.count ?: books.size)
    }

    /** Fetch one book by numeric id; unknown ids surface NOT_FOUND. */
    open suspend fun getById(numericId: Int): CatalogBook {
        val res = transport.getWithRetry("$baseUrl/books/$numericId/")
        val record = json.decodeFromString<GutendexRecord>(res.body)
        return mapGutendexBook(record)
            ?: throw catalogError(CatalogErrorCode.NOT_FOUND, "gutendex book $numericId unavailable")
    }

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
