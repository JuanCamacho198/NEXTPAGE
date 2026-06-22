package com.nextpage.domain.repository

import androidx.paging.PagingData
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>

    fun observeLibraryPaged(): Flow<PagingData<Book>>

    fun observeBookById(bookId: String): Flow<Book?>

    fun observeProgressForBook(bookId: String): Flow<ReadingProgress?>

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

    suspend fun updateBookRating(bookId: String, rating: Int?)

    suspend fun updateBookStatus(bookId: String, status: String?): Result<Unit>

    suspend fun updateBookMetadata(
        bookId: String,
        title: String,
        author: String?,
        description: String?,
        coverPath: String?
    ): Result<Unit>
}
