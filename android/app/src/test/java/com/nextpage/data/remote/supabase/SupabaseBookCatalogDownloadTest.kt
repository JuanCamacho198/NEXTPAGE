package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.data.remote.sync.StorageSyncRemoteDataSource
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [SupabaseBookCatalogSync.downloadRemoteBook] — the Drive
 * download path introduced for cross-device library sync.
 *
 * Covers:
 *  - D4: 401/403 refresh-then-retry-once in downloadWithRetry.
 *  - D5: local cover is sourced from the catalog `coverUrl`.
 *  - D6: a locally DELETE-marked book is never resurrected by a download.
 */
class SupabaseBookCatalogDownloadTest {

    private lateinit var fakeBookDao: FakeBookDao
    private lateinit var mockSession: SessionManager
    private lateinit var mockCatalog: SupabaseBookCatalogDataSource
    private lateinit var mockRemote: StorageSyncRemoteDataSource
    private lateinit var localDir: java.io.File
    private var refreshCalls = 0

    private val session = AuthSession(userId = "test-user", email = "test@example.com")

    @Before
    fun setUp() {
        fakeBookDao = FakeBookDao()
        mockSession = mockk(relaxed = true)
        mockCatalog = mockk(relaxed = true)
        mockRemote = mockk(relaxed = true)
        localDir = createTempDir()
        refreshCalls = 0

        coEvery { mockSession.getCurrentSession() } returns Result.success(session)
    }

    @After
    fun tearDown() {
        localDir.deleteRecursively()
        io.mockk.unmockkAll()
    }

    private fun newSync(): SupabaseBookCatalogSync = SupabaseBookCatalogSync(
        outboxDao = FakeSyncOutboxDao(),
        bookDao = fakeBookDao,
        sessionManager = mockSession,
        dataSource = mockCatalog,
        remoteDataSource = mockRemote,
        localBooksDir = localDir,
        driveTokenRefresher = {
            refreshCalls++
            Result.success("refreshed-token")
        }
    )

    // ─── D6: tombstone guard ───────────────────────────────────────

    @Test
    fun download_tombstonedBookRejectedWithoutCallingRemote() = runBlocking {
        fakeBookDao.upsert(sampleBook("dead-1").copy(deletedAtEpochMillis = 1234L))

        val result = newSync().downloadRemoteBook("dead-1")

        assertTrue(result.isFailure)
        assertEquals("BOOK_TOMBSTONED", (result.exceptionOrNull() as? AppError)?.code)
        coVerify(inverse = true) { mockRemote.download(any()) }
    }

    @Test
    fun download_rejectsCatalogRowWithoutRemoteFile() = runBlocking {
        coEvery { mockCatalog.listUserBooks("test-user") } returns listOf(
            catalogRow(id = "no-file", filePath = null)
        )

        val result = newSync().downloadRemoteBook("no-file")

        assertTrue(result.isFailure)
        assertEquals("BOOK_NOT_IN_CATALOG", (result.exceptionOrNull() as? AppError)?.code)
        coVerify(inverse = true) { mockRemote.download(any()) }
    }

    // ─── D4: refresh-then-retry on 401/403 ──────────────────────────────────

    @Test
    fun download_refreshesOnceThenRetriesAndSucceeds() = runBlocking {
        coEvery { mockCatalog.listUserBooks("test-user") } returns listOf(
            catalogRow(id = "auth-1", filePath = "/auth-1.epub")
        )
        var calls = 0
        coEvery { mockRemote.download("books/test-user/auth-1.epub") } answers {
            calls++
            if (calls == 1) {
                throw AppError(
                    category = ErrorCategory.AUTH,
                    code = "GOOGLE_DRIVE_UNAUTHORIZED",
                    message = "401",
                    component = "test"
                )
            }
            "file-bytes".toByteArray()
        }

        val result = newSync().downloadRemoteBook("auth-1")

        assertTrue(result.isSuccess)
        assertEquals(1, refreshCalls)
        coVerify(exactly = 2) { mockRemote.download("books/test-user/auth-1.epub") }
    }

    @Test
    fun download_surfacesFailureWhenRefreshFails() = runBlocking {
        coEvery { mockCatalog.listUserBooks("test-user") } returns listOf(
            catalogRow(id = "auth-2", filePath = "/auth-2.epub")
        )
        coEvery { mockRemote.download(any()) } throws AppError(
            category = ErrorCategory.AUTH,
            code = "GOOGLE_DRIVE_UNAUTHORIZED",
            message = "401",
            component = "test"
        )
        val sync = SupabaseBookCatalogSync(
            outboxDao = FakeSyncOutboxDao(),
            bookDao = fakeBookDao,
            sessionManager = mockSession,
            dataSource = mockCatalog,
            remoteDataSource = mockRemote,
            localBooksDir = localDir,
            driveTokenRefresher = {
                refreshCalls++
                Result.failure(AppError(ErrorCategory.AUTH, "REFRESH_FAILED", "no refresh token", "test"))
            }
        )

        val result = sync.downloadRemoteBook("auth-2")

        assertTrue(result.isFailure)
        assertEquals("DOWNLOAD_ERROR", (result.exceptionOrNull() as? AppError)?.code)
        assertEquals(1, refreshCalls)
    }

