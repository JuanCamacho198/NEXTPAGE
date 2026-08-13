package com.nextpage.data.remote.supabase

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.ReadingSessionDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.ReadingSessionEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.model.AuthSession
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

/**
 * Unit tests for [SupabaseProgressSync] READING_SESSION outbox processing and
 * Realtime LWW application (REQ-reading-sessions-sync-3/4, SCEN-sync-3/4/6/7).
 */
class SupabaseProgressSyncTest {

    private lateinit var fakeBookDao: FakeBookDao
    private lateinit var fakeSessionDao: FakeReadingSessionDao
    private lateinit var fakeOutboxDao: FakeSyncOutboxDao
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockDataSource: SupabaseProgressDataSource
    private lateinit var sync: SupabaseProgressSync
    private val upsertedSessionSlot = CapturingSlot<ReadingSessionRow>()

    @Before
    fun setUp() {
        fakeBookDao = FakeBookDao()
        fakeSessionDao = FakeReadingSessionDao()
        fakeOutboxDao = FakeSyncOutboxDao()
        mockSessionManager = mockk(relaxed = true)
        mockDataSource = mockk(relaxed = true)

        coEvery { mockSessionManager.ensureFreshSession() } returns Result.success(
            AuthSession(userId = "test-user", email = "test@example.com")
        )

        sync = SupabaseProgressSync(
            outboxDao = fakeOutboxDao,
            bookDao = fakeBookDao,
            readingProgressDao = mockk(relaxed = true),
            bookmarkDao = mockk(relaxed = true),
            highlightDao = mockk(relaxed = true),
            readingSessionDao = fakeSessionDao,
            sessionManager = mockSessionManager,
            dataSource = mockDataSource
        )
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    private fun sessionOutboxItem(id: String, sessionId: String, bookId: String = "book-1"): SyncOutboxEntity {
        val payload = JSONObject()
            .put("id", sessionId)
            .put("bookId", bookId)
            .put("startTimeEpochMillis", 1_000_000L)
            .put("durationMinutes", 5)
            .put("date", 1_000_000L)
            .put("userId", "legacy-user")
            .put("updatedAtEpochMillis", 2_000_000L)
        return SyncOutboxEntity(
            id = id,
            entityType = SyncEntityType.READING_SESSION.name,
            entityId = bookId,
            operation = SyncOperation.UPDATE.name,
            payloadJson = payload.toString(),
            createdAtEpochMillis = 100L
        )
    }

    private fun remoteRow(id: String, bookId: String = "book-1", updatedAt: String = "2026-08-13T12:00:00.000Z"): ReadingSessionRow =
        ReadingSessionRow(
            id = id,
            userId = "test-user",
            bookId = bookId,
            startedAt = "2026-08-13T11:00:00.000Z",
            durationMinutes = 5,
            date = "2026-08-13T00:00:00.000Z",
            updatedAt = updatedAt
        )

    // ─── Outbox: upsert + delete (SCEN-sync-3) ────────────────────

    @Test
    fun processOutbox_readingSession_upsertsAndDeletesOutboxEntry() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        fakeOutboxDao.insert(sessionOutboxItem("outbox-1", "sess_abc"))

        coEvery { mockDataSource.upsertReadingSession(any()) } returns mockk()

        sync.startProcessing()
        Thread.sleep(500)

        assertEquals(0, fakeOutboxDao.getPendingItems().size)
        coVerify { mockDataSource.upsertReadingSession(match { it.id == "sess_abc" && it.userId == "test-user" }) }
    }

    @Test
    fun processOutbox_readingSession_usesFreshSessionUserId() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        // Payload recorded pre-auth with userId='' — the remote row must carry
        // the FRESH session's user id (pre-auth sessions merge into the account).
        fakeOutboxDao.insert(sessionOutboxItem("outbox-1", "sess_abc"))

        coEvery { mockDataSource.upsertReadingSession(any()) } returns mockk()

        sync.startProcessing()
        Thread.sleep(500)

