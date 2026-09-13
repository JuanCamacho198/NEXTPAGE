package com.nextpage.data.repository

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.domain.sync.SyncSettleGate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Verifies the slice-6b orphan-sweep contract: only unreferenced files past the
 * grace window are deleted; in-flight `*.part` downloads are never candidates;
 * a sweep is skipped entirely while sync has not settled.
 */
class StorageRepositoryImplTest {

    private lateinit var root: File
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("nextpage-sweep-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun sweep_deletesUnreferencedFilesPastGrace() = runBlocking {
        val orphan = fileIn("catalog", "orphan.epub", ageMillis = 10 * 60 * 1000L)
        val repository = repository(settled = true, liveBooks = emptyList())

        val deleted = repository.sweepOrphanBookFiles()

        assertEquals(1, deleted)
        assertFalse(orphan.exists())
    }

    @Test
    fun sweep_excludesPartFiles() = runBlocking {
        val part = fileIn("catalog", "download.part", ageMillis = 10 * 60 * 1000L)
        val orphan = fileIn("catalog", "orphan.epub", ageMillis = 10 * 60 * 1000L)
        val repository = repository(settled = true, liveBooks = emptyList())

        val deleted = repository.sweepOrphanBookFiles()

        assertEquals("only the non-part orphan is swept", 1, deleted)
        assertTrue("in-flight .part files must never be swept", part.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun sweep_respectsGraceWindow() = runBlocking {
        val fresh = fileIn("pdfs", "fresh.pdf", ageMillis = 60 * 1000L)
        val repository = repository(settled = true, liveBooks = emptyList())

        val deleted = repository.sweepOrphanBookFiles()

        assertEquals(0, deleted)
        assertTrue("a file inside the grace window is skipped", fresh.exists())
    }

    @Test
    fun sweep_skipsWhenSyncNotSettled() = runBlocking {
        val orphan = fileIn("epubs", "orphan.epub", ageMillis = 10 * 60 * 1000L)
        val repository = repository(settled = false, liveBooks = emptyList())

        val deleted = repository.sweepOrphanBookFiles()

        assertEquals(0, deleted)
        assertTrue("no sweep while sync has not settled", orphan.exists())
    }

    @Test
    fun sweep_keepsFilesReferencedByLiveBooks() = runBlocking {
        val referenced = fileIn("catalog", "referenced.epub", ageMillis = 10 * 60 * 1000L)
        val repository = repository(
            settled = true,
            liveBooks = listOf(bookEntity(filePath = referenced.absolutePath))
        )

        val deleted = repository.sweepOrphanBookFiles()

        assertEquals(0, deleted)
        assertTrue("a file a live book references is not an orphan", referenced.exists())
    }

    private fun repository(settled: Boolean, liveBooks: List<BookEntity>): StorageRepositoryImpl {
        val bookDao = mockk<BookDao>(relaxed = true)
        every { bookDao.observeAllBooks() } returns flowOf(liveBooks)
        return StorageRepositoryImpl(
            appContext = mockk { every { filesDir } returns root },
            bookDao = bookDao,
            settleGate = FakeSettleGate(settled),
            nowMillis = { fixedNow }
        )
    }

    /** Creates an aged regular file under `root/<dir>`; [ageMillis] before [fixedNow]. */
    private fun fileIn(dir: String, name: String, ageMillis: Long): File {
        val directory = File(root, dir).apply { mkdirs() }
        return File(directory, name).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(fixedNow - ageMillis)
        }
    }

    private fun bookEntity(filePath: String) = BookEntity(
        id = "live-book",
        title = "Live Book",
        author = "Author",
        coverPath = null,
        filePath = filePath,
        format = "epub",
        updatedAtEpochMillis = 1L
    )

    private class FakeSettleGate(private val settled: Boolean) : SyncSettleGate {
        override suspend fun awaitSettled(): Boolean = settled
    }
}
