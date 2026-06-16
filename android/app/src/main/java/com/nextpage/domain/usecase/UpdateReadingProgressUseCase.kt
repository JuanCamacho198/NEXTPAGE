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
        val progress = ReadingProgress(
            id = "progress-$bookId",
            bookId = bookId,
            cfiLocation = cfiLocation,
            percentage = percentage.coerceIn(0f, 100f),
            updatedAtEpochMillis = System.currentTimeMillis(),
            locatorJson = locatorJson
        )
        readerRepository.upsertProgress(progress)
    }
}
