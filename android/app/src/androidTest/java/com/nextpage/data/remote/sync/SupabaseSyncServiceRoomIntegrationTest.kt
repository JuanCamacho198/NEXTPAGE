package com.nextpage.data.remote.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextpage.data.local.AppDatabase
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.data.local.entity.SyncEntityType
import com.nextpage.data.local.entity.SyncOperation
import com.nextpage.data.local.entity.SyncOutboxEntity
import com.nextpage.data.session.SessionManager
import com.nextpage.domain.model.AuthSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SupabaseSyncServiceRoomIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun schedulePush_thenSchedulePull_drainsOutboxAndRegistersLocalBook() = runTest {
        val rootDir = File(context.cacheDir, "sync-room-it-${System.currentTimeMillis()}")
        val localBooksDir = File(rootDir, "books").apply { mkdirs() }
        val localSource = File(localBooksDir, "book-1.epub").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        database.bookDao().upsert(
            BookEntity(
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
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                id = "outbox-1",
                entityType = SyncEntityType.BOOK.name,
                entityId = "book-1",
                operation = SyncOperation.CREATE.name,
                payloadJson = "{}",
                createdAtEpochMillis = 1L
            )
        )

        val remote = FakeRemoteDataSource()
        val service = SupabaseSyncService(
            outboxDao = database.syncOutboxDao(),
            bookDao = database.bookDao(),
            mappingDao = database.syncFileMappingDao(),
            sessionManager = FixedSessionManager(AuthSession(userId = "user-1", email = "u1@test.com")),
            remoteDataSource = remote,
            localBooksDir = localBooksDir,
            isEnabled = true
        )

        val pushResult = service.schedulePush()
        assertTrue(pushResult.isSuccess)
        assertEquals(listOf("books/user-1/book-1.epub"), remote.uploadedPaths)
        assertTrue(database.syncOutboxDao().getPendingItems().isEmpty())

        assertTrue(localSource.delete())

        val pullResult = service.schedulePull()
        assertTrue(pullResult.isSuccess)

        val restored = database.bookDao().getBookById("book-1")
        assertTrue(restored != null)
        assertTrue(File(restored!!.filePath).exists())
        assertEquals("epub", restored.format)

        val mapping = database.syncFileMappingDao().getByRemotePath("books/user-1/book-1.epub")
        assertTrue(mapping != null)
        assertEquals("book-1", mapping!!.bookId)
    }

    private class FixedSessionManager(
        private val session: AuthSession
    ) : SessionManager {
        override suspend fun restoreSession(): Result<AuthSession?> = Result.success(session)
        override suspend fun getCurrentSession(): Result<AuthSession?> = Result.success(session)
        override suspend fun ensureFreshSession(): Result<AuthSession> = Result.success(session)
        override suspend fun signOutAll(): Result<Unit> = Result.success(Unit)
        override suspend fun setCurrentSession(session: AuthSession?): Result<Unit> = Result.success(Unit)
    }

    private class FakeRemoteDataSource : StorageSyncRemoteDataSource {
        val uploadedPaths = mutableListOf<String>()
        private val remoteFiles = linkedMapOf<String, ByteArray>()

        override suspend fun upload(path: String, bytes: ByteArray) {
            uploadedPaths += path
            remoteFiles[path] = bytes
        }

        override suspend fun download(path: String): ByteArray {
            return remoteFiles[path] ?: error("Missing remote file $path")
        }

        override suspend fun list(prefix: String): List<String> {
            return remoteFiles.keys.filter { it.startsWith(prefix) }
        }
    }
}
