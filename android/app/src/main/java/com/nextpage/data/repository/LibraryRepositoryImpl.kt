package com.nextpage.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

class LibraryRepositoryImpl(
    private val appContext: Context,
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

        // Primary: extract cover via Readium Publication.cover()
        // Fallback: use existing OPF-based cover bytes from metadata
        val coverBytes = extractReadiumCover(request.sourcePath)
            ?: metadata.coverImageBytes

        val coverPath = coverBytes
            ?.let { coverStorage.saveCover(bookId = bookId, coverBytes = it).getOrNull() }

        val book = Book(
            id = bookId,
            title = metadata.title.ifBlank { request.fallbackTitle ?: "Untitled" },
            author = metadata.author,
            description = metadata.description,
            coverPath = coverPath,
            filePath = request.sourcePath,
            format = EPUB_FORMAT,
            totalPages = metadata.estimatedPageCount,
            chapterCount = metadata.chapterCount.takeIf { it > 0 },
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
        // Clean up cover file first (idempotent — no-op if missing)
        coverStorage.deleteCover(bookId).getOrNull()
        val now = System.currentTimeMillis()
        bookDao.deleteBook(bookId, now)
        readingStatsDao.deleteForBook(bookId)
    }

    override suspend fun updateBookRating(bookId: String, rating: Int?) {
        bookDao.updateRating(bookId, rating)
    }

    /**
     * Attempts to extract cover image bytes using Readium's [Publication.cover]
     * extension (from [CoverService]).  Falls back to returning null, letting
     * the caller use the OPF-based [EpubMetadata.coverImageBytes].
     *
     * Opens the EPUB via [PublicationOpener] (same pattern as
     * [com.nextpage.presentation.viewmodel.ReaderViewModel.loadEpubBook]),
     * calls [publication.cover] which returns a [Bitmap?], then compresses
     * it to JPEG bytes.
     */
    private suspend fun extractReadiumCover(filePath: String): ByteArray? = runCatching {
        val file = File(filePath)
        if (!file.exists()) return@runCatching null

        val fileUri = Uri.fromFile(file).toString()
        val url = AbsoluteUrl(fileUri) ?: return@runCatching null
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)

        val asset = withContext(Dispatchers.IO) {
            assetRetriever.retrieve(url)
        }.getOrNull() ?: return@runCatching null

        val parser = DefaultPublicationParser(
            context = appContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
        val opener = PublicationOpener(parser)

        val publication = withContext(Dispatchers.IO) {
            opener.open(asset, allowUserInteraction = false)
        }.getOrNull() ?: return@runCatching null

        // publication.cover() returns a Bitmap? via CoverService
        publication.cover()
            ?.let { bitmap ->
                withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        stream.toByteArray()
                    }
                }
            }
    }.getOrNull()

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
        chapterCount = chapterCount,
        userRating = userRating,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private companion object {
        const val EPUB_FORMAT = "epub"
        const val PDF_FORMAT = "pdf"
    }
}
