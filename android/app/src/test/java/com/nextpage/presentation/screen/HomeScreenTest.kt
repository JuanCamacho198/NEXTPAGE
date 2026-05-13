package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nextpage.presentation.viewmodel.HomeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = HomeViewModel()

    @Test
    fun allSectionsRender_whenDefaultState() {
        composeTestRule.setContent {
            HomeScreen(
                contentPadding = PaddingValues(0.dp),
                viewModel = viewModel,
                onNavigateToLibrary = {},
                onNavigateToHighlights = {},
                onNavigateToSettings = {},
                onBookSelected = { _, _, _ -> },
                onImportEpub = {},
                onImportPdf = {}
            )
        }

        // Header: NextPage title
        composeTestRule.onNodeWithText("NextPage").assertIsDisplayed()

        // Greeting: "Hola, Reader"
        composeTestRule.onNodeWithText("Hola, Reader").assertIsDisplayed()

        // TodaySummary: stat cards
        composeTestRule.onNodeWithText("Minutos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sesiones").assertIsDisplayed()
        composeTestRule.onNodeWithText("Progreso").assertIsDisplayed()

        // ContinueReading: empty state
        composeTestRule.onNodeWithText("Sin libro en curso").assertIsDisplayed()

        // MyBookshelf: section title
        composeTestRule.onNodeWithText("Mi estantería").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ver todo").assertIsDisplayed()

        // QuickAccess: icon labels
        composeTestRule.onNodeWithText("Import EPUB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resaltados").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ajustes").assertIsDisplayed()
    }
}
