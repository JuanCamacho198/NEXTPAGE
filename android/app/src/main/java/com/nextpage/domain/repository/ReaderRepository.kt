package com.nextpage.domain.repository

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ReaderRepository {
    fun observeProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun upsertProgress(progress: ReadingProgress)
    suspend fun getProgressForBook(bookId: String): ReadingProgress?

    fun observeAllHighlights(): Flow<List<Highlight>>
    fun observeHighlights(bookId: String): Flow<List<Highlight>>
    suspend fun upsertHighlight(highlight: Highlight)
    suspend fun getHighlightsForBook(bookId: String): List<Highlight>

    fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())

    fun observeAllBookmarks(): Flow<List<Bookmark>>
    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>
    suspend fun upsertBookmark(bookmark: Bookmark)
    suspend fun getBookmarksForBook(bookId: String): List<Bookmark>
}
