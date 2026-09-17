use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::filename::{
    sanitize_extension, sanitize_file_stem, FALLBACK_BOOK_ID, MAX_BOOK_ID_CHARS,
};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use std::fs;
use std::path::{Path, PathBuf};
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

    let books_dir = app.path().app_data_dir()?.join("books");
    save_book_file_under(repo, &books_dir, book_id, data, title, author, format)
}

/// File-first, then DB: the destination is written (temp `.part` → rename)
/// BEFORE any row is committed, so a file-I/O failure can never leave a durable
/// `books` row whose file does not exist. The `books` writes then run in one
/// transaction; a DB failure after the file landed best-effort removes the file
/// for a brand-new row so no orphan remains. Split out from the app-handle
/// lookup so the ordering is unit-testable with a temp directory.
fn save_book_file_under(
    repo: &LibraryRepository,
    books_dir: &Path,
    book_id: &str,
    data: &[u8],
    title: Option<&str>,
    author: Option<&str>,
    format: Option<&str>,
) -> AppResult<()> {
    let existing_path: Option<String> = repo
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

    let fmt = format.unwrap_or("epub").trim_start_matches('.');
    let is_new_row = existing_path.is_none();
    let destination = match existing_path {
        Some(path) => PathBuf::from(path),
        None => {
            // Fresh install / app-data loss: create the book row under the
            // app-data books dir so recovery import never hard-fails.
            // The catalog id (`gutendex:2701`) is not a valid Windows path
            // segment: `:` starts an NTFS ADS. Sanitize the stem and keep the
            // persisted `format` untouched for the row below.
            let stem = sanitize_file_stem(book_id, MAX_BOOK_ID_CHARS, FALLBACK_BOOK_ID);
            let ext = sanitize_extension(Some(fmt));
            books_dir.join(format!("{stem}.{ext}"))
        }
    };

    write_book_file(&destination, data)?;

    if let Err(err) =
        commit_book_file_row(repo, book_id, &destination, title, author, fmt, is_new_row)
    {
        if is_new_row {
            let _ = fs::remove_file(&destination);
        }
        return Err(err);
    }

    Ok(())
}

/// Atomic file write: temp file then rename. A failed write or rename removes
/// the `.part` it created so `books/` never accumulates artifacts.
fn write_book_file(path: &Path, data: &[u8]) -> AppResult<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let temp_path = path.with_extension("part");
    if let Err(err) = fs::write(&temp_path, data) {
        let _ = fs::remove_file(&temp_path);
        return Err(err.into());
    }
    if let Err(err) = fs::rename(&temp_path, path) {
        let _ = fs::remove_file(&temp_path);
        return Err(err.into());
    }
    Ok(())
}

/// One transaction for the row so `sync_status = 'synced'` cannot be half
/// applied: either the row exists as synced, or nothing was committed.
fn commit_book_file_row(
    repo: &LibraryRepository,
    book_id: &str,
    destination: &Path,
    title: Option<&str>,
    author: Option<&str>,
    fmt: &str,
    is_new_row: bool,
) -> AppResult<()> {
    let now = Utc::now().to_rfc3339();
    let transaction = repo.connection.unchecked_transaction()?;
    if is_new_row {
        transaction.execute(
            "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, 'local', 0, 0, ?6, ?6, 1)
             ON CONFLICT(id) DO UPDATE SET file_path = excluded.file_path, format = excluded.format, updated_at = excluded.updated_at, version = version + 1",
            params![book_id, title.unwrap_or(book_id), author.unwrap_or_default(), destination.to_string_lossy().to_string(), fmt, now],
        )?;
    }
    transaction.execute(
        "UPDATE books
             SET sync_status = 'synced', updated_at = ?1, version = version + 1
             WHERE id = ?2",
        params![now, book_id],
    )?;
    transaction.commit()?;
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::new_repository;

    #[test]
    fn save_book_file_lands_a_sanitized_file_and_commits_a_synced_row() {
        let repo = new_repository();
        let dir = tempfile::tempdir().expect("tempdir");
        let books_dir = dir.path().join("books");

        save_book_file_under(
            &repo,
            &books_dir,
            "gutendex:2701",
            b"epub-bytes",
            Some("Moby Dick"),
            Some("Herman Melville"),
            Some("epub"),
        )
        .expect("save");

        let (file_path, sync_status): (String, String) = repo
            .connection
            .query_row(
                "SELECT file_path, sync_status FROM books WHERE id = ?1",
                params!["gutendex:2701"],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .expect("row");
        assert!(file_path.ends_with("gutendex2701.epub"), "{file_path}");
        assert_eq!(sync_status, "synced");
        let written = PathBuf::from(&file_path);
        assert!(written.exists(), "the file must exist at its committed path");
        assert_eq!(fs::read(&written).expect("read"), b"epub-bytes");
        assert!(
            !written.with_extension("part").exists(),
            "no .part artifact may remain after a successful rename"
        );
    }

    /// F2: a file-I/O failure must not leave a committed `books` row, so the
    /// library can never show a phantom book the user cannot open.
    #[test]
    fn save_book_file_leaves_no_row_when_the_file_write_fails() {
        let repo = new_repository();
        let dir = tempfile::tempdir().expect("tempdir");
        // A regular file where the books directory should be makes
        // `create_dir_all` fail deterministically.
        let books_dir = dir.path().join("books");
        fs::write(&books_dir, b"not a directory").expect("seed blocker file");

        let err = save_book_file_under(
            &repo,
            &books_dir,
            "gutendex:2701",
            b"epub-bytes",
            Some("Moby Dick"),
            Some("Herman Melville"),
            Some("epub"),
        )
        .expect_err("the file write must fail");
        assert!(matches!(err, AppError::Io(_)), "{err}");

        let rows: i64 = repo
            .connection
            .query_row(
                "SELECT COUNT(*) FROM books WHERE id = ?1",
                params!["gutendex:2701"],
                |row| row.get(0),
            )
            .expect("count");
        assert_eq!(rows, 0, "a failed file write must not leave a committed row");
    }
}
