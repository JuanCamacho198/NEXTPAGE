package com.nextpage.domain.repository

import com.nextpage.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibrary(): Flow<List<Book>>
}
