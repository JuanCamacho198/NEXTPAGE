package com.nextpage.data.repository

import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderRepositoryImpl(
    private val readingProgressDao: ReadingProgressDao
) : ReaderRepository {
    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        readingProgressDao
            .observeProgressForBook(bookId)
            .map { progress -> progress?.toDomain() }

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}
