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
        // Wire flows to avoid NPE when collecting
        val dailyStatsFlow = MutableStateFlow(ReadingStats())
        val currentBookFlow = MutableStateFlow<Book?>(null)
        val recentBooksFlow = MutableStateFlow<List<Book>>(emptyList())

        // Use every block to set up the mock
        io.mockk.every { mockRepo.observeDailyStats() } returns dailyStatsFlow
        io.mockk.every { mockRepo.observeCurrentBook() } returns currentBookFlow
        io.mockk.every { mockRepo.observeRecentBooks(any()) } returns recentBooksFlow

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
        // Wait a bit for init to collect
        kotlinx.coroutines.delay(100)
        val state = viewModel.uiState.value
        assertEquals("isLoading should default to false", false, state.isLoading)
    }
}
