package com.nextpage.presentation.navigation

import com.nextpage.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PR1 RED: Discover destination exists with the `discover` route and its own
 * label resource. v1 keeps Discover out of the bottom bar (entry via
 * Home/Library actions); the NavHost wires [NextPageDestination.Discover].
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
}
