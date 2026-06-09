package com.nextpage.data.pdf

import com.nextpage.domain.model.SearchResult

/**
 * Interface for text search within PDF documents.
 *
 * PDF text search is inherently complex because PdfRenderer does not
 * expose text content. The default stub returns empty results.
 * A proper implementation using OCR (ML Kit) or text extraction (iText)
 * can be plugged in later.
 */
interface PdfSearchHelper {

    /**
     * Search the loaded PDF document for [query].
     * Returns an empty list if no results or if search is unavailable.
     */
    suspend fun searchText(query: String): List<SearchResult>
}

/**
 * Default stub that always returns empty results.
 * Used until a real implementation is hooked up.
 */
class NoopPdfSearchHelper : PdfSearchHelper {
    override suspend fun searchText(query: String): List<SearchResult> = emptyList()
}
