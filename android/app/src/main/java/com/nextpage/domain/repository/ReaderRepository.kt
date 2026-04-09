package com.nextpage.domain.repository

import com.nextpage.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface ReaderRepository {
    fun observeProgress(bookId: String): Flow<ReadingProgress?>
}
