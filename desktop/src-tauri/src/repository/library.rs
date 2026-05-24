use rusqlite::params;

use crate::error::AppResult;
use crate::models::BookDto;

use super::LibraryRepository;

pub fn list_books(repo: &LibraryRepository) -> AppResult<Vec<BookDto>> {
    let mut statement = repo.connection.prepare(
        "SELECT id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at
         FROM books
         WHERE deleted_at IS NULL
           AND hidden_at IS NULL
         ORDER BY updated_at DESC",
    )?;

    let rows = statement.query_map([], |row| {
        Ok(BookDto {
            id: row.get(0)?,
            title: row.get(1)?,
            author: row.get(2)?,
            file_path: row.get(3)?,
            format: row.get(4)?,
            sync_status: row.get(5)?,
            current_page: row.get(6)?,
            total_pages: row.get(7)?,
            created_at: row.get(8)?,
            updated_at: row.get(9)?,
        })
    })?;

    let books = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(books)
}

pub fn upsert_book(repo: &LibraryRepository, book: BookDto) -> AppResult<()> {
    repo.connection.execute(
        "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, 1)
         ON CONFLICT(id) DO UPDATE SET
           title = excluded.title,
           author = excluded.author,
           file_path = excluded.file_path,
           format = excluded.format,
           sync_status = excluded.sync_status,
           current_page = excluded.current_page,
           total_pages = excluded.total_pages,
           updated_at = excluded.updated_at,
           version = version + 1",
        params![
            book.id,
            book.title,
            book.author,
            book.file_path,
            book.format,
            book.sync_status,
            book.current_page,
            book.total_pages,
            book.created_at,
            book.updated_at
        ],
    )?;
    Ok(())
}
