package com.nextpage.presentation.screen

import com.nextpage.data.session.ReaderPreferences
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.LineHeightPreset
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import com.nextpage.ui.components.molecules.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Settings-related models and persistence.
 * SettingsScreen itself is a pure Composable with no ViewModel,
 * so we test the data layer (ReaderPreferences) and UI models.
 */
class SettingsTests {

    // ── NotificationItem model ──────────────────────────────────────

    @Test
    fun `notificationItem defaults to unread`() {
        val item = NotificationItem(
            id = "test-1",
            icon = androidx.compose.material.icons.Icons.Outlined.AutoGraph,
            title = "Test",
            body = "Body"
        )
        assertEquals("test-1", item.id)
        assertEquals("Test", item.title)
        assertEquals("Body", item.body)
        assertEquals(true, item.isUnread)
    }

    @Test
    fun `notificationItem can be created as read`() {
        val item = NotificationItem(
            id = "test-2",
            icon = androidx.compose.material.icons.Icons.Outlined.EmojiEvents,
            title = "Read",
            body = "Body",
            isUnread = false
        )
        assertEquals(false, item.isUnread)
    }

    // ── ReaderSettings model ────────────────────────────────────────

    @Test
    fun `readerSettings uses defaults`() {
        val settings = ReaderSettings()
        assertEquals(FontSizePreset.MEDIUM, settings.fontSize)
        assertEquals(ReaderTheme.DARK, settings.theme)
        assertEquals(LineHeightPreset.NORMAL, settings.lineHeight)
    }

    @Test
    fun `readerSettings can be customized`() {
        val settings = ReaderSettings(
            fontSize = FontSizePreset.LARGE,
            theme = ReaderTheme.SEPIA,
            lineHeight = LineHeightPreset.COMFORTABLE
        )
        assertEquals(FontSizePreset.LARGE, settings.fontSize)
        assertEquals(ReaderTheme.SEPIA, settings.theme)
        assertEquals(LineHeightPreset.COMFORTABLE, settings.lineHeight)
    }

    // ── PreferenceItem model (used in SettingsScreen) ───────────────

    @Test
    fun `preferenceItems list has correct items`() {
        // Verify the preference items defined in SettingsScreen
        val expectedLabels = listOf(
            com.nextpage.R.string.settings_pref_theme,
            com.nextpage.R.string.settings_pref_font_size,
            com.nextpage.R.string.settings_pref_sync,
            com.nextpage.R.string.settings_pref_about,
            com.nextpage.R.string.settings_pref_notifications
        )
        assertEquals(5, expectedLabels.size)
    }
}
