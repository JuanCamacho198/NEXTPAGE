package com.nextpage.presentation.viewmodel

import com.nextpage.data.session.ReaderPreferences
import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.FontSizePreset
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.LineHeightPreset
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.model.ReaderSettings
import com.nextpage.domain.model.ReaderTheme
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.repository.ReadingStatsData
import com.nextpage.domain.repository.ReadingStatsRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import android.app.Application
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `default settings are applied on init`() = runTest {
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            readerPreferences = null
        )

        val settings = viewModel.uiState.value.readerSettings
        assertEquals(FontSizePreset.M, settings.fontSize)
        assertEquals(ReaderTheme.DARK, settings.theme)
        assertEquals(LineHeightPreset.NORMAL, settings.lineHeight)
    }

    @Test
    fun `updateReaderSettings updates UIState`() = runTest {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true)
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            readerPreferences = mockPrefs
        )

        val newSettings = ReaderSettings(
            fontSize = FontSizePreset.XL,
            theme = ReaderTheme.SEPIA,
            lineHeight = LineHeightPreset.COMFORTABLE
        )

        viewModel.updateReaderSettings(newSettings)

        val state = viewModel.uiState.value
        assertEquals(FontSizePreset.XL, state.readerSettings.fontSize)
        assertEquals(ReaderTheme.SEPIA, state.readerSettings.theme)
        assertEquals(LineHeightPreset.COMFORTABLE, state.readerSettings.lineHeight)
    }

    @Test
    fun `updateReaderSettings persists via ReaderPreferences`() = runTest {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true)
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            readerPreferences = mockPrefs
        )

        val newSettings = ReaderSettings(
            fontSize = FontSizePreset.XXL,
            theme = ReaderTheme.LIGHT,
            lineHeight = LineHeightPreset.WIDE
        )

        viewModel.updateReaderSettings(newSettings)

        // Verify persistence was called with correct settings
        verify { mockPrefs.save(newSettings) }
    }

    @Test
    fun `settings are loaded from preferences on ViewModel init`() = runTest {
        val persistedSettings = ReaderSettings(
            fontSize = FontSizePreset.SM,
            theme = ReaderTheme.LIGHT,
            lineHeight = LineHeightPreset.TIGHT
        )
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true)
        every { mockPrefs.load() } returns persistedSettings

        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            readerPreferences = mockPrefs
        )

        val settings = viewModel.uiState.value.readerSettings
        assertEquals(FontSizePreset.SM, settings.fontSize)
        assertEquals(ReaderTheme.LIGHT, settings.theme)
        assertEquals(LineHeightPreset.TIGHT, settings.lineHeight)
    }

    @Test
    fun `updateReaderSettings with partial change preserves other settings`() = runTest {
        val mockPrefs = mockk<ReaderPreferences>(relaxed = true)
        val viewModel = ReaderViewModel(
            application = mockk<Application>(relaxed = true),
            readerRepository = FakeReaderRepository(),
            readingStatsRepository = FakeReadingStatsRepository(),
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(FakeReaderRepository()),
            defaultBookId = null,
            readerPreferences = mockPrefs
        )

        // Change only font size — copy with defaults for the rest
        viewModel.updateReaderSettings(
            ReaderSettings(
                fontSize = FontSizePreset.XXL,
                theme = ReaderTheme.DARK,
                lineHeight = LineHeightPreset.NORMAL
            )
        )

        val state = viewModel.uiState.value
        assertEquals(FontSizePreset.XXL, state.readerSettings.fontSize)
        assertEquals(ReaderTheme.DARK, state.readerSettings.theme)
        assertEquals(LineHeightPreset.NORMAL, state.readerSettings.lineHeight)
    }

    private class FakeReaderRepository : ReaderRepository {
        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = MutableStateFlow(null)
        override suspend fun upsertProgress(progress: ReadingProgress) = Unit
        override suspend fun updateBookReadingState(bookId: String, progressPercent: Float, updatedAt: Long) = Unit
        override fun observeAllHighlights(): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override fun observeAllHighlightsPaged(): Flow<androidx.paging.PagingData<Highlight>> =
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
        override fun observeHighlights(bookId: String): Flow<List<Highlight>> = MutableStateFlow(emptyList())
        override suspend fun upsertHighlight(highlight: Highlight) = Unit
        override fun observeAllBookmarks(): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
        override suspend fun upsertBookmark(bookmark: Bookmark) = Unit
        override suspend fun getProgressForBook(bookId: String): com.nextpage.domain.model.ReadingProgress? = null
        override suspend fun getHighlightsForBook(bookId: String): List<com.nextpage.domain.model.Highlight> = emptyList()
        override suspend fun getBookmarksForBook(bookId: String): List<com.nextpage.domain.model.Bookmark> = emptyList()
    }

    private class FakeReadingStatsRepository : ReadingStatsRepository {
        override fun observeStats(bookId: String): Flow<ReadingStatsData?> = MutableStateFlow(null)
        override fun observeTotalTime(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun updateReadingTime(bookId: String, additionalMinutes: Long) = Unit
        override suspend fun deleteStats(bookId: String) = Unit
        override fun observeBookStats(): kotlinx.coroutines.flow.Flow<List<com.nextpage.domain.repository.ReadingStatsData>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override suspend fun getDailyActivity(): List<com.nextpage.domain.model.DailyReadingActivity> = emptyList()
    }
}
