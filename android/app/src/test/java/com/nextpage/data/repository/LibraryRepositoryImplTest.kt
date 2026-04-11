package com.nextpage.data.repository

import com.nextpage.data.epub.EpubMetadata
import com.nextpage.data.epub.EpubParserService
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.ReadingStatsDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.ReadingStatsEntity
import com.nextpage.data.pdf.PdfMetadata
import com.nextpage.data.pdf.PdfParserService
import com.nextpage.data.storage.CoverStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LibraryRepositoryImplTest {
    @Test
    fun importBookFromEpub_persistsMetadataIntoBookDao() = runBlocking {
        val fakeDao = FakeBookDao()
        val repository = LibraryRepositoryImpl(
            bookDao = fakeDao,
            readingStatsDao = FakeReadingStatsDao(),
            epubParserService = FakeEpubParserService(
                Result.success(
                    EpubMetadata(
                        title = "Domain Driven Design",
                        author = "Eric Evans",
                        coverImageBytes = null
                    )
                )
            ),
            pdfParserService = FakePdfParserService(Result.success(PdfMetadata("PDF Book", null, 10, 100))),
            coverStorage = FakeCoverStorage()
        )

        val result = repository.importBookFromEpub(
            request = com.nextpage.domain.model.BookImportRequest(
                sourcePath = "content://books/ddd.epub",
                fallbackTitle = "ddd.epub"
            ),
            inputStreamProvider = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        )

        assertTrue(result.isSuccess)
        val inserted = fakeDao.lastUpserted
        assertNotNull(inserted)
        assertEquals("Domain Driven Design", inserted?.title)
        assertEquals("Eric Evans", inserted?.author)
        assertEquals("content://books/ddd.epub", inserted?.filePath)
        assertEquals("epub", inserted?.format)
        assertEquals(null, inserted?.coverPath)
    }

    @Test
    fun importBookFromPdf_persistsMetadataIntoBookDao() = runBlocking {
        val fakeDao = FakeBookDao()
        val repository = LibraryRepositoryImpl(
            bookDao = fakeDao,
            readingStatsDao = FakeReadingStatsDao(),
            epubParserService = FakeEpubParserService(Result.failure(IllegalStateException("Should not be called"))),
            pdfParserService = FakePdfParserService(
                Result.success(
                    PdfMetadata(
                        title = "PDF Guide",
                        author = "John Doe",
                        pageCount = 250,
                        fileSizeBytes = 2048000L
                    )
                )
            ),
            coverStorage = FakeCoverStorage()
        )

        val result = repository.importBookFromPdf(
            request = com.nextpage.domain.model.BookImportRequest(
                sourcePath = "content://books/guide.pdf",
                fallbackTitle = "guide.pdf"
            ),
            file = java.io.File("guide.pdf")
        )

        assertTrue(result.isSuccess)
        val inserted = fakeDao.lastUpserted
        assertNotNull(inserted)
        assertEquals("PDF Guide", inserted?.title)
        assertEquals("John Doe", inserted?.author)
        assertEquals("content://books/guide.pdf", inserted?.filePath)
        assertEquals("pdf", inserted?.format)
        assertEquals(null, inserted?.coverPath)
    }

    @Test
    fun importBookFromPdf_returnsFailureWhenMetadataExtractionFails() = runBlocking {
        val fakeDao = FakeBookDao()
        val repository = LibraryRepositoryImpl(
            bookDao = fakeDao,
            readingStatsDao = FakeReadingStatsDao(),
            epubParserService = FakeEpubParserService(Result.failure(IllegalStateException("Should not be called"))),
            pdfParserService = FakePdfParserService(Result.failure(IllegalStateException("Invalid PDF"))),
            coverStorage = FakeCoverStorage()
        )

        val result = repository.importBookFromPdf(
            request = com.nextpage.domain.model.BookImportRequest(
                sourcePath = "content://books/broken.pdf",
                fallbackTitle = "broken.pdf"
            ),
            file = java.io.File("broken.pdf")
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun observeTotalReadingTime_mapsNullToZero() = runBlocking {
        val fakeDao = FakeBookDao()
        val fakeReadingStatsDao = FakeReadingStatsDao()
        val repository = LibraryRepositoryImpl(
            bookDao = fakeDao,
            readingStatsDao = fakeReadingStatsDao,
            epubParserService = FakeEpubParserService(Result.failure(IllegalStateException("unused"))),
            pdfParserService = FakePdfParserService(Result.failure(IllegalStateException("unused"))),
            coverStorage = FakeCoverStorage()
        )

        fakeReadingStatsDao.totalMinutesState.value = null
        assertEquals(0L, repository.observeTotalReadingTime().first())

        fakeReadingStatsDao.totalMinutesState.value = 55L
        assertEquals(55L, repository.observeTotalReadingTime().first())
    }

    @Test
    fun observeLibrary_excludesSoftDeletedBooks() = runBlocking {
        val fakeDao = FakeBookDao()
        val repository = LibraryRepositoryImpl(
            bookDao = fakeDao,
            readingStatsDao = FakeReadingStatsDao(),
            epubParserService = FakeEpubParserService(Result.failure(IllegalStateException("unused"))),
            pdfParserService = FakePdfParserService(Result.failure(IllegalStateException("unused"))),
            coverStorage = FakeCoverStorage()
        )

        fakeDao.upsert(
            BookEntity(
                id = "active-book",
                title = "Active",
                author = "Author",
                coverPath = null,
                filePath = "/active.epub",
                format = "epub",
                updatedAtEpochMillis = 10L,
                deletedAtEpochMillis = null
            )
        )
        fakeDao.upsert(
            BookEntity(
                id = "deleted-book",
                title = "Deleted",
                author = "Author",
                coverPath = null,
                filePath = "/deleted.epub",
                format = "epub",
                updatedAtEpochMillis = 20L,
                deletedAtEpochMillis = null
            )
        )
        fakeDao.deleteBook("deleted-book", deletedAt = 30L)

        val books = repository.observeLibrary().first()
        assertEquals(1, books.size)
        assertEquals("active-book", books.first().id)

        val deletedById = repository.observeBookById("deleted-book").firstOrNull()
        assertEquals("deleted-book", deletedById?.id)
    }

    private class FakeEpubParserService(
        private val result: Result<EpubMetadata>
    ) : EpubParserService {
        override suspend fun extractMetadata(inputStream: java.io.InputStream): Result<EpubMetadata> = result
    }

    private class FakePdfParserService(
        private val result: Result<PdfMetadata>
    ) : PdfParserService {
        override suspend fun extractMetadata(file: java.io.File): Result<PdfMetadata> = result
        override fun getPageCount(file: java.io.File): Int = 0
    }

    private class FakeBookDao : BookDao {
        private val booksState = MutableStateFlow<List<BookEntity>>(emptyList())
        var lastUpserted: BookEntity? = null

        override fun observeAllBooks(): Flow<List<BookEntity>> =
            booksState.map { books -> books.filter { it.deletedAtEpochMillis == null } }

        override suspend fun upsert(book: BookEntity) {
            lastUpserted = book
            booksState.value = booksState.value
                .filterNot { it.id == book.id }
                .plus(book)
                .sortedByDescending { it.updatedAtEpochMillis }
        }

        override suspend fun upsertAll(books: List<BookEntity>) {
            books.forEach { upsert(it) }
        }

        override fun observeBookById(bookId: String): Flow<BookEntity?> =
            MutableStateFlow(booksState.value.firstOrNull { it.id == bookId })

        override suspend fun getBookById(bookId: String): BookEntity? {
            return booksState.value.firstOrNull { it.id == bookId }
        }

        override suspend fun deleteBook(bookId: String, deletedAt: Long) {
            booksState.value = booksState.value.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        updatedAtEpochMillis = deletedAt,
                        deletedAtEpochMillis = deletedAt
                    )
                } else {
                    book
                }
            }
        }
    }

    private class FakeCoverStorage : CoverStorage {
        override suspend fun saveCover(bookId: String, coverBytes: ByteArray): Result<String> {
            return Result.success("/tmp/$bookId.jpg")
        }
    }

    private class FakeReadingStatsDao : ReadingStatsDao {
        val totalMinutesState = MutableStateFlow<Long?>(0L)

        override fun observeStatsForBook(bookId: String): Flow<ReadingStatsEntity?> = MutableStateFlow(null)

        override fun observeAllStats(): Flow<List<ReadingStatsEntity>> = MutableStateFlow(emptyList())

        override suspend fun upsert(stats: ReadingStatsEntity) = Unit

        override fun observeTotalMinutesRead(): Flow<Long?> = totalMinutesState

        override suspend fun deleteForBook(bookId: String) = Unit
    }
}
