package com.nextpage.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextpage.R
import com.nextpage.ui.icons.NextPageIcons

sealed class NextPageDestination(
    val route: String,
    @param:StringRes val labelRes: Int = -1,
    val icon: ImageVector? = null
) {
    data object Auth : NextPageDestination("auth", R.string.tab_auth, NextPageIcons.Person)
    data object AuthRegister : NextPageDestination("auth/register")
    data object AuthForgot : NextPageDestination("auth/forgot")
    data object OnboardingGoal : NextPageDestination("onboarding/goal")
    data object Home : NextPageDestination("home", R.string.nav_home, NextPageIcons.Home)
    data object Library : NextPageDestination("library", R.string.nav_library, NextPageIcons.Library)
    data object Discover : NextPageDestination("discover", R.string.nav_discover, NextPageIcons.Search)

    /**
     * "Ver todo" section list. All three arguments are optional query params:
     * `sectionTitle` is already localized, and exactly one of `sort` / `sourceId`
     * is non-null so the screen knows which paging seam to use.
     */
    data object DiscoverSection : NextPageDestination(
        "discover/section?sectionTitle={sectionTitle}&sort={sort}&sourceId={sourceId}"
    )
    data object Reader : NextPageDestination("reader?bookId={bookId}&bookPath={bookPath}&bookFormat={bookFormat}", R.string.tab_reader, NextPageIcons.BookOpen) {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_BOOK_PATH = "bookPath"
        const val ARG_BOOK_FORMAT = "bookFormat"

        /**
         * Builds a Reader route carrying the book identity as navigation
         * arguments. The previous host-snapshot plumbing wrote the id into
         * a `rememberSaveable` and navigated to bare `"reader"`, so the
         * new entry captured stale blank values (NavHost composition
         * snapshot). Args ride on the destination entry itself, survive
         * process-death restore, and win over the snapshot backup.
         * Paths are percent-encoded for route safety (spaces/special chars).
         * Encoding is implemented on `Charsets.UTF_8` bytes directly (instead
         * of `android.net.Uri.encode`) so it also runs on plain JVM unit tests
         * where the Android stub throws.
         */
        fun routeFor(bookId: String, filePath: String?, format: String): String {
            val encodedPath = filePath?.takeIf { it.isNotBlank() }?.let { encodeRouteParam(it) }.orEmpty()
            return "reader" +
                "?$ARG_BOOK_ID=${encodeRouteParam(bookId)}" +
                "&$ARG_BOOK_PATH=$encodedPath" +
                "&$ARG_BOOK_FORMAT=${encodeRouteParam(format)}"
        }

        private val ROUTE_PARAM_HEX = "0123456789ABCDEF"

        /** Unsigned byte mask: a Kotlin [Byte] is signed, so widen it modulo 256. */
        private const val BYTE_MASK = 0xFF

        /** Mask for the low nibble of a byte (the two hex digits split 4/4). */
        private const val LOW_NIBBLE_MASK = 0x0F

        /** Shift from the low nibble to the high nibble of a byte. */
        private const val HIGH_NIBBLE_SHIFT = 4

        private fun encodeRouteParam(value: String): String {
            val out = StringBuilder(value.length)
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and BYTE_MASK
                val unreserved = c in 'a'.code..'z'.code ||
                    c in 'A'.code..'Z'.code ||
                    c in '0'.code..'9'.code ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
                if (unreserved) {
                    out.append(c.toChar())
                } else {
                    out.append('%')
                        .append(ROUTE_PARAM_HEX[c shr HIGH_NIBBLE_SHIFT])
                        .append(ROUTE_PARAM_HEX[c and LOW_NIBBLE_MASK])
                }
            }
            return out.toString()
        }
    }
    data object Highlights : NextPageDestination("highlights", R.string.nav_highlights, NextPageIcons.Highlights)
    data object Settings : NextPageDestination("settings", R.string.nav_settings, NextPageIcons.Settings)
    data object Statistics : NextPageDestination("statistics", R.string.nav_statistics, NextPageIcons.Statistics)
    data object BookDetail : NextPageDestination("book_detail/{bookId}", R.string.nav_book_detail, NextPageIcons.Book)
    data object BookEdit : NextPageDestination("book_edit/{bookId}")

    // Settings nested destinations
    data object SettingsList : NextPageDestination("settings/list")
    data object SettingsAccount : NextPageDestination("settings/account")
    data object SettingsDataStorage : NextPageDestination("settings/data")
    data object SettingsStorage : NextPageDestination("settings/storage", R.string.settings_storage_title, NextPageIcons.Storage)
    data object SettingsSync : NextPageDestination("settings/sync", R.string.settings_sync_title, NextPageIcons.CloudSync)
    data object SettingsNotifications : NextPageDestination("settings/notifications")
    data object SettingsTheme : NextPageDestination("settings/theme")
    data object SettingsLanguage : NextPageDestination("settings/language")
    data object SettingsPalette : NextPageDestination("settings/palette")
    data object SettingsAbout : NextPageDestination("settings/about")
    data object SettingsStatistics : NextPageDestination("settings/data/statistics")
    data object SettingsDictionary : NextPageDestination("settings/dictionary", R.string.settings_dictionary_label, NextPageIcons.LibraryBooks)
    data object Dictionary : NextPageDestination("settings/dictionary", R.string.settings_dictionary_label, NextPageIcons.LibraryBooks)
    data object SettingsDevices : NextPageDestination("settings/devices")
    data object SettingsDailyGoal : NextPageDestination("settings/daily-goal")
    data object SettingsPerformance : NextPageDestination("settings/performance")
    data object SettingsAddons : NextPageDestination("settings/addons", R.string.settings_addons_title, NextPageIcons.LibraryBooks)
    /** U5: legal policy page, reachable from the disclaimer and addon screens. */
    data object SettingsLegal : NextPageDestination("settings/legal", R.string.legal_policy_title, NextPageIcons.LibraryBooks)
    /** U5: per-addon capability detail; `{addonId}` is the registry id (hex). */
    data object SettingsAddonCapabilities : NextPageDestination("settings/addon-capabilities/{addonId}")
    data object LogViewer : NextPageDestination("settings/log-viewer")
}
