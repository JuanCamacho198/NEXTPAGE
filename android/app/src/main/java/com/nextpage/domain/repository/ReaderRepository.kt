package com.nextpage.domain.repository

import com.nextpage.domain.model.Bookmark
import com.nextpage.domain.model.Highlight
import com.nextpage.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface ReaderRepository {
    fun observeProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun upsertProgress(progress: ReadingProgress)

    fun observeAllHighlights(): Flow<List<Highlight>>
    fun observeHighlights(bookId: String): Flow<List<Highlight>>
    suspend fun upsertHighlight(highlight: Highlight)

    fun observeAllBookmarks(): Flow<List<Bookmark>>
    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>
    suspend fun upsertBookmark(bookmark: Bookmark)
}
