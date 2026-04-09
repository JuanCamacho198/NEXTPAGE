package com.nextpage.data.repository

import com.nextpage.data.epub.EpubParserService
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.util.UUID

class LibraryRepositoryImpl(
    private val bookDao: BookDao,
    private val epubParserService: EpubParserService,
    private val coverStorage: CoverStorage
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> =
        bookDao.observeAllBooks().map { books -> books.map(BookEntity::toDomain) }

    override fun observeBookById(bookId: String): Flow<Book?> =
        bookDao.observeBookById(bookId).map { it?.toDomain() }

    override suspend fun importBookFromEpub(
        request: BookImportRequest,
        inputStreamProvider: suspend () -> InputStream?
    ): Result<Book> = runCatching {
        val inputStream = inputStreamProvider()
            ?: throw IllegalArgumentException("Unable to open EPUB stream")
        val metadata = epubParserService.extractMetadata(inputStream).getOrThrow()
        val now = System.currentTimeMillis()
        val bookId = UUID.randomUUID().toString()
        val coverPath = metadata.coverImageBytes
            ?.let { coverBytes ->
                coverStorage.saveCover(bookId = bookId, coverBytes = coverBytes).getOrNull()
            }

        val book = Book(
            id = bookId,
            title = metadata.title.ifBlank { request.fallbackTitle ?: "Untitled" },
            author = metadata.author,
            coverPath = coverPath,
            filePath = request.sourcePath,
            format = EPUB_FORMAT,
            updatedAtEpochMillis = now
        )

        bookDao.upsert(book.toEntity())
        book
    }

    private fun BookEntity.toDomain(): Book = Book(
        id = id,
        title = title,
        author = author,
        coverPath = coverPath,
        filePath = filePath,
        format = format,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun Book.toEntity(): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        coverPath = coverPath,
        filePath = filePath,
        format = format,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private companion object {
        const val EPUB_FORMAT = "epub"
    }
}
