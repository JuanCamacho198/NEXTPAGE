package com.nextpage.data.repository

import com.nextpage.data.local.dao.BookDao
import com.nextpage.data.local.entity.BookEntity
import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl(
    private val bookDao: BookDao
) : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> =
        bookDao.observeAllBooks().map { books -> books.map(BookEntity::toDomain) }

    private fun BookEntity.toDomain(): Book = Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        format = format,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}
