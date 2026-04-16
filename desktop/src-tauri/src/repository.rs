use std::path::PathBuf;

use chrono::Utc;
use rusqlite::{params, Connection, OptionalExtension};
use tauri::Manager;
use uuid::Uuid;

use crate::error::{AppError, AppResult};
use crate::models::{
    BookDto, BookImportInput, BookmarkDto, HighlightDto, ReadingProgressDto, SaveBookmarkInput,
    SaveHighlightInput, SaveProgressInput,
};

pub struct LibraryRepository {
    connection: Connection,
}

impl LibraryRepository {
    pub fn new(connection: Connection) -> Self {
        Self { connection }
    }

    pub fn list_books(&self) -> AppResult<Vec<BookDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at
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

    pub fn upsert_book(&self, book: BookDto) -> AppResult<()> {
        self.connection.execute(
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

    pub fn import_book(&self, app: tauri::AppHandle, input: BookImportInput) -> AppResult<BookDto> {
        let source_path = PathBuf::from(&input.source_path);
        if !source_path.exists() {
            return Err(AppError::InvalidInput(format!(
                "Source file does not exist: {}",
                input.source_path
            )));
        }

        let app_data_dir = app
            .path()
            .app_data_dir()
            .map_err(|err| AppError::InvalidInput(err.to_string()))?;
        let books_dir = app_data_dir.join("books");
        std::fs::create_dir_all(&books_dir).map_err(|err| {
            AppError::InvalidInput(format!("Failed to create books directory: {}", err))
        })?;

        let file_name = source_path
            .file_name()
            .ok_or_else(|| AppError::InvalidInput("Invalid file name".to_string()))?
            .to_string_lossy()
            .to_string();

        let dest_path = books_dir.join(&file_name);
        if dest_path.exists() {
            let stem = source_path
                .file_stem()
                .ok_or_else(|| AppError::InvalidInput("Invalid file name".to_string()))?
                .to_string_lossy()
                .to_string();
            let ext = source_path
                .extension()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string();
            let unique_name = format!("{}_{}.{}", stem, Uuid::new_v4(), ext);
            let dest_path = books_dir.join(unique_name);
            std::fs::copy(&source_path, &dest_path)
                .map_err(|err| AppError::InvalidInput(format!("Failed to copy file: {}", err)))?;
        } else {
            std::fs::copy(&source_path, &dest_path)
                .map_err(|err| AppError::InvalidInput(format!("Failed to copy file: {}", err)))?;
        }

        let now = Utc::now().to_rfc3339();
        let title = input.title.unwrap_or_else(|| {
            source_path
                .file_stem()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string()
        });
        let author = input.author.unwrap_or_default();
        let format = input.format;

        let book = BookDto {
            id: Uuid::new_v4().to_string(),
            title,
            author,
            file_path: dest_path.to_string_lossy().to_string(),
            format,
            sync_status: "local".to_string(),
            current_page: 0,
            total_pages: 0,
            created_at: now.clone(),
            updated_at: now,
        };

        self.connection.execute(
            "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, 1)",
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

        Ok(book)
    }

    pub fn update_book_progress(&self, book_id: &str, current_page: i32) -> AppResult<()> {
        if book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();
        self.connection.execute(
            "UPDATE books SET current_page = ?1, updated_at = ?2, version = version + 1 WHERE id = ?3",
            params![current_page, now, book_id],
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

    pub fn list_highlights(&self, book_id: &str) -> AppResult<Vec<HighlightDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, created_at, updated_at
             FROM highlights
             WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY page ASC, rect_top ASC",
        )?;

        let rows = statement.query_map(params![book_id], |row| {
            Ok(HighlightDto {
                id: row.get(0)?,
                book_id: row.get(1)?,
                color: row.get(2)?,
                text: row.get(3)?,
                page: row.get(4)?,
                rect_left: row.get(5)?,
                rect_right: row.get(6)?,
                rect_top: row.get(7)?,
                rect_bottom: row.get(8)?,
                cfi: row.get(9)?,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
            })
        })?;

        let highlights = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(highlights)
    }

    pub fn save_highlight(&self, payload: SaveHighlightInput) -> AppResult<HighlightDto> {
        if payload.book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();
        let id = Uuid::new_v4().to_string();

        self.connection.execute(
            "INSERT INTO highlights (id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, 1)",
            params![
                id,
                payload.book_id,
                payload.color,
                payload.text,
                payload.page,
                payload.rect_left,
                payload.rect_right,
                payload.rect_top,
                payload.rect_bottom,
                payload.cfi,
                now,
                now
            ],
        )?;

        Ok(HighlightDto {
            id,
            book_id: payload.book_id,
            color: payload.color,
            text: payload.text,
            page: payload.page,
            rect_left: payload.rect_left,
            rect_right: payload.rect_right,
            rect_top: payload.rect_top,
            rect_bottom: payload.rect_bottom,
            cfi: payload.cfi,
            created_at: now.clone(),
            updated_at: now,
        })
    }

    pub fn delete_highlight(&self, id: &str) -> AppResult<()> {
        let now = Utc::now().to_rfc3339();
        self.connection.execute(
            "UPDATE highlights SET deleted_at = ?1, version = version + 1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }

    pub fn list_bookmarks(&self, book_id: &str) -> AppResult<Vec<BookmarkDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, book_id, page, position, title, created_at
             FROM bookmarks
             WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY page ASC, position ASC",
        )?;

        let rows = statement.query_map(params![book_id], |row| {
            Ok(BookmarkDto {
                id: row.get(0)?,
                book_id: row.get(1)?,
                page: row.get(2)?,
                position: row.get(3)?,
                title: row.get(4)?,
                created_at: row.get(5)?,
            })
        })?;

        let bookmarks = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(bookmarks)
    }

    pub fn save_bookmark(&self, payload: SaveBookmarkInput) -> AppResult<BookmarkDto> {
        if payload.book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();
        let id = Uuid::new_v4().to_string();

        self.connection.execute(
            "INSERT INTO bookmarks (id, book_id, page, position, title, created_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, 1)",
            params![
                id,
                payload.book_id,
                payload.page,
                payload.position,
                payload.title,
                now
            ],
        )?;

        Ok(BookmarkDto {
            id,
            book_id: payload.book_id,
            page: payload.page,
            position: payload.position,
            title: payload.title,
            created_at: now,
        })
    }

    pub fn delete_bookmark(&self, id: &str) -> AppResult<()> {
        let now = Utc::now().to_rfc3339();
        self.connection.execute(
            "UPDATE bookmarks SET deleted_at = ?1, version = version + 1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }
}
