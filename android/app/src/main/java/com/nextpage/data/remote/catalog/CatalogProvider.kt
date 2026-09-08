package com.nextpage.data.remote.catalog

import kotlinx.serialization.Serializable

/**
 * CatalogProvider port — identical contract on Android (Kotlin) and desktop (TS).
 * Gutendex is metadata/download authority; Open Library enriches + cover fallback.
 */
@Serializable
enum class CatalogSource {
    GUTENDEX,
    OPENLIBRARY
}

@Serializable
data class CatalogBook(
    val id: String,
    val provider: CatalogSource,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    val languages: List<String>,
    val subjects: List<String>,
    val downloadUrl: String?
)

@Serializable
data class PagedResult(
    val results: List<CatalogBook>,
    /** Next 1-based page, or null when the last page was reached. */
    val nextPage: Int?,
    val totalCount: Int
)

/**
 * Stable catalog error codes shared by desktop and Android.
 * Messages are redacted at the boundary; only [code] is contractual.
 */
enum class CatalogErrorCode {
    INVALID_PAGE,
    UNAVAILABLE_DOWNLOAD,
    NOT_FOUND,
    RATE_LIMITED,
    UPSTREAM_ERROR,
    NETWORK_ERROR
}

class CatalogException(
    val code: CatalogErrorCode,
    message: String,
    val retryable: Boolean = code == CatalogErrorCode.RATE_LIMITED ||
        code == CatalogErrorCode.NETWORK_ERROR
) : Exception("$code: $message")

/** Build a typed catalog error without leaking upstream details. */
fun catalogError(code: CatalogErrorCode, detail: String? = null): CatalogException =
    CatalogException(code, detail ?: code.name)

fun isCatalogError(err: Throwable): Boolean = err is CatalogException

/** Map an upstream HTTP status to a stable contract code. */
fun mapHttpStatusToCode(status: Int): CatalogErrorCode = when (status) {
    404 -> CatalogErrorCode.NOT_FOUND
    429 -> CatalogErrorCode.RATE_LIMITED
    in 500..599 -> CatalogErrorCode.UPSTREAM_ERROR
    else -> CatalogErrorCode.UPSTREAM_ERROR
}

interface CatalogProvider {
    /** [page] is 1-based; `page < 1` rejects with INVALID_PAGE before any I/O. */
    suspend fun search(query: String, page: Int): PagedResult

    /** Unknown id rejects with NOT_FOUND. */
    suspend fun getDetails(id: String): CatalogBook

    /**
     * Pure function (no I/O): pick a download URL from a Gutendex `formats` map.
     * Throws UNAVAILABLE_DOWNLOAD when no usable URL exists.
     */
    fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String
}
