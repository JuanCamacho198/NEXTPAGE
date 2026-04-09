package com.nextpage.data.repository

import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.ReaderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ReaderRepositoryImpl : ReaderRepository {
    override fun observeProgress(bookId: String): Flow<ReadingProgress?> = flowOf(null)
}
