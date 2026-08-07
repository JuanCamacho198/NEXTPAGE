use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use std::fs;
use std::path::PathBuf;
use tauri::Manager;

pub fn update_book_progress(
    repo: &LibraryRepository,
    book_id: &str,
    current_page: i32,
) -> AppResult<()> {
    if book_id.trim().is_empty() {
        return Err(AppError::MissingBookId);
    }

    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "UPDATE books SET current_page = ?1, updated_at = ?2, version = version + 1 WHERE id = ?3",
        params![current_page, now, book_id],
    )?;
    Ok(())
}

pub fn save_book_file(
    repo: &LibraryRepository,
    app: &tauri::AppHandle,
    id: &str,
    data: &[u8],
    title: Option<&str>,
    author: Option<&str>,
    format: Option<&str>,
) -> AppResult<()> {
    let book_id = id.trim();
    if book_id.is_empty() {
        return Err(AppError::MissingBookId);
    }

    let file_path: Option<String> = repo
        .connection
        .query_row(
            "SELECT file_path
                 FROM books
                 WHERE id = ?1 AND deleted_at IS NULL
                 LIMIT 1",
            params![book_id],
            |row| row.get(0),
        )
        .optional()?;

    let file_path = match file_path {
        Some(path) => path,
        None => {
            // Fresh install / app-data loss: create the book row under the
            // app-data books dir so recovery import never hard-fails.
            let books_dir = app.path().app_data_dir()?.join("books");
            std::fs::create_dir_all(&books_dir)?;
            let fmt = format.unwrap_or("epub").trim_start_matches('.');
            let dest = books_dir.join(format!("{}.{}", book_id, fmt));
            let now = Utc::now().to_rfc3339();
            repo.connection.execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, ?2, ?3, ?4, ?5, 'local', 0, 0, ?6, ?6, 1)
                 ON CONFLICT(id) DO UPDATE SET file_path = excluded.file_path, format = excluded.format, updated_at = excluded.updated_at, version = version + 1",
                params![book_id, title.unwrap_or(book_id), author.unwrap_or_default(), dest.to_string_lossy().to_string(), fmt, now],
            )?;
            dest.to_string_lossy().to_string()
        }
    };

    let path = PathBuf::from(&file_path);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    // Atomic file write: temp file then rename.
    let temp_path = path.with_extension("part");
    fs::write(&temp_path, data)?;
    fs::rename(&temp_path, &path)?;

    repo.connection.execute(
        "UPDATE books
             SET sync_status = 'synced', updated_at = ?1, version = version + 1
             WHERE id = ?2",
        params![Utc::now().to_rfc3339(), book_id],
    )?;

    Ok(())
}

pub fn hide_book_from_library(repo: &LibraryRepository, book_id: &str) -> AppResult<()> {
    let normalized_book_id = book_id.trim();
    if normalized_book_id.is_empty() {
        return Err(AppError::MissingBookId);
    }

    let existing_hidden_at: Option<Option<String>> = repo
        .connection
        .query_row(
            "SELECT hidden_at
                 FROM books
                 WHERE id = ?1 AND deleted_at IS NULL
                 LIMIT 1",
            params![normalized_book_id],
            |row| row.get(0),
        )
        .optional()?;

    match existing_hidden_at {
        None => {
            return Err(AppError::InvalidInput(format!(
                "Book not found for id {}",
                normalized_book_id
            )));
        }
        Some(Some(_)) => return Ok(()),
        Some(None) => {}
    }

    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "UPDATE books
             SET hidden_at = COALESCE(hidden_at, ?1), updated_at = ?1, version = version + 1
             WHERE id = ?2 AND deleted_at IS NULL",
        params![now, normalized_book_id],
    )?;

    Ok(())
}
