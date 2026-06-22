package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingStats
import com.nextpage.domain.repository.HomeRepository
import com.nextpage.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(): HomeViewModel {
        val mockRepo = mockk<HomeRepository>(relaxed = true)
        // Wire flows to avoid NPE when collecting (all 5 needed for R9 combine)
        val dailyStatsFlow = MutableStateFlow(ReadingStats())
        val currentBookFlow = MutableStateFlow<Book?>(null)
        val progressFlow = MutableStateFlow(0f)
        val recentBooksFlow = MutableStateFlow<List<Book>>(emptyList())
        val allBooksFlow = MutableStateFlow<List<Book>>(emptyList())

        io.mockk.every { mockRepo.observeDailyStats() } returns dailyStatsFlow
        io.mockk.every { mockRepo.observeCurrentBook() } returns currentBookFlow
        io.mockk.every { mockRepo.observeCurrentBookProgress() } returns progressFlow
        io.mockk.every { mockRepo.observeRecentBooks(any()) } returns recentBooksFlow
        io.mockk.every { mockRepo.observeBooks() } returns allBooksFlow

        return HomeViewModel(mockRepo)
    }

    @Test
    fun homeUiState_sessionsToday_defaultsToZero() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("sessionsToday should default to 0", 0, state.sessionsToday)
    }

    @Test
    fun homeUiState_dailyProgressPercent_defaultsToZeroFloat() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("dailyProgressPercent should default to 0f", 0f, state.dailyProgressPercent, 0.001f)
    }

    @Test
    fun homeUiState_userName_defaultsToReader() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("userName should default to Reader", "Reader", state.userName)
    }

    @Test
    fun homeUiState_minutesReadToday_defaultsToZero() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("minutesReadToday should default to 0", 0, state.minutesReadToday)
    }

    @Test
    fun homeUiState_currentBook_defaultsToNull() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("currentBook should default to null", null, state.currentBook)
    }

    @Test
    fun homeUiState_recentBooks_defaultsToEmptyList() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertEquals("recentBooks should default to empty list", emptyList<Any>(), state.recentBooks)
    }

    @Test
    fun homeUiState_isLoading_defaultsToFalse() = runTest {
        val viewModel = createViewModel()
        // Wait a bit for init to collect (combine fires once all 5 flows have initial value)
        kotlinx.coroutines.delay(100)
        val state = viewModel.uiState.value
        assertEquals("isLoading should default to false", false, state.isLoading)
    }

    @Test
    fun combine_emitsCorrectState_whenAllFlowsEmit() = runTest {
        val mockRepo = mockk<HomeRepository>(relaxed = true)
        val dailyStatsFlow = MutableStateFlow(ReadingStats(minutesRead = 30, sessionCount = 2, dailyProgressPercent = 0.5f))
        val currentBookFlow = MutableStateFlow<Book?>(
            Book(id = "b1", title = "Test", author = "Author", coverPath = null, filePath = "/path", format = "epub", totalPages = 200, updatedAtEpochMillis = 1L)
        )
        val progressFlow = MutableStateFlow(0.75f)
        val recentBooksFlow = MutableStateFlow<List<Book>>(emptyList())
        val allBooksFlow = MutableStateFlow<List<Book>>(emptyList())

        io.mockk.every { mockRepo.observeDailyStats() } returns dailyStatsFlow
        io.mockk.every { mockRepo.observeCurrentBook() } returns currentBookFlow
        io.mockk.every { mockRepo.observeCurrentBookProgress() } returns progressFlow
        io.mockk.every { mockRepo.observeRecentBooks(any()) } returns recentBooksFlow
        io.mockk.every { mockRepo.observeBooks() } returns allBooksFlow

        val viewModel = HomeViewModel(mockRepo)
        kotlinx.coroutines.delay(100)

        val state = viewModel.uiState.value
        assertEquals("combine should set minutesReadToday", 30, state.minutesReadToday)
        assertEquals("combine should set sessionsToday", 2, state.sessionsToday)
        assertEquals("combine should set dailyProgressPercent", 0.5f, state.dailyProgressPercent, 0.001f)
        assertEquals("combine should set currentBook id", "b1", state.currentBook?.id)
        assertEquals("combine should set currentBookProgress", 0.75f, state.currentBookProgress, 0.001f)
        assertEquals("combine should set isLoading to false", false, state.isLoading)
    }
}
