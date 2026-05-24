use super::LibraryRepository;
use chrono::Utc;
use std::fs;
use std::path::PathBuf;
use rusqlite::{params, OptionalExtension};
use crate::error::{AppError, AppResult};


    pub fn update_book_progress(repo: &LibraryRepository, book_id: &str, current_page: i32) -> AppResult<()> {
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

    pub fn save_book_file(repo: &LibraryRepository, id: &str, data: &[u8]) -> AppResult<()> {
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

        let file_path = file_path
            .ok_or_else(|| AppError::InvalidInput(format!("Book not found for id {}", book_id)))?;

        let path = PathBuf::from(file_path);
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(path, data)?;

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
