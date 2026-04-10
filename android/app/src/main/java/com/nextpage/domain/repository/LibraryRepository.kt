package com.nextpage.domain.repository

import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>

    fun observeBookById(bookId: String): Flow<Book?>

    fun observeTotalReadingTime(): Flow<Long>

    fun observeReadingTimeByBook(): Flow<Map<String, Long>>

    suspend fun importBookFromEpub(
        request: BookImportRequest,
        inputStreamProvider: suspend () -> InputStream?
    ): Result<Book>

    suspend fun importBookFromPdf(
        request: BookImportRequest,
        file: java.io.File
    ): Result<Book>

    suspend fun deleteBook(bookId: String): Result<Unit>
}
