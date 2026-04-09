package com.nextpage.presentation.viewmodel

import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase
import com.nextpage.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelProgressTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateProgress_writesLocalProgressThroughRepository() = runTest {
        val repository = FakeReaderRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReaderViewModel(
            readerRepository = repository,
            updateReadingProgressUseCase = UpdateReadingProgressUseCase(repository),
            defaultBookId = "book-10",
            mainDispatcher = dispatcher
        )

        viewModel.updateProgress(
            bookId = "book-10",
            cfiLocation = "epubcfi(/6/2[chapter-1]!/4/1:0)",
            percentage = 25f
        )

        val saved = repository.lastUpserted
        assertNotNull(saved)
        assertEquals("progress-book-10", saved?.id)
        assertEquals("book-10", saved?.bookId)
        assertEquals("epubcfi(/6/2[chapter-1]!/4/1:0)", saved?.cfiLocation)
        assertEquals(25f, saved?.percentage)
        assertTrue((saved?.updatedAtEpochMillis ?: 0L) > 0L)
    }

    private class FakeReaderRepository : ReaderRepository {
        private val progressFlow = MutableStateFlow<ReadingProgress?>(null)
        var lastUpserted: ReadingProgress? = null

        override fun observeProgress(bookId: String): Flow<ReadingProgress?> = progressFlow

        override suspend fun upsertProgress(progress: ReadingProgress) {
            lastUpserted = progress
            progressFlow.value = progress
        }
    }
}
