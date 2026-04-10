package com.nextpage.data.repository

import com.nextpage.data.epub.EpubMetadata
import com.nextpage.data.epub.EpubParserService
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.pdf.PdfMetadata
import com.nextpage.data.pdf.PdfParserService
import com.nextpage.data.storage.CoverStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

        override fun observeAllBooks(): Flow<List<BookEntity>> = booksState

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
    }

    private class FakeCoverStorage : CoverStorage {
        override suspend fun saveCover(bookId: String, coverBytes: ByteArray): Result<String> {
            return Result.success("/tmp/$bookId.jpg")
        }
    }
}
