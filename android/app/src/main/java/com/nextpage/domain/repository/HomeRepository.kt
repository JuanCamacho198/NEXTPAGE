package com.nextpage.domain.repository

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeRecentBooks(limit: Int = 5): Flow<List<Book>>
    fun observeCurrentBook(): Flow<Book?>
    fun observeCurrentBookProgress(): Flow<Float>
    fun observeDailyStats(): Flow<ReadingStats>
    suspend fun deleteBook(bookId: String): Result<Unit>
}