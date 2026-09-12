package com.nextpage.presentation.navigation

import com.nextpage.R
import com.nextpage.data.remote.catalog.CatalogFeaturedSort
import com.nextpage.presentation.feature.legal.addonCapabilitiesRoute
import com.nextpage.presentation.navigation.feature.discoverSectionRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discover destinations exist with their routes and labels. v1 keeps Discover
 * out of the bottom bar (entry via Home/Library actions); the "Ver todo" section
 * list is a second destination inside the same graph.
 */
class DiscoverNavTest {

    @Test
    fun discover_usesDiscoverRoute() {
        assertEquals("discover", NextPageDestination.Discover.route)
    }

    @Test
    fun discover_usesNavDiscoverLabel() {
        assertEquals(R.string.nav_discover, NextPageDestination.Discover.labelRes)
    }

    @Test
    fun discoverSection_routeCarriesTitleAndBothSelectors() {
        val route = NextPageDestination.DiscoverSection.route
        assertTrue(route.startsWith("discover/section?"))
        assertTrue(route.contains("sectionTitle={sectionTitle}"))
        assertTrue(route.contains("sort={sort}"))
        assertTrue(route.contains("sourceId={sourceId}"))
    }

    @Test
    fun discoverSectionRoute_encodesLocalizedTitleAndSortSelector() {
        val route = discoverSectionRoute("Recién agregados al catálogo", CatalogFeaturedSort.NEWEST, null)
        assertTrue(route.startsWith("discover/section?sectionTitle="))
        assertTrue(route.contains("&sort=NEWEST"))
        assertTrue(route.endsWith("&sourceId="))
        assertFalse("localized title must be percent-encoded", route.contains(" "))
    }

    @Test
    fun discoverSectionRoute_carriesSourceSelectorOnly() {
        val route = discoverSectionRoute("Gutendex", null, "builtin:gutendex")
        assertTrue(route.contains("&sort=&"))
        assertTrue(route.endsWith("&sourceId=builtin%3Agutendex"))
    }

    @Test
    fun settingsLegal_usesLegalRoute() {
        assertEquals("settings/legal", NextPageDestination.SettingsLegal.route)
    }

    @Test
    fun addonCapabilitiesRoute_carriesAddonId() {
        val route = addonCapabilitiesRoute("abc123")
        assertEquals("settings/addon-capabilities/abc123", route)
        assertTrue(NextPageDestination.SettingsAddonCapabilities.route.contains("{addonId}"))
    }
}
