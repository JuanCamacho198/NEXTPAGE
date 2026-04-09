package com.nextpage.di

import com.nextpage.data.repository.LibraryRepositoryImpl
import com.nextpage.data.repository.ReaderRepositoryImpl
import com.nextpage.domain.repository.LibraryRepository
import com.nextpage.domain.repository.ReaderRepository

class AppContainer {
    val libraryRepository: LibraryRepository = LibraryRepositoryImpl()
    val readerRepository: ReaderRepository = ReaderRepositoryImpl()
}
