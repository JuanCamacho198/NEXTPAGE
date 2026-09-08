package com.nextpage.data.remote.catalog

import java.text.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Normalizers, PD predicates, download resolution, merge and pagination math.
 * Pure functions — fully testable offline with fixture JSON.
 * Mirrors desktop `services/catalog/mappers.ts` rule-for-rule.
 */
@Serializable
data class GutendexAuthor(val name: String = "")

@Serializable
data class GutendexRecord(
    val id: Int = 0,
    val title: String = "",
    val authors: List<GutendexAuthor> = emptyList(),
    val copyright: Boolean? = null,
    val languages: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val formats: Map<String, String> = emptyMap()
)

@Serializable
data class OpenLibraryDoc(
    val key: String = "",
    val title: String = "",
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("cover_i") val coverId: Int? = null,
    @SerialName("ebook_access") val ebookAccess: String? = null,
    val language: List<String> = emptyList(),
    val subject: List<String> = emptyList()
)

@Serializable
data class GutendexSearchResponse(
    val count: Int? = null,
    val results: List<GutendexRecord> = emptyList()
)

@Serializable
data class OpenLibrarySearchResponse(
    @SerialName("numFound") val numFound: Int? = null,
    val docs: List<OpenLibraryDoc> = emptyList()
)

/** Gutendex record is public-domain only when `copyright == false`. */
fun isGutendexPublicDomain(record: GutendexRecord): Boolean = record.copyright == false

/** OL doc is usable only with full public ebook access (never borrowable). */
fun isOpenLibraryPublic(doc: OpenLibraryDoc): Boolean = doc.ebookAccess == "public"

fun openLibraryCoverUrl(coverId: Int?): String? {
    if (coverId == null) return null
    return "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
}

/** Map a Gutendex record; null when in-copyright (excluded, never surfaced). */
fun mapGutendexBook(record: GutendexRecord): CatalogBook? {
    if (!isGutendexPublicDomain(record)) return null
    return CatalogBook(
        id = "gutendex:${record.id}",
        provider = CatalogSource.GUTENDEX,
        title = record.title,
        authors = record.authors.map { it.name },
        coverUrl = null,
        languages = record.languages,
        subjects = record.subjects,
        downloadUrl = null
    )
}

/** Map an OL doc; null when borrow-restricted or non-public. */
fun mapOpenLibraryDoc(doc: OpenLibraryDoc): CatalogBook? {
    if (!isOpenLibraryPublic(doc)) return null
    return CatalogBook(
        id = "openlibrary:${doc.key}",
        provider = CatalogSource.OPENLIBRARY,
        title = doc.title,
        authors = doc.authorName,
        coverUrl = openLibraryCoverUrl(doc.coverId),
        languages = doc.language,
        subjects = doc.subject.take(8),
        downloadUrl = null
    )
}

private val EPUB_MIMES = listOf("application/epub+zip", "application/x-mobipocket-ebook")
private val FALLBACK_MIMES = listOf("text/plain", "text/html", "application/pdf")

/**
 * Deterministic download priority: EPUB-first (or fallback-first when
 * [preferEpub] is false). First `https://` match wins, else UNAVAILABLE_DOWNLOAD.
 */
fun resolveDownloadUrl(formats: Map<String, String>, preferEpub: Boolean): String {
    val order = if (preferEpub) EPUB_MIMES + FALLBACK_MIMES else FALLBACK_MIMES + EPUB_MIMES
    for (mime in order) {
        val url = formats[mime]
        if (url != null && url.startsWith("https://")) return url
    }
    throw catalogError(CatalogErrorCode.UNAVAILABLE_DOWNLOAD, "no usable format")
}

private fun normalizeText(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
    return stripped.replace(Regex("[^a-z0-9]+"), " ").trim()
}

/** Normalized title+author key used to pair OL docs with Gutendex records. */
fun normalizeMatchKey(title: String, authors: List<String>): String {
    // Author names arrive in different orders per source ("Austen, Jane" vs
    // "Jane Austen"), so compare sorted word bags instead of raw strings.
    fun authorKey(value: String): String =
        normalizeText(value).split(" ").filter { it.isNotEmpty() }.sorted().joinToString(" ")
    return normalizeText(title) + "|" + authors.map(::authorKey).sorted().joinToString(",")
}

/**
 * Merge sources: every Gutendex field wins; an empty Gutendex `coverUrl`
 * is filled from the matching OL doc. Unmatched OL books are appended.
 */
fun mergeResults(gutendexBooks: List<CatalogBook>, olBooks: List<CatalogBook>): List<CatalogBook> {
    val olByKey = olBooks.associateBy { normalizeMatchKey(it.title, it.authors) }
    val usedOlKeys = mutableSetOf<String>()
    val merged = gutendexBooks.map { g ->
        val key = normalizeMatchKey(g.title, g.authors)
        val match = olByKey[key]
        if (match != null) usedOlKeys.add(key)
        if (match != null && g.coverUrl == null && match.coverUrl != null) {
            g.copy(coverUrl = match.coverUrl)
        } else {
            g
        }
    }
    val extras = olBooks.filter {
        !usedOlKeys.contains(normalizeMatchKey(it.title, it.authors))
    }
    return merged + extras
}

/** Gutendex `count` wins when present, else OL `numFound`. */
fun resolveTotalCount(gutendexCount: Int?, olNumFound: Int): Int {
    if (gutendexCount != null && gutendexCount >= 0) return gutendexCount
    return olNumFound.coerceAtLeast(0)
}

/** Next 1-based page, or null when `offset + pageLength` reached `totalCount`. */
fun computeNextPage(page: Int, pageLength: Int, totalCount: Int): Int? {
    val offset = (page - 1) * pageLength
    return if (offset + pageLength < totalCount) page + 1 else null
}

fun toPagedResult(results: List<CatalogBook>, page: Int, totalCount: Int): PagedResult =
    PagedResult(
        results = results,
        nextPage = computeNextPage(page, results.size, totalCount),
        totalCount = totalCount
    )