        coVerify { mockDataSource.upsertReadingSession(match { it.userId == "test-user" }) }
    }

    @Test
    fun processOutbox_readingSession_repeatedRunIsIdempotent() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        // Two outbox items carrying the SAME deterministic session id (e.g. a
        // duplicate enqueue) → both upsert the same remote primary key, so the
        // remote table never gets duplicate rows (onConflict = "id").
        fakeOutboxDao.insert(sessionOutboxItem("outbox-1", "sess_dup"))
        fakeOutboxDao.insert(sessionOutboxItem("outbox-2", "sess_dup"))

        val upsertedIds = mutableListOf<String>()
        coEvery { mockDataSource.upsertReadingSession(capture(upsertedSessionSlot)) } answers {
            upsertedIds.add(upsertedSessionSlot.captured.id)
            mockk()
        }

        sync.startProcessing()
        Thread.sleep(500)

        assertEquals(0, fakeOutboxDao.getPendingItems().size)
        assertEquals(2, upsertedIds.size)
        // Same deterministic id sent both times → remote onConflict=id dedupes.
        assertEquals(listOf("sess_dup", "sess_dup"), upsertedIds)
    }

    @Test
    fun processOutbox_readingSession_failureIncrementsRetryAndPrunes() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        fakeOutboxDao.insert(sessionOutboxItem("outbox-1", "sess_retry"))

        coEvery { mockDataSource.upsertReadingSession(any()) } throws RuntimeException("Network error")

        // Failure 1 → retryCount 1, item retained.
        sync.startProcessing()
        Thread.sleep(500)
        assertEquals(1, fakeOutboxDao.getPendingItems().single().retryCount)

        // Failures 2-3 → retryCount 3 → pruneFailedItems(3) removes it.
        sync.startProcessing()
        Thread.sleep(500)
        sync.startProcessing()
        Thread.sleep(500)

        assertEquals(0, fakeOutboxDao.getPendingItems().size)
    }

    @Test
    fun processOutbox_readingSession_malformedPayloadIsDropped() = runBlocking {
        fakeOutboxDao.insert(
            SyncOutboxEntity(
                id = "outbox-bad",
                entityType = SyncEntityType.READING_SESSION.name,
                entityId = "book-1",
                operation = SyncOperation.UPDATE.name,
                payloadJson = "not json",
                createdAtEpochMillis = 100L
            )
        )

        sync.startProcessing()
        Thread.sleep(500)

        assertEquals(0, fakeOutboxDao.getPendingItems().size)
        coVerify(inverse = true) { mockDataSource.upsertReadingSession(any()) }
    }

    // ─── Realtime apply (SCEN-sync-6/7) ───────────────────────────

    @Test
    fun applyRemoteSession_skipsWhenBookMissingLocally() = runBlocking {
        // No local book "ghost" → FK guard skips (never crashes).
        val applied = sync.applyRemoteSession(remoteRow("sess_ghost", bookId = "ghost"))

        assertFalse(applied)
        assertEquals(0, fakeSessionDao.count())
    }

    @Test
    fun applyRemoteSession_newerRemoteWins() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        fakeSessionDao.upsert(
            ReadingSessionEntity(
                id = "sess_lww",
                bookId = "book-1",
                startTimeEpochMillis = 1L,
                durationMinutes = 5,
                date = 2L,
                userId = "test-user",
                updatedAtEpochMillis = 100L // local clock is OLD
            )
        )

        val applied = sync.applyRemoteSession(
            remoteRow(
                id = "sess_lww",
                updatedAt = "2026-08-13T12:00:00.000Z" // newer than local 100ms epoch
            )
        )

        assertTrue(applied)
        val local = fakeSessionDao.getById("sess_lww")
        assertEquals(5, local?.durationMinutes)
        assertTrue("remote clock must be stamped on the local row", (local?.updatedAtEpochMillis ?: 0L) > 100L)
    }

    @Test
    fun applyRemoteSession_olderRemoteIsSkipped() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))
        fakeSessionDao.upsert(
            ReadingSessionEntity(
                id = "sess_old",
                bookId = "book-1",
                startTimeEpochMillis = 1L,
                durationMinutes = 7,
                date = 2L,
                userId = "test-user",
                updatedAtEpochMillis = System.currentTimeMillis() // local clock is NEWER
            )
        )

        val applied = sync.applyRemoteSession(
            remoteRow(
                id = "sess_old",
                updatedAt = "2026-08-13T12:00:00.000Z"
            )
        )

        assertFalse(applied)
        val local = fakeSessionDao.getById("sess_old")
        assertEquals(7, local?.durationMinutes) // unchanged
    }

    @Test
    fun applyRemoteSession_insertsWhenNoLocalRow() = runBlocking {
        fakeBookDao.upsert(createSampleBook("book-1"))

        val applied = sync.applyRemoteSession(remoteRow("sess_new"))

        assertTrue(applied)
        assertEquals(1, fakeSessionDao.count())
        assertEquals("test-user", fakeSessionDao.getById("sess_new")?.userId)
    }

    // ─── Fakes (same inline pattern as SupabaseBookCatalogSyncTest) ──

    private fun createSampleBook(id: String): BookEntity = BookEntity(
        id = id,
        title = "Book $id",
        author = "Author $id",
        coverPath = null,
        filePath = "/$id.epub",
        format = "epub",
        totalPages = 100,
        updatedAtEpochMillis = 1000L,
        deletedAtEpochMillis = null
    )

    private class FakeBookDao : BookDao {
        private val booksState = MutableStateFlow<List<BookEntity>>(emptyList())

        override fun observeAllBooks(): Flow<List<BookEntity>> =
            booksState.map { books -> books.filter { it.deletedAtEpochMillis == null } }

        override fun observeReadingBooks(): Flow<List<BookEntity>> = booksState

        override suspend fun upsert(book: BookEntity) {
            booksState.value = booksState.value
                .filterNot { it.id == book.id }
                .plus(book)
        }

        override suspend fun upsertAll(books: List<BookEntity>) {
            books.forEach { upsert(it) }
        }

        override fun observeBookById(bookId: String): Flow<BookEntity?> =
            MutableStateFlow(booksState.value.firstOrNull { it.id == bookId })

        override suspend fun getBookById(bookId: String): BookEntity? =
            booksState.value.firstOrNull { it.id == bookId }

        override suspend fun deleteBook(bookId: String, deletedAt: Long) {
            booksState.value = booksState.value.map { book ->
                if (book.id == bookId) book.copy(updatedAtEpochMillis = deletedAt, deletedAtEpochMillis = deletedAt) else book
            }
        }

        override suspend fun deleteById(bookId: String) {
            booksState.value = booksState.value.filterNot { it.id == bookId }
        }

        override suspend fun updateRating(bookId: String, rating: Int?) = Unit
        override suspend fun updateStatus(bookId: String, status: String?, updatedAt: Long) = Unit
        override suspend fun startReading(bookId: String, updatedAt: Long) = Unit
        override suspend fun updateReadingProgress(bookId: String, progress: Float, updatedAt: Long) = Unit
        override suspend fun completeReading(bookId: String, updatedAt: Long) = Unit
        override suspend fun updateMetadata(bookId: String, title: String, author: String?, description: String?, coverPath: String?, updatedAt: Long) = Unit
        override suspend fun count(): Int = booksState.value.size
        override fun observeAllBooksPaged(): androidx.paging.PagingSource<Int, BookEntity> =
            com.nextpage.testutil.FakePagingSource(emptyList())
    }

    private class FakeReadingSessionDao : ReadingSessionDao {
        private val sessions = mutableMapOf<String, ReadingSessionEntity>()

        override suspend fun insert(session: ReadingSessionEntity) {
            sessions[session.id] = session
        }

        override fun getTotalMinutesForDate(date: Long): Flow<Int> = MutableStateFlow(0)
        override fun getTotalMinutesForDateAndUser(date: Long, userId: String): Flow<Int> = MutableStateFlow(0)
        override fun getTotalMinutes(): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCount(): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCountForDate(date: Long): Flow<Int> = MutableStateFlow(0)
        override fun getSessionCountForDateAndUser(date: Long, userId: String): Flow<Int> = MutableStateFlow(0)
        override fun observeSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>> =
            MutableStateFlow(sessions.values.filter { it.bookId == bookId })
        override suspend fun deleteSessionsForBook(bookId: String) {
            sessions.entries.removeAll { it.value.bookId == bookId }
        }

        override suspend fun count(): Int = sessions.size
        override suspend fun getDailyMinutes(): List<com.nextpage.data.local.model.DailyReadingMinutes> = emptyList()
        override suspend fun getDailyMinutesFromDate(startDate: Long): List<com.nextpage.data.local.model.DailyReadingMinutes> = emptyList()
        override suspend fun getById(id: String): ReadingSessionEntity? = sessions[id]
        override suspend fun getDailyMinutesForUser(userId: String?): List<com.nextpage.data.local.model.DailyReadingMinutes> = emptyList()

        suspend fun upsert(session: ReadingSessionEntity) {
            sessions[session.id] = session
        }
    }

    private class FakeSyncOutboxDao : SyncOutboxDao {
        private val items = mutableListOf<SyncOutboxEntity>()

        override suspend fun getPendingItems(): List<SyncOutboxEntity> =
            items.toList().sortedBy { it.createdAtEpochMillis }

        override suspend fun insert(item: SyncOutboxEntity) {
            items.add(item)
        }

        override suspend fun deleteById(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun incrementRetryCount(id: String, error: String) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    retryCount = items[index].retryCount + 1,
                    lastError = error
                )
            }
        }

        override suspend fun pruneFailedItems(maxRetries: Int) {
            items.removeAll { it.retryCount >= maxRetries }
        }

        override fun observePendingCount(): Flow<Int> =
            MutableStateFlow(items.size)
    }
}
