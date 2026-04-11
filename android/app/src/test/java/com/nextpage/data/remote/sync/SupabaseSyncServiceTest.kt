package com.nextpage.data.remote.sync

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.dao.SyncFileMappingDao
import com.nextpage.data.local.dao.SyncOutboxDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncFileMappingEntity
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import com.nextpage.domain.model.AuthSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseSyncServiceTest {

    @Test
    fun schedulePush_uploadsBookAndDeletesOutboxItem() = runTest {
        val root = Files.createTempDirectory("sync-test-push").toFile()
        val sourceFile = File(root, "source.epub").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val outboxDao = FakeOutboxDao(
            pending = mutableListOf(
                SyncOutboxEntity(
                    id = "o1",
                    entityType = SyncEntityType.BOOK.name,
                    entityId = "book-1",
                    operation = SyncOperation.CREATE.name,
                    payloadJson = "{}",
                    createdAtEpochMillis = 1L
                )
            )
        )
        val bookDao = FakeBookDao(
            books = mutableMapOf(
                "book-1" to BookEntity(
                    id = "book-1",
                    title = "Book 1",
                    author = null,
                    coverPath = null,
                    filePath = sourceFile.absolutePath,
                    format = "epub",
                    updatedAtEpochMillis = 10L
                )
            )
        )
        val mappingDao = FakeMappingDao()
        val remote = FakeRemoteDataSource()
        val service = SupabaseSyncService(
            outboxDao = outboxDao,
            bookDao = bookDao,
            mappingDao = mappingDao,
            sessionManager = FakeSessionManager(Result.success(AuthSession("user-1", "u@x.com"))),
            remoteDataSource = remote,
            localBooksDir = File(root, "books"),
            isEnabled = true
        )

        val result = service.schedulePush()

        assertTrue(result.isSuccess)
        assertEquals(listOf("books/user-1/book-1.epub"), remote.uploadedPaths)
        assertTrue(outboxDao.pending.isEmpty())
        assertEquals("books/user-1/book-1.epub", mappingDao.byRemote.keys.single())
    }

    @Test
    fun schedulePush_retriesTransientUploadError() = runTest {
        val root = Files.createTempDirectory("sync-test-retry").toFile()
        val sourceFile = File(root, "source.pdf").apply { writeBytes(byteArrayOf(9, 8, 7)) }

        val outboxDao = FakeOutboxDao(
            pending = mutableListOf(
                SyncOutboxEntity(
                    id = "o1",
                    entityType = SyncEntityType.BOOK.name,
                    entityId = "book-2",
                    operation = SyncOperation.UPDATE.name,
                    payloadJson = "{}",
                    createdAtEpochMillis = 1L
                )
            )
        )
        val bookDao = FakeBookDao(
            books = mutableMapOf(
                "book-2" to BookEntity(
                    id = "book-2",
                    title = "Book 2",
                    author = null,
                    coverPath = null,
                    filePath = sourceFile.absolutePath,
                    format = "pdf",
                    updatedAtEpochMillis = 10L
                )
            )
        )
        val remote = FakeRemoteDataSource(failUploadAttempts = 1)
        val service = SupabaseSyncService(
            outboxDao = outboxDao,
            bookDao = bookDao,
            mappingDao = FakeMappingDao(),
            sessionManager = FakeSessionManager(Result.success(AuthSession("user-1", "u@x.com"))),
            remoteDataSource = remote,
            localBooksDir = File(root, "books"),
            isEnabled = true
        )

        val result = service.schedulePush()

        assertTrue(result.isSuccess)
        assertEquals(2, remote.uploadAttemptCount)
    }

    @Test
    fun schedulePull_downloadsMissingLocalAndUpsertsBook() = runTest {
        val root = Files.createTempDirectory("sync-test-pull").toFile()
        val remotePath = "books/user-1/book-3.epub"
        val remoteBytes = byteArrayOf(4, 5, 6)

        val outboxDao = FakeOutboxDao()
        val bookDao = FakeBookDao()
        val mappingDao = FakeMappingDao()
        val remote = FakeRemoteDataSource(
            listed = listOf(remotePath),
            downloads = mutableMapOf(remotePath to remoteBytes)
        )
        val service = SupabaseSyncService(
            outboxDao = outboxDao,
            bookDao = bookDao,
            mappingDao = mappingDao,
            sessionManager = FakeSessionManager(Result.success(AuthSession("user-1", "u@x.com"))),
            remoteDataSource = remote,
            localBooksDir = File(root, "books"),
            isEnabled = true
        )

        val result = service.schedulePull()

        assertTrue(result.isSuccess)
        val upserted = bookDao.books["book-3"]
        assertTrue(upserted != null)
        val localFile = File(upserted!!.filePath)
        assertTrue(localFile.exists())
        assertTrue(localFile.readBytes().contentEquals(remoteBytes))
    }

    @Test
    fun schedulePush_thenSchedulePull_drainsOutboxAndRegistersLocalBook() = runTest {
        val root = Files.createTempDirectory("sync-test-roundtrip").toFile()
        val localBooksDir = File(root, "books").apply { mkdirs() }
        val localSource = File(localBooksDir, "book-1.epub").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val outboxDao = FakeOutboxDao(
            pending = mutableListOf(
                SyncOutboxEntity(
                    id = "o1",
                    entityType = SyncEntityType.BOOK.name,
                    entityId = "book-1",
                    operation = SyncOperation.CREATE.name,
                    payloadJson = "{}",
                    createdAtEpochMillis = 1L
                )
            )
        )
        val bookDao = FakeBookDao(
            books = mutableMapOf(
                "book-1" to BookEntity(
                    id = "book-1",
                    title = "Book 1",
                    author = "A",
                    coverPath = null,
                    filePath = localSource.absolutePath,
                    format = "epub",
                    updatedAtEpochMillis = 1L,
                    deletedAtEpochMillis = null
                )
            )
        )
        val mappingDao = FakeMappingDao()
        val remote = FakeRemoteDataSource()
        val service = SupabaseSyncService(
            outboxDao = outboxDao,
            bookDao = bookDao,
            mappingDao = mappingDao,
            sessionManager = FakeSessionManager(Result.success(AuthSession("user-1", "u1@test.com"))),
            remoteDataSource = remote,
            localBooksDir = localBooksDir,
            isEnabled = true
        )

        val pushResult = service.schedulePush()
        assertTrue(pushResult.isSuccess)
        assertEquals(listOf("books/user-1/book-1.epub"), remote.uploadedPaths)
        assertTrue(outboxDao.pending.isEmpty())

        assertTrue(localSource.delete())

        val pullResult = service.schedulePull()
        assertTrue(pullResult.isSuccess)

        val restored = bookDao.books["book-1"]
        assertTrue(restored != null)
        assertTrue(File(restored!!.filePath).exists())
        assertEquals("epub", restored.format)

        val mapping = mappingDao.getByRemotePath("books/user-1/book-1.epub")
        assertTrue(mapping != null)
        assertEquals("book-1", mapping!!.bookId)
    }

    @Test
    fun schedulePush_failsWithConfigErrorWhenDisabled() = runTest {
        val service = SupabaseSyncService(
            outboxDao = FakeOutboxDao(),
            bookDao = FakeBookDao(),
            mappingDao = FakeMappingDao(),
            sessionManager = FakeSessionManager(Result.success(AuthSession("u", null))),
            remoteDataSource = FakeRemoteDataSource(),
            localBooksDir = Files.createTempDirectory("sync-test-disabled").toFile(),
            isEnabled = false,
            diagnosticError = AppError(
                category = ErrorCategory.CONFIG_ERROR,
                code = "SUPABASE_CONFIG_MISSING_URL",
                message = "Missing URL",
                component = "SupabaseClientProvider"
            )
        )

        val result = service.schedulePush()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AppError
        assertEquals(ErrorCategory.CONFIG_ERROR, error.category)
    }

    private class FakeSessionManager(
        private val ensureResult: Result<AuthSession>
    ) : SessionManager {
        override suspend fun restoreSession(): Result<AuthSession?> = Result.success(null)
        override suspend fun getCurrentSession(): Result<AuthSession?> = Result.success(null)
        override suspend fun ensureFreshSession(): Result<AuthSession> = ensureResult
        override suspend fun signOutAll(): Result<Unit> = Result.success(Unit)
        override suspend fun setCurrentSession(session: AuthSession?): Result<Unit> = Result.success(Unit)
    }

    private class FakeRemoteDataSource(
        private val listed: List<String> = emptyList(),
        private val downloads: MutableMap<String, ByteArray> = mutableMapOf(),
        private val failUploadAttempts: Int = 0
    ) : StorageSyncRemoteDataSource {
        val uploadedPaths = mutableListOf<String>()
        var uploadAttemptCount: Int = 0

        override suspend fun upload(path: String, bytes: ByteArray) {
            uploadAttemptCount++
            if (uploadAttemptCount <= failUploadAttempts) {
                throw IOException("temporary")
            }
            uploadedPaths += path
            downloads[path] = bytes
        }

        override suspend fun download(path: String): ByteArray {
            return downloads[path] ?: error("Missing remote file for $path")
        }

        override suspend fun list(prefix: String): List<String> {
            return if (listed.isNotEmpty()) {
                listed
            } else {
                downloads.keys.filter { it.startsWith(prefix) }
            }
        }

    }

    private class FakeOutboxDao(
        val pending: MutableList<SyncOutboxEntity> = mutableListOf()
    ) : SyncOutboxDao {
        private val pendingCountFlow = MutableStateFlow(pending.size)

        override suspend fun getPendingItems(): List<SyncOutboxEntity> = pending.toList()

        override suspend fun insert(item: SyncOutboxEntity) {
            pending += item
            pendingCountFlow.value = pending.size
        }

        override suspend fun deleteById(id: String) {
            pending.removeAll { it.id == id }
            pendingCountFlow.value = pending.size
        }

        override suspend fun incrementRetryCount(id: String, error: String) {
            val idx = pending.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val item = pending[idx]
                pending[idx] = item.copy(retryCount = item.retryCount + 1, lastError = error)
            }
        }

        override suspend fun pruneFailedItems(maxRetries: Int) {
            pending.removeAll { it.retryCount >= maxRetries }
            pendingCountFlow.value = pending.size
        }

        override fun observePendingCount(): Flow<Int> = pendingCountFlow
    }

    private class FakeBookDao(
        val books: MutableMap<String, BookEntity> = mutableMapOf()
    ) : BookDao {
        override fun observeAllBooks(): Flow<List<BookEntity>> = flowOf(books.values.toList())

        override suspend fun upsert(book: BookEntity) {
            books[book.id] = book
        }

        override suspend fun upsertAll(books: List<BookEntity>) {
            books.forEach { book -> this.books[book.id] = book }
        }

        override fun observeBookById(bookId: String): Flow<BookEntity?> = flowOf(books[bookId])

        override suspend fun getBookById(bookId: String): BookEntity? = books[bookId]

        override suspend fun deleteBook(bookId: String, deletedAt: Long) {
            val existing = books[bookId] ?: return
            books[bookId] = existing.copy(
                deletedAtEpochMillis = deletedAt,
                updatedAtEpochMillis = deletedAt
            )
        }
    }

    private class FakeMappingDao : SyncFileMappingDao {
        val byRemote = mutableMapOf<String, SyncFileMappingEntity>()

        override suspend fun upsert(mapping: SyncFileMappingEntity) {
            byRemote[mapping.remotePath] = mapping
        }

        override suspend fun getByRemotePath(remotePath: String): SyncFileMappingEntity? = byRemote[remotePath]

        override suspend fun getByUserId(userId: String): List<SyncFileMappingEntity> {
            return byRemote.values.filter { it.userId == userId }
        }
    }
}
