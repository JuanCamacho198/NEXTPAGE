package com.nextpage.data.repository

import com.nextpage.data.epub.EpubParserService
import com.nextpage.data.pdf.PdfParserService
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.ReadingProgressDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.ReadingProgressEntity
import com.nextpage.data.storage.CoverStorage
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.model.Book
import com.nextpage.domain.model.ReadingProgress
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.util.UUID

class LibraryRepositoryImpl(
    private val bookDao: BookDao,
    private val readingStatsDao: com.nextpage.data.local.dao.ReadingStatsDao,
    private val epubParserService: EpubParserService,
    private val pdfParserService: PdfParserService,
    private val coverStorage: CoverStorage,
    private val readingProgressDao: ReadingProgressDao
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> =
        bookDao.observeAllBooks().map { books -> books.map { it.toDomain() } }

    override fun observeBookById(bookId: String): Flow<Book?> =
        bookDao.observeBookById(bookId).map { it?.toDomain() }

    override fun observeProgressForBook(bookId: String): Flow<ReadingProgress?> =
        readingProgressDao.observeProgressForBook(bookId).map { it?.toDomain() }

    override fun observeTotalReadingTime(): Flow<Long> =
        readingStatsDao.observeTotalMinutesRead().map { it ?: 0L }

    override fun observeReadingTimeByBook(): Flow<Map<String, Long>> =
        readingStatsDao.observeAllStats().map { stats ->
            stats.associate { it.bookId to it.totalMinutesRead }
        }

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
            description = metadata.description,
            coverPath = coverPath,
            filePath = request.sourcePath,
            format = EPUB_FORMAT,
            totalPages = metadata.chapterCount.takeIf { it > 0 },
            updatedAtEpochMillis = now
        )

        bookDao.upsert(book.toEntity())
        book
    }

    override suspend fun importBookFromPdf(
        request: BookImportRequest,
        file: java.io.File
    ): Result<Book> = runCatching {
        val metadata = pdfParserService.extractMetadata(file).getOrThrow()
        val now = System.currentTimeMillis()
        val bookId = UUID.randomUUID().toString()

        val coverPath = metadata.coverBytes
            ?.let { coverStorage.saveCover(bookId = bookId, coverBytes = it).getOrNull() }

        val book = Book(
            id = bookId,
            title = metadata.title?.ifBlank { request.fallbackTitle ?: "Untitled" }
                ?: request.fallbackTitle ?: "Untitled",
            author = metadata.author,
            coverPath = coverPath,
            filePath = request.sourcePath,
            format = PDF_FORMAT,
            totalPages = metadata.pageCount,
            updatedAtEpochMillis = now
        )

        bookDao.upsert(book.toEntity())
        book
    }

    override suspend fun deleteBook(bookId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        bookDao.deleteBook(bookId, now)
        readingStatsDao.deleteForBook(bookId)
    }

    override suspend fun updateBookRating(bookId: String, rating: Int?) {
        bookDao.updateRating(bookId, rating)
    }

    private fun BookEntity.toDomain(): Book = Book(
        id = id,
        title = title,
        author = author,
        description = description,
        coverPath = coverPath,
        filePath = filePath,
        format = format,
        totalPages = totalPages,
        userRating = userRating,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        id = id,
        bookId = bookId,
        cfiLocation = cfiLocation,
        percentage = percentage,
        currentPage = currentPage,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun Book.toEntity(): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        description = description,
        coverPath = coverPath,
        filePath = filePath,
        format = format,
        totalPages = totalPages,
        userRating = userRating,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private companion object {
        const val EPUB_FORMAT = "epub"
        const val PDF_FORMAT = "pdf"
    }
}
