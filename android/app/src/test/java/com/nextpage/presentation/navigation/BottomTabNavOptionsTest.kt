package com.nextpage.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomTabNavOptionsTest {

    // ── Inicio (Home) — special case ──────────────────────────────────

    @Test
    fun `Inicio tap pops up to Home non-inclusive with launchSingleTop and no save-restore`() {
        val options = BottomTabNavOptions.forRoute(
            route = NextPageDestination.Home.route,
            homeRoute = NextPageDestination.Home.route
        )

        assertEquals("popUpTo must target the Home route", NextPageDestination.Home.route, options.popUpToRoute)
        assertFalse("Inicio popUpTo must be non-inclusive", options.popUpToInclusive)
        assertTrue("Inicio tap must launch single top", options.launchSingleTop)
        assertFalse("Inicio must not restore the previous tab's state", options.restoreState)
        assertFalse("Inicio must not save the previous tab's state", options.saveState)
    }

    // ── Other tabs — classic save/restore ─────────────────────────────

    @Test
    fun `other tabs keep restoreState and saveState around Home`() {
        val otherTabs = listOf(
            NextPageDestination.Library,
            NextPageDestination.Highlights,
            NextPageDestination.Settings
        )

        otherTabs.forEach { tab ->
            val options = BottomTabNavOptions.forRoute(
                route = tab.route,
                homeRoute = NextPageDestination.Home.route
            )

            assertEquals("${tab.route}: popUpTo must target the Home route", NextPageDestination.Home.route, options.popUpToRoute)
            assertFalse("${tab.route}: popUpTo must be non-inclusive", options.popUpToInclusive)
            assertTrue("${tab.route}: tab tap must launch single top", options.launchSingleTop)
            assertTrue("${tab.route}: tab tap must restore state", options.restoreState)
            assertTrue("${tab.route}: tab tap must save state", options.saveState)
        }
    }

    // ── Membership guard (design open question) ───────────────────────

    @Test
    fun `every bottom nav destination resolves correct options`() {
        val bottomNavDestinations = listOf(
            NextPageDestination.Home,
            NextPageDestination.Library,
            NextPageDestination.Highlights,
            NextPageDestination.Settings
        )

        bottomNavDestinations.forEach { dest ->
            val options = BottomTabNavOptions.forRoute(
                route = dest.route,
                homeRoute = NextPageDestination.Home.route
            )
            // Home is the only destination without save/restore.
            val isHome = dest.route == NextPageDestination.Home.route
            assertEquals(isHome, !options.restoreState)
            assertEquals(isHome, !options.saveState)
        }
    }
}
