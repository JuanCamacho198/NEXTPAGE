package com.nextpage.domain.usecase

import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository

class UpdateReadingProgressUseCase(
    private val readerRepository: ReaderRepository
) {
    suspend operator fun invoke(
        bookId: String,
        cfiLocation: String,
        percentage: Float,
        locatorJson: String? = null
    ) {
        val clamped = percentage.coerceIn(0f, 100f)
        val progress = ReadingProgress(
            id = "progress-$bookId",
            bookId = bookId,
            cfiLocation = cfiLocation,
            percentage = clamped,
            updatedAtEpochMillis = System.currentTimeMillis(),
            locatorJson = locatorJson
        )
        readerRepository.upsertProgress(progress)
        // Keep the book's reading_state in sync so the Home "Continue reading"
        // section (which filters on reading_state == "reading") shows it.
        readerRepository.updateBookReadingState(
            bookId = bookId,
            progressPercent = clamped,
            updatedAt = System.currentTimeMillis()
        )
    }
}
