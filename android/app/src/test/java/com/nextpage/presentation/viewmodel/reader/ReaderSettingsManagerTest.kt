package com.nextpage.presentation.viewmodel.reader

import com.nextpage.data.session.ReaderPreferences
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingsManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `init loads persisted settings via ReaderPreferences`() {
        val customSettings = ReaderSettings(
            fontSize = FontSizePreset.XL,
            theme = ReaderTheme.SEPIA
        )
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns customSettings
        }

        val manager = ReaderSettingsManager(mockPrefs)

        verify { mockPrefs.load() }
        assertEquals("Font size should be XL", FontSizePreset.XL, manager.state.value.readerSettings.fontSize)
        assertEquals("Theme should be SEPIA", ReaderTheme.SEPIA, manager.state.value.readerSettings.theme)
    }

    @Test
    fun `updateReaderSettings updates state and persists`() {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns ReaderSettings()
        }
        val manager = ReaderSettingsManager(mockPrefs)
        val newSettings = ReaderSettings(
            fontSize = FontSizePreset.XXL,
            theme = ReaderTheme.LIGHT
        )

        manager.updateReaderSettings(newSettings)

        assertEquals(newSettings, manager.state.value.readerSettings)
        verify { mockPrefs.save(newSettings) }
    }

    @Test
    fun `onToggleSplitSettings flips boolean`() {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns ReaderSettings()
        }
        val manager = ReaderSettingsManager(mockPrefs)

        assertFalse("Split settings should start hidden", manager.state.value.showSplitSettings)

        manager.onToggleSplitSettings()
        assertTrue("Split settings should be visible after toggle", manager.state.value.showSplitSettings)

        manager.onToggleSplitSettings()
        assertFalse("Split settings should be hidden after second toggle", manager.state.value.showSplitSettings)
    }

    @Test
    fun `onUpdateCustomHighlightColor mutates palette and persists`() {
        val initialColors = listOf("#000000", "#111111", "#222222", "#333333", "#444444")
        val initialSettings = ReaderSettings(customHighlightColors = initialColors)
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns initialSettings
        }
        val manager = ReaderSettingsManager(mockPrefs)

        manager.onUpdateCustomHighlightColor(1, "#FF0000")

        val expectedColors = listOf("#000000", "#FF0000", "#222222", "#333333", "#444444")
        assertEquals(expectedColors, manager.state.value.readerSettings.customHighlightColors)

        val expectedSettings = ReaderSettings(customHighlightColors = expectedColors)
        verify { mockPrefs.save(expectedSettings) }
    }

    @Test
    fun `onUpdateCustomHighlightColor with out-of-bounds index is no-op`() {
        val initialColors = listOf("#000000", "#111111", "#222222", "#333333", "#444444")
        val initialSettings = ReaderSettings(customHighlightColors = initialColors)
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns initialSettings
        }
        val manager = ReaderSettingsManager(mockPrefs)

        manager.onUpdateCustomHighlightColor(10, "#FF0000")

        assertEquals(initialColors, manager.state.value.readerSettings.customHighlightColors)
    }

    @Test
    fun `onUpdateCustomHighlightColor with null initial palette is no-op`() {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns ReaderSettings()
        }
        val manager = ReaderSettingsManager(mockPrefs)

        // customHighlightColors is null (default), index 0 is out of bounds for empty list
        manager.onUpdateCustomHighlightColor(0, "#FF0000")

        assertNull(
            "customHighlightColors should remain null since default is null and no palette existed",
            manager.state.value.readerSettings.customHighlightColors
        )
    }

    @Test
    fun `onResetCustomHighlightColors resets palette`() {
        val initialColors = listOf("#000000", "#111111", "#222222", "#333333", "#444444")
        val initialSettings = ReaderSettings(customHighlightColors = initialColors)
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true) {
            every { load() } returns initialSettings
        }
        val manager = ReaderSettingsManager(mockPrefs)

        manager.onResetCustomHighlightColors()

        val defaultReaderSettings = ReaderSettings()
        assertEquals(
            defaultReaderSettings.customHighlightColors,
            manager.state.value.readerSettings.customHighlightColors
        )

        val expectedSaved = ReaderSettings(customHighlightColors = null)
        verify { mockPrefs.save(expectedSaved) }
    }
}
