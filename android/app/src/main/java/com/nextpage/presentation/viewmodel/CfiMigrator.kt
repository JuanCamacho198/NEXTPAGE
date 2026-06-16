package com.nextpage.presentation.viewmodel

import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/**
 * Migrates legacy CFI strings (format: "epubcfi(/6/{chapterIndex})") to
 * Readium [Locator] objects using the publication's reading order.
 *
 * The old format stored chapter-level positions only — no intra-chapter
 * precision — so migrated locators point to the start of the chapter
 * (progression = 0.0).
 */
object CfiMigrator {
    private val CFI_REGEX = Regex("""epubcfi\(/6/(\d+)\)""")

    /**
     * Converts a legacy CFI string to a Readium Locator.
     * Returns null if the CFI format is invalid or the chapter index is out of range.
     *
     * Builds the Locator via JSON to avoid depending on Readium's internal
     * [Url] and [MediaType] constructor types directly.
     *
     * @param cfiString e.g. "epubcfi(/6/3)" — chapter index is 1-based
     * @param readingOrder the publication's readingOrder (list of Link)
     */
    fun migrateCfiToLocator(cfiString: String, readingOrder: List<Link>): Locator? {
        val chapterIndex = CFI_REGEX.find(cfiString)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?.minus(1) // Convert to 0-based
            ?: return null

        val link = readingOrder.getOrNull(chapterIndex) ?: return null

        // Build a minimal Locator JSON matching Readium's serialization format.
        val json = JSONObject().apply {
            put("href", link.href.toString())
            put("mediaType", link.mediaType?.toString() ?: "application/xhtml+xml")
            put("locations", JSONObject().apply {
                put("progression", 0.0)
            })
        }
        return Locator.fromJSON(json)
    }

    /** Serializes a Locator to its JSON representation. */
    fun locatorToJson(locator: Locator): String = locator.toJSON().toString()

    /** Deserializes a JSON string back to a Locator. Returns null on failure. */
    fun jsonToLocator(json: String): Locator? = Locator.fromJSON(JSONObject(json))
}
