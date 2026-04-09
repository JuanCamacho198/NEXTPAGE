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
    ): Result<Book> = libraryRepository.importBookFromEpub(
        request = request,
        inputStreamProvider = inputStreamProvider
    )
}