    // ─── D5: cover sourced from catalog coverUrl ──────────────────────────

    @Test
    fun download_persistsCoverFromCatalog() = runBlocking {
        coEvery { mockCatalog.listUserBooks("test-user") } returns listOf(
            catalogRow(id = "cover-1", filePath = "/cover-1.epub", coverUrl = "https://cdn.example/cover-1.jpg")
        )
        coEvery { mockRemote.download(any()) } returns "bytes".toByteArray()

        val result = newSync().downloadRemoteBook("cover-1")

        assertTrue(result.isSuccess)
        assertEquals("https://cdn.example/cover-1.jpg", fakeBookDao.getBookById("cover-1")?.coverPath)
    }

    // ─── Factory helpers ───────────────────────────────────────────────────

    private fun sampleBook(id: String): BookEntity = BookEntity(
        id = id,
        title = "Book $id",
        author = "Author",
        coverPath = null,
        filePath = "/$id.epub",
        format = "epub",
        totalPages = 100,
        description = "Description",
        updatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null
    )

    private fun catalogRow(id: String, filePath: String?, coverUrl: String? = null): UserBookRow =
        UserBookRow(
            id = id,
            userId = "test-user",
            title = "Book $id",
            author = "Author",
            format = "epub",
            contentHash = null,
            filePath = filePath,
            coverUrl = coverUrl,
            description = null,
            totalPages = 100,
            sourceDevice = "desktop",
            importedAt = "2026-07-12T12:00:00.000Z",
            updatedAt = "2026-07-12T12:00:00.000Z"
        )

    // ── Fake BookDao ───────────────────────────────────────────────────────
    private class FakeBookDao : BookDao {
        private val byId = mutableMapOf<String, BookEntity>()

        override suspend fun getBookById(bookId: String): BookEntity? = byId[bookId]

        override suspend fun upsert(book: BookEntity) { byId[book.id] = book }
        override suspend fun upsertAll(books: List<BookEntity>) { books.forEach { upsert(it) } }
        override fun observeAllBooks(): Flow<List<BookEntity>> = MutableStateFlow(byId.values.toList())
        override fun observeBookById(bookId: String): Flow<BookEntity?> = MutableStateFlow(byId[bookId])
        override fun observeAllBooksPaged(): androidx.paging.PagingSource<Int, BookEntity> =
            com.nextpage.testutil.FakePagingSource(emptyList())
        override suspend fun deleteBook(bookId: String, deletedAt: Long) {
            val existing = byId[bookId] ?: return
            byId[bookId] = existing.copy(deletedAtEpochMillis = deletedAt)
        }
        override suspend fun updateRating(bookId: String, rating: Int?) {
            val existing = byId[bookId] ?: return
            byId[bookId] = existing.copy(userRating = rating)
        }
        override suspend fun updateStatus(bookId: String, status: String?, updatedAt: Long) {
            val existing = byId[bookId] ?: return
            byId[bookId] = existing.copy(status = status, updatedAtEpochMillis = updatedAt)
        }
        override suspend fun startReading(bookId: String, updatedAt: Long) {}
        override suspend fun updateReadingProgress(bookId: String, progress: Float, updatedAt: Long) {}
        override suspend fun completeReading(bookId: String, updatedAt: Long) {}
        override suspend fun updateMetadata(
            bookId: String, title: String, author: String?, description: String?, coverPath: String?, updatedAt: Long
        ) {
            val existing = byId[bookId] ?: return
            byId[bookId] = existing.copy(
                title = title, author = author, description = description, coverPath = coverPath, updatedAtEpochMillis = updatedAt
            )
        }
        override suspend fun count(): Int = byId.size
    }

    private class FakeSyncOutboxDao : com.nextpage.data.local.dao.SyncOutboxDao {
        override suspend fun getPendingItems(): List<com.nextpage.data.local.entity.SyncOutboxEntity> = emptyList()
        override suspend fun insert(item: com.nextpage.data.local.entity.SyncOutboxEntity) {}
        override suspend fun deleteById(id: String) {}
        override suspend fun incrementRetryCount(id: String, error: String) {}
        override suspend fun pruneFailedItems(maxRetries: Int) {}
        override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)
    }
}
