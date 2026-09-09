package com.nextpage.data.remote.addons

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure manifest validation for the addon registry (Android mirror of desktop
 * `validateManifest.ts`). No I/O: transport fetches bytes, this validates.
 * Shared additive error codes; the Kotlin enum name IS the wire code.
 */
enum class AddonFetchErrorCode {
    HTTPS_REQUIRED,
    TOO_LARGE,
    BAD_CONTENT_TYPE,
    INVALID_MANIFEST,
    NETWORK;

    /** Stable additive wire code (ADDON_FETCH_*), shared with desktop TS/Rust. */
    val wireCode: String
        get() = "ADDON_FETCH_$name"
}

class AddonFetchException(val code: AddonFetchErrorCode, detail: String? = null) :
    Exception("${code.name}: ${detail ?: code.name}")

data class AddonCatalogEntry(val type: String, val id: String, val name: String)

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val catalogs: List<AddonCatalogEntry>,
    val resources: List<String>
)

/** Rules and codes mirror desktop validateManifest.ts byte-for-byte. */
object ManifestValidator {

    const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_CATALOGS = 16
    private const val MAX_CATALOG_ENTRY_CHARS = 512
    private const val MAX_RESOURCES = 16
    private const val MAX_RESOURCE_CHARS = 64

    /** HTTPS-only install URLs: reject before any network I/O. */
    fun assertHttpsInstallUrl(url: String): Boolean {
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
        if (scheme != "https") {
            throw AddonFetchException(AddonFetchErrorCode.HTTPS_REQUIRED, "install URL must be https: $url")
        }
        return true
    }

    private fun isJsonContentType(contentType: String?): Boolean {
        if (contentType == null) return false
        val base = contentType.substringBefore(';').trim().lowercase()
        return base == "application/json" || base.endsWith("+json")
    }

    private fun invalid(detail: String): Nothing = throw AddonFetchException(AddonFetchErrorCode.INVALID_MANIFEST, detail)

    private fun nonEmptyString(value: Any?): Boolean =
        value is String && value.isNotEmpty()

    private fun parseCatalogEntry(entry: Any?): AddonCatalogEntry {
        if (entry !is JSONObject) invalid("catalog entry must be an object")
        for (key in listOf("type", "id", "name")) {
            val value = entry.opt(key)
            if (!nonEmptyString(value) || (value as String).length > MAX_CATALOG_ENTRY_CHARS) {
                invalid("catalog entry missing $key")
            }
        }
        return AddonCatalogEntry(
            type = entry.getString("type"),
            id = entry.getString("id"),
            name = entry.getString("name")
        )
        // unknown entry fields ignored
    }

    private fun parseResources(value: Any?): List<String> {
        if (value !is JSONArray || value.length() == 0) invalid("resources must be a non-empty array")
        if (value.length() > MAX_RESOURCES) invalid("too many resources")
        return (0 until value.length()).map { i ->
            val entry = value.opt(i)
            if (!nonEmptyString(entry) || (entry as String).length > MAX_RESOURCE_CHARS) {
                invalid("invalid resource entry")
            }
            entry
        }
    }

    private fun parseManifestObject(value: Any?): AddonManifest {
        if (value !is JSONObject) invalid("manifest must be a JSON object")
        for (key in listOf("id", "name", "version")) {
            if (!nonEmptyString(value.opt(key))) invalid("missing required field $key")
        }
        val catalogs = value.opt("catalogs")
        if (catalogs !is JSONArray || catalogs.length() == 0) invalid("catalogs must be a non-empty array")
        if (catalogs.length() > MAX_CATALOGS) invalid("too many catalogs")
        return AddonManifest(
            id = value.getString("id"),
            name = value.getString("name"),
            version = value.getString("version"),
            catalogs = (0 until catalogs.length()).map { parseCatalogEntry(catalogs.opt(it)) },
            resources = parseResources(value.opt("resources"))
        )
    }

    /**
     * Validate manifest bytes + upstream content type.
     * Order: size cap (pre-parse) → content type → JSON parse → shape.
     */
    fun validate(bytes: ByteArray, contentType: String?): AddonManifest {
        if (bytes.size > MAX_MANIFEST_BYTES) {
            throw AddonFetchException(AddonFetchErrorCode.TOO_LARGE, "manifest exceeds $MAX_MANIFEST_BYTES bytes")
        }
        if (!isJsonContentType(contentType)) {
            throw AddonFetchException(AddonFetchErrorCode.BAD_CONTENT_TYPE, "content type is not JSON: $contentType")
        }
        val parsed = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            invalid("manifest is not valid JSON")
        }
        return parseManifestObject(parsed)
    }
}
