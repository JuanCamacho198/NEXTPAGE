use chrono::Utc;
use rusqlite::{params, Connection, OptionalExtension};
use uuid::Uuid;

use crate::error::{AppError, AppResult};
use crate::models::{BookDto, ReadingProgressDto, SaveProgressInput};

pub struct LibraryRepository {
    connection: Connection,
}

impl LibraryRepository {
    pub fn new(connection: Connection) -> Self {
        Self { connection }
    }

    pub fn list_books(&self) -> AppResult<Vec<BookDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, title, author, file_path, format, created_at, updated_at
             FROM books
             WHERE deleted_at IS NULL
             ORDER BY updated_at DESC",
        )?;

        let rows = statement.query_map([], |row| {
            Ok(BookDto {
                id: row.get(0)?,
                title: row.get(1)?,
                author: row.get(2)?,
                file_path: row.get(3)?,
                format: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
            })
        })?;

        let books = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(books)
    }

    pub fn upsert_book(&self, book: BookDto) -> AppResult<()> {
        self.connection.execute(
            "INSERT INTO books (id, title, author, file_path, format, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 1)
             ON CONFLICT(id) DO UPDATE SET
               title = excluded.title,
               author = excluded.author,
               file_path = excluded.file_path,
               format = excluded.format,
               updated_at = excluded.updated_at,
               version = version + 1",
            params![
                book.id,
                book.title,
                book.author,
                book.file_path,
                book.format,
                book.created_at,
                book.updated_at
            ],
        )?;
        Ok(())
    }

    pub fn get_progress(&self, book_id: &str) -> AppResult<Option<ReadingProgressDto>> {
        if book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let mut statement = self.connection.prepare(
            "SELECT id, book_id, cfi_location, percentage, updated_at
             FROM reading_progress
             WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY updated_at DESC
             LIMIT 1",
        )?;

        let progress = statement
            .query_row(params![book_id], |row| {
                Ok(ReadingProgressDto {
                    id: row.get(0)?,
                    book_id: row.get(1)?,
                    cfi_location: row.get(2)?,
                    percentage: row.get(3)?,
                    updated_at: row.get(4)?,
                })
            })
            .optional()?;

        Ok(progress)
    }

    pub fn save_progress(&self, payload: SaveProgressInput) -> AppResult<()> {
        if payload.book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();

        let existing_id: Option<String> = self
            .connection
            .query_row(
                "SELECT id FROM reading_progress WHERE book_id = ?1 AND deleted_at IS NULL LIMIT 1",
                params![payload.book_id],
                |row| row.get(0),
            )
            .optional()?;

        match existing_id {
            Some(id) => {
                self.connection.execute(
                    "UPDATE reading_progress
                     SET cfi_location = ?1, percentage = ?2, updated_at = ?3, version = version + 1
                     WHERE id = ?4",
                    params![payload.cfi_location, payload.percentage, now, id],
                )?;
            }
            None => {
                self.connection.execute(
                    "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, version)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                    params![
                        Uuid::new_v4().to_string(),
                        payload.book_id,
                        payload.cfi_location,
                        payload.percentage,
                        now,
                        1
                    ],
                )?;
            }
        }

        Ok(())
    }

    pub fn upsert_progress(&self, progress: ReadingProgressDto) -> AppResult<()> {
        self.connection.execute(
            "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, 1)
             ON CONFLICT(id) DO UPDATE SET
               book_id = excluded.book_id,
               cfi_location = excluded.cfi_location,
               percentage = excluded.percentage,
               updated_at = excluded.updated_at,
               version = version + 1",
            params![
                progress.id,
                progress.book_id,
                progress.cfi_location,
                progress.percentage,
                progress.updated_at
            ],
        )?;
        Ok(())
    }
}
