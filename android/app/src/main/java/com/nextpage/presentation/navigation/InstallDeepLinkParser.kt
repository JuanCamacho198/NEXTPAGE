package com.nextpage.presentation.navigation

import android.net.Uri
import com.nextpage.data.remote.addons.ManifestValidator

/**
 * Pure parser for the addon install deep link `nextpage://install?url=<https-url>`
 * (Android mirror of desktop `parseInstallDeepLink`). Non-install URIs (auth
 * callback, reset-password, confirm, Drive redirect) are never claimed — the
 * caller must fall through to the existing auth handling.
 */
object InstallDeepLinkParser {

    /** The install deep-link host inside the nextpage scheme. */
    const val INSTALL_HOST = "install"

    /**
     * True when [uri] is an ACTION_VIEW target this parser owns:
     * scheme `nextpage` with host exactly `install`.
     */
    fun isInstallUri(uri: Uri?): Boolean =
        uri != null && uri.scheme == "nextpage" && uri.host == INSTALL_HOST

    /**
     * Extracts the https manifest URL from an install URI, or null when the
     * link is not an install URI, the `url` param is missing/blank, or the
     * value is not an https URL (http/file/ftp/garbage — rejected BEFORE any
     * network fetch, mirroring desktop REQ https-only).
     */
    fun parse(uri: Uri?): String? {
        if (!isInstallUri(uri)) return null
        val rawUrl = uri!!.getQueryParameter("url")
        if (rawUrl.isNullOrBlank()) return null
        if (!runCatching { ManifestValidator.assertHttpsInstallUrl(rawUrl) }.isSuccess) return null
        return rawUrl
    }
}
