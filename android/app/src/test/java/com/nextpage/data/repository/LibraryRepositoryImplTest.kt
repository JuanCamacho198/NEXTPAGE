package com.nextpage.data.repository

import com.nextpage.data.epub.EpubMetadata
import com.nextpage.data.epub.EpubParserService
import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
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
            )
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
    }

    private class FakeEpubParserService(
        private val result: Result<EpubMetadata>
    ) : EpubParserService {
        override suspend fun extractMetadata(inputStream: java.io.InputStream): Result<EpubMetadata> = result
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
    }
}
