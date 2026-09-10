package com.nextpage.domain.usecase

import com.nextpage.domain.model.Book
import com.nextpage.domain.model.BookImportRequest
import com.nextpage.domain.repository.LibraryRepository
import java.io.InputStream

class ImportEpubBookUseCase(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        request: BookImportRequest,
        inputStreamProvider: suspend () -> InputStream?
    ): Result<Book> {
        // A6 - import timing: one pair around the existing suspend call on a
        // background dispatcher; heavy work is unchanged. runCatching keeps JVM
        // unit tests (unmocked SystemClock) green.
        val start = runCatching { android.os.SystemClock.elapsedRealtime() }.getOrDefault(0L)
        val result = libraryRepository.importBookFromEpub(
            request = request,
            inputStreamProvider = inputStreamProvider
        )
        runCatching {
            val elapsed = android.os.SystemClock.elapsedRealtime() - start

            val exception = result.exceptionOrNull()
            val tags = mutableMapOf(
                "source" to "import",
                "format" to "epub",
                "platform" to "android"
            )
            if (exception != null) {
                // Truncated application error code only — never the exception
                // message, which can embed user content.
                tags["error_code"] = exception.javaClass.simpleName.take(8)
            }
            com.nextpage.debug.SentryMetrics.distribution(
                "book_import",
                com.nextpage.debug.SentryMetrics.bucketDurationMs(elapsed),
                tags
            )
        }
        return result
    }
}
