package com.nextpage.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose test proving the bottom-nav home-reset fix: from any
 * bottom-nav tab (Estantería / Resaltados / Ajustes), tapping Inicio collapses
 * the back stack to `[home]` instead of stacking a fresh Home entry on top.
 *
 * Uses a [TestNavHostController] mini-graph mirroring the real
 * `bottomNavDestinations` and drives navigation through the exact production
 * helper [navigateToBottomTab] used by [NextPageNavHost].
 *
 * NOTE: requires a device/emulator to run (androidTest). Compile-level
 * verification is done via `:app:compileDebugAndroidTestKotlin`.
 */
@RunWith(AndroidJUnit4::class)
class NextPageNavHostStackCollapseTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private val otherTabs = listOf(
        NextPageDestination.Library.route,
        NextPageDestination.Highlights.route,
        NextPageDestination.Settings.route
    )

    private fun launchMiniGraph() {
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            NavHost(
                navController = navController,
                startDestination = NextPageDestination.Home.route
            ) {
                composable(NextPageDestination.Home.route) { Text("Home") }
                composable(NextPageDestination.Library.route) { Text("Library") }
                composable(NextPageDestination.Highlights.route) { Text("Highlights") }
                composable(NextPageDestination.Settings.route) { Text("Settings") }
            }
        }
        composeRule.waitForIdle()
    }

    /** Simulates a bottom-nav tap using the same options as [NextPageNavHost]. */
    private fun tapTab(route: String) {
        composeRule.runOnUiThread {
            navController.navigateToBottomTab(
                route = route,
                homeRoute = NextPageDestination.Home.route
            )
        }
        composeRule.waitForIdle()
    }

    private fun currentStack(): List<String> {
        var routes: List<String> = emptyList()
        composeRule.runOnUiThread {
            routes = navController.backStack.mapNotNull { it.destination.route }
        }
        return routes
    }

    @Test
    fun `Inicio tap collapses the stack to home from every tab`() {
        launchMiniGraph()

        otherTabs.forEach { tab ->
            // Navigate to the tab first — classic bottom-nav behavior.
            tapTab(tab)
            assertEquals(
                "expected [home, $tab] before tapping Inicio",
                listOf(NextPageDestination.Home.route, tab),
                currentStack()
            )

            // The fix: Inicio tap must collapse the stack to [home].
            tapTab(NextPageDestination.Home.route)
            assertEquals(
                "Inicio tap from $tab must collapse the stack to [home]",
                listOf(NextPageDestination.Home.route),
                currentStack()
            )
        }
    }
}
