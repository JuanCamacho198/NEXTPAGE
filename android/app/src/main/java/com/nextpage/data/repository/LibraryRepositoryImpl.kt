package com.nextpage.data.repository

import com.nextpage.domain.model.Book
import com.nextpage.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LibraryRepositoryImpl : LibraryRepository {
    override fun observeLibrary(): Flow<List<Book>> = flowOf(emptyList())
}
