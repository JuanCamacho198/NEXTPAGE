package com.nextpage.data.remote.catalog

import kotlinx.serialization.Serializable

/**
 * CatalogProvider port — identical contract on Android (Kotlin) and desktop (TS).
 * Gutendex is metadata/download authority; Open Library enriches + cover fallback.
 */
/** addonId = sha256(url) first 16 hex chars (design A6) — lowercase only. */
private val ADDON_ID_RE = Regex("^[0-9a-f]{16}$")

/**
 * Strict catalog source ids: 'builtin:<name>' for first-party sources,
 * 'addon:<addonId>' for registry addons. Exact-prefix parsing only —
 * no tolerant parsing, malformed ids rejected. Mirrors desktop
 * `CatalogProvider.ts` (branded string there, constants here).
 */
object CatalogSources {
    const val GUTENDEX = "builtin:gutendex"
    const val OPENLIBRARY = "builtin:openlibrary"

    /** Closed registry of first-party built-in source names. */
    private val KNOWN_BUILTIN_NAMES =
        setOf("gutendex", "openlibrary", "standard-ebooks", "librivox", "wikisource", "faded-page")

    fun addonSource(addonId: String): String = "addon:$addonId"

    /** Extract the addonId from an addon source id; null for non-addon sources. */
    fun addonIdOf(source: String): String? =
        if (source.startsWith("addon:") && ADDON_ID_RE.matches(source.removePrefix("addon:"))) {
            source.removePrefix("addon:")
        } else {
            null
        }

    /** Exact-prefix parse: built-ins are a closed registry; addons require 16-hex ids. */
    fun parse(raw: String): String {
        if (raw.startsWith("builtin:")) {
            val name = raw.removePrefix("builtin:")
            if (name in KNOWN_BUILTIN_NAMES) return raw
        } else if (raw.startsWith("addon:") && ADDON_ID_RE.matches(raw.removePrefix("addon:"))) {
            return raw
        }
        throw catalogError(CatalogErrorCode.NOT_FOUND, "unknown catalog source $raw")
    }
}

const val BUILTIN_GUTENDEX = CatalogSources.GUTENDEX
const val BUILTIN_OPENLIBRARY = CatalogSources.OPENLIBRARY

fun addonSource(addonId: String): String = CatalogSources.addonSource(addonId)

fun addonSourceIdOf(source: String): String? = CatalogSources.addonIdOf(source)

fun parseCatalogSource(raw: String): String = CatalogSources.parse(raw)

@Serializable
data class CatalogBook(
    val id: String,
    val provider: String,
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

    /** Pure function (no I/O): the sources this provider can serve, in order. */
    fun listSources(): List<CatalogSourceInfo>
}

enum class CatalogSourceKind {
    BUILTIN,
    CURATED,
    ADDON
}

data class CatalogSourceInfo(
    val sourceId: String,
    val name: String,
    val kind: CatalogSourceKind
)
