package com.nextpage.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nextpage.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookById(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("UPDATE books SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :bookId")
    suspend fun deleteBook(bookId: String, deletedAt: Long)
}
