package com.nextpage.domain.usecase

import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import java.util.UUID

class UpdateReadingProgressUseCase(
    private val readerRepository: ReaderRepository
) {
    suspend operator fun invoke(bookId: String, cfiLocation: String, percentage: Float) {
        val progress = ReadingProgress(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            cfiLocation = cfiLocation,
            percentage = percentage,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        readerRepository.upsertProgress(progress)
    }
}
