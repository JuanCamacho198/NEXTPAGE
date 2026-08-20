package com.nextpage.data.sync

/**
 * LocatorCodec — Android parity for desktop `LocatorCodec.ts`.
 *
 * CFI-first page derivation: page is NOT a persisted source of truth.
 * It is derived from the canonical CFI (cfiRange || cfiLocation) via
 * LocatorCodec(cfi) → 1 or null, fallback 1 for EPUB.
 *
 * Spine prefix grammar: `epubcfi(/6/{spineIndex}!)` where spineIndex ≥ 1.
 * See desktop/src/lib/shared/sync/LocatorCodec.ts for full Readium shape.
 */
object LocatorCodec {

    private val SPINE_INDEX_RE = Regex("""^epubcfi\(/6/(\d+)""")

    /** Normalize href to forward slash (Windows backslash fix). */
    fun normalizeHref(href: String): String = href.replace("\\", "/")

    /**
     * Normalize a locator JSON string: replace backslash in href field with forward slash.
     * Returns original json if no backslash or parse fails (fallback global replace).
     */
    fun normalizeLocatorJson(json: String?): String? {
        if (json.isNullOrEmpty()) return json
        if (!json.contains("\\")) return json
        return try {
            val obj = org.json.JSONObject(json)
            if (obj.has("href")) {
                val href = obj.optString("href")
                if (href.contains("\\")) {
                    obj.put("href", normalizeHref(href))
                    return obj.toString()
                }
            }
            // If href not at top level but json still contains backslash, fallback
            if (json.contains("\\") && json.contains("\"href\"")) {
                // Fallback: global replace for href value only via regex
                // Simple: replace all backslashes (CFI has none, so safe)
                json.replace("\\", "/")
            } else json
        } catch (_: Exception) {
            // Fallback: replace all backslashes if JSON parse fails
            if (json.contains("\\")) json.replace("\\", "/") else json
        }
    }

    /**
     * Extract 1-based spine index from a CFI string. Returns null for malformed/cfi missing.
     */
    fun parseSpineIndex(cfi: String?): Int? {
        if (cfi.isNullOrEmpty()) return null
        val m = SPINE_INDEX_RE.find(cfi) ?: return null
        val raw = m.groupValues.getOrNull(1) ?: return null
        val parsed = raw.toIntOrNull() ?: return null
        if (parsed < 1) return null
        return parsed
    }

    /**
     * Derived page helper — CFI-first page resolution.
     * Returns 1 for any valid `epubcfi(...)`, null when CFI missing/malformed.
     * Callers use `fromCfi(cfiRange ?: cfiLocation) ?: 1`.
     * `current_page` / `pageNumber` are deprecated as sync sources — display-only.
     */
    fun fromCfi(cfi: String?): Int? {
        val idx = parseSpineIndex(cfi)
        return if (idx != null) 1 else null
    }

    /** Alias for callers that expect `derivePage`. */
    fun derivePage(cfi: String?): Int? = fromCfi(cfi)

    /** @deprecated Use `fromCfi(cfi) ?: 1` — `current_page` is no longer canonical. */
    const val currentPageDeprecated: Boolean = true
}
