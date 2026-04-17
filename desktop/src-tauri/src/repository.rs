use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

use chrono::Utc;
use rusqlite::{params, Connection, OptionalExtension};
use serde_json::Value;
use tauri::Manager;
use uuid::Uuid;

use crate::error::{AppError, AppResult};
use crate::models::{
    AppSettingDto, BookCoverDto, BookDeleteInput, BookDto, BookImportInput, BookmarkDto,
    HighlightDto, IndexBookTextInput, LibraryBookDto, ReadingProgressDto, ReadingSessionInput,
    ReadingStatsSummaryDto, SaveBookmarkInput, SaveHighlightInput, SaveProgressInput,
    SearchBookTextInput, SearchBookTextResponse, SearchResultDto,
};

const MAX_SETTING_BATCH: usize = 100;
const MAX_SEARCH_PAGE_SIZE: i64 = 200;
const DEFAULT_SEARCH_PAGE_SIZE: i64 = 50;

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

    pub fn get_settings(&self) -> AppResult<Vec<AppSettingDto>> {
        let mut statement = self.connection.prepare(
            "SELECT key, value_json, updated_at
             FROM app_settings
             ORDER BY key ASC",
        )?;

        let rows = statement.query_map([], |row| {
            Ok(AppSettingDto {
                key: row.get(0)?,
                value_json: row.get(1)?,
                updated_at: row.get(2)?,
            })
        })?;

        let settings = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(settings)
    }

    pub fn upsert_settings(&mut self, settings: Vec<AppSettingDto>) -> AppResult<()> {
        if settings.len() > MAX_SETTING_BATCH {
            return Err(AppError::InvalidInput(format!(
                "Too many settings in one request (max {})",
                MAX_SETTING_BATCH
            )));
        }

        for setting in &settings {
            Self::validate_setting(setting)?;
        }

        let now = Utc::now().to_rfc3339();
        let tx = self.connection.transaction()?;
        for setting in settings {
            tx.execute(
                "INSERT INTO app_settings (key, value_json, updated_at)
                 VALUES (?1, ?2, ?3)
                 ON CONFLICT(key) DO UPDATE SET
                   value_json = excluded.value_json,
                   updated_at = excluded.updated_at",
                params![setting.key, setting.value_json, now],
            )?;
        }
        tx.commit()?;

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

        if let Some(cover_source_path) = Self::find_cover_source_path(&source_path) {
            if let Err(err) = self.upsert_book_cover_from_file(&app, &book.id, &cover_source_path) {
                let _ = self.log_recoverable_cover_error(
                    &app,
                    &format!(
                        "cover_ingest_failed book_id={} source={} error={}",
                        book.id,
                        cover_source_path.display(),
                        err
                    ),
                );
            }
        }

        let _ = self.run_deferred_cover_cleanup(&app);

        Ok(book)
    }

    pub fn delete_book(&mut self, app: tauri::AppHandle, input: BookDeleteInput) -> AppResult<()> {
        let book_id = input.book_id.trim();
        let maybe_cover_path = self.delete_book_metadata(book_id)?;

        if let Some(storage_path) = maybe_cover_path {
            let remove_result = fs::remove_file(PathBuf::from(&storage_path));
            if let Err(err) = remove_result {
                if err.kind() != std::io::ErrorKind::NotFound {
                    self.enqueue_cover_cleanup(&app, &storage_path)?;
                    self.log_recoverable_cover_error(
                        &app,
                        &format!(
                            "deferred_cover_cleanup_queued book_id={} path={} error={}",
                            book_id, storage_path, err
                        ),
                    )?;
                }
            }
        }

        let _ = self.run_deferred_cover_cleanup(&app);
        Ok(())
    }

    pub fn delete_book_metadata(&mut self, book_id: &str) -> AppResult<Option<String>> {
        let book_id = book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();
        let cover: Option<(String, String)> = self
            .connection
            .query_row(
                "SELECT id, storage_path
                 FROM book_covers
                 WHERE book_id = ?1 AND deleted_at IS NULL
                 LIMIT 1",
                params![book_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;

        let tx = self.connection.transaction()?;
        tx.execute(
            "UPDATE books
             SET deleted_at = ?1, updated_at = ?1, version = version + 1
             WHERE id = ?2 AND deleted_at IS NULL",
            params![now, book_id],
        )?;

        if let Some((cover_id, _)) = &cover {
            tx.execute(
                "UPDATE book_covers
                 SET deleted_at = ?1, updated_at = ?1, version = version + 1
                 WHERE id = ?2",
                params![now, cover_id],
            )?;
        }
        tx.commit()?;

        Ok(cover.map(|(_, storage_path)| storage_path))
    }

    pub fn upsert_book_cover_from_file(
        &self,
        app: &tauri::AppHandle,
        book_id: &str,
        source_cover_path: &Path,
    ) -> AppResult<BookCoverDto> {
        if book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
        if !source_cover_path.exists() {
            return Err(AppError::InvalidInput(format!(
                "Cover source file does not exist: {}",
                source_cover_path.display()
            )));
        }

        let covers_dir = self.resolve_covers_dir(app)?;
        fs::create_dir_all(&covers_dir)?;
        let extension = source_cover_path
            .extension()
            .and_then(|ext| ext.to_str())
            .map(|ext| ext.to_ascii_lowercase())
            .unwrap_or_else(|| "bin".to_string());
        let storage_path = covers_dir.join(format!("{}.{}", book_id, extension));

        fs::copy(source_cover_path, &storage_path)?;
        let metadata = fs::metadata(&storage_path)?;
        let mime_type = Self::mime_type_from_extension(&extension);
        let now = Utc::now().to_rfc3339();

        let existing_cover: Option<(String, String)> = self
            .connection
            .query_row(
                "SELECT id, storage_path
                 FROM book_covers
                 WHERE book_id = ?1 AND deleted_at IS NULL
                 LIMIT 1",
                params![book_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;

        let cover_id = existing_cover
            .as_ref()
            .map(|(id, _)| id.clone())
            .unwrap_or_else(|| Uuid::new_v4().to_string());

        self.connection.execute(
            "INSERT INTO book_covers (id, book_id, storage_path, mime_type, width, height, byte_size, checksum, created_at, updated_at, deleted_at, version)
             VALUES (?1, ?2, ?3, ?4, NULL, NULL, ?5, NULL, ?6, ?6, NULL, 1)
             ON CONFLICT(id) DO UPDATE SET
               storage_path = excluded.storage_path,
               mime_type = excluded.mime_type,
               width = excluded.width,
               height = excluded.height,
               byte_size = excluded.byte_size,
               checksum = excluded.checksum,
               updated_at = excluded.updated_at,
               deleted_at = NULL,
               version = book_covers.version + 1",
            params![
                cover_id,
                book_id,
                storage_path.to_string_lossy().to_string(),
                mime_type,
                metadata.len() as i64,
                now,
            ],
        )?;

        if let Some((_, old_storage_path)) = existing_cover {
            let new_storage_path = storage_path.to_string_lossy().to_string();
            if old_storage_path != new_storage_path {
                let _ = fs::remove_file(old_storage_path);
            }
        }

        Ok(BookCoverDto {
            book_id: book_id.to_string(),
            storage_path: storage_path.to_string_lossy().to_string(),
            mime_type: mime_type.to_string(),
            width: None,
            height: None,
            byte_size: metadata.len() as i64,
        })
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

    pub fn save_book_file(&self, id: &str, data: &[u8]) -> AppResult<()> {
        let book_id = id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let file_path: Option<String> = self
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

        self.connection.execute(
            "UPDATE books
             SET sync_status = 'synced', updated_at = ?1, version = version + 1
             WHERE id = ?2",
            params![Utc::now().to_rfc3339(), book_id],
        )?;

        Ok(())
    }

    pub fn hide_book_from_library(&self, book_id: &str) -> AppResult<()> {
        let normalized_book_id = book_id.trim();
        if normalized_book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let existing_hidden_at: Option<Option<String>> = self
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
        self.connection.execute(
            "UPDATE books
             SET hidden_at = COALESCE(hidden_at, ?1), updated_at = ?1, version = version + 1
             WHERE id = ?2 AND deleted_at IS NULL",
            params![now, normalized_book_id],
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
                params![&payload.book_id],
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
                        &payload.book_id,
                        &payload.cfi_location,
                        payload.percentage,
                        &now,
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
                &progress.book_id,
                &progress.cfi_location,
                progress.percentage,
                &progress.updated_at
            ],
        )?;

        Ok(())
    }

    pub fn list_library_books(&self) -> AppResult<Vec<LibraryBookDto>> {
        let mut statement = self.connection.prepare(
            "SELECT b.id,
                    b.title,
                    b.author,
                    b.format,
                    b.current_page,
                    b.total_pages,
                    COALESCE(rp.percentage, 0.0) AS progress_percentage,
                    bc.storage_path,
                    COALESCE(CAST(ROUND(rs.total_duration_seconds / 60.0) AS INTEGER), 0) AS minutes_read,
                    b.updated_at
             FROM books b
             LEFT JOIN reading_progress rp
               ON rp.book_id = b.id
              AND rp.deleted_at IS NULL
             LEFT JOIN book_covers bc
               ON bc.book_id = b.id
              AND bc.deleted_at IS NULL
             LEFT JOIN (
                SELECT book_id, SUM(duration_seconds) AS total_duration_seconds
                FROM reading_sessions
                GROUP BY book_id
             ) rs
               ON rs.book_id = b.id
             WHERE b.deleted_at IS NULL
               AND b.hidden_at IS NULL
             ORDER BY b.updated_at DESC, b.id ASC",
        )?;

        let rows = statement.query_map([], |row| {
            Ok(LibraryBookDto {
                id: row.get(0)?,
                title: row.get(1)?,
                author: row.get(2)?,
                format: row.get(3)?,
                current_page: row.get(4)?,
                total_pages: row.get(5)?,
                progress_percentage: row.get(6)?,
                cover_path: row.get(7)?,
                minutes_read: row.get(8)?,
                updated_at: row.get(9)?,
            })
        })?;

        let books = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(books)
    }

    pub fn save_reading_session(&self, session: ReadingSessionInput) -> AppResult<()> {
        let book_id = session.book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }
        if session.started_at.trim().is_empty() {
            return Err(AppError::InvalidInput(
                "Reading session startedAt is required".to_string(),
            ));
        }
        if session.duration_seconds < 0 {
            return Err(AppError::InvalidInput(
                "Reading session durationSeconds cannot be negative".to_string(),
            ));
        }
        if session.duration_seconds == 0 {
            return Err(AppError::InvalidInput(
                "Reading session durationSeconds must be greater than zero".to_string(),
            ));
        }
        if session.ended_at.is_none() {
            return Err(AppError::InvalidInput(
                "Reading session endedAt is required".to_string(),
            ));
        }

        let started_at =
            chrono::DateTime::parse_from_rfc3339(session.started_at.trim()).map_err(|_| {
                AppError::InvalidInput("Reading session startedAt must be RFC3339".to_string())
            })?;
        let ended_at_raw = session.ended_at.as_deref().unwrap_or_default();
        let ended_at = chrono::DateTime::parse_from_rfc3339(ended_at_raw.trim()).map_err(|_| {
            AppError::InvalidInput("Reading session endedAt must be RFC3339".to_string())
        })?;
        if ended_at <= started_at {
            return Err(AppError::InvalidInput(
                "Reading session endedAt must be after startedAt".to_string(),
            ));
        }

        Self::validate_percentage("startPercentage", session.start_percentage)?;
        Self::validate_percentage("endPercentage", session.end_percentage)?;

        if let (Some(start), Some(end)) = (session.start_percentage, session.end_percentage) {
            if (start - end).abs() < f64::EPSILON {
                return Err(AppError::InvalidInput(
                    "Reading session startPercentage and endPercentage cannot be equal".to_string(),
                ));
            }
        }

        self.connection.execute(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_seconds, start_percentage, end_percentage, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                Uuid::new_v4().to_string(),
                book_id,
                session.started_at,
                session.ended_at,
                session.duration_seconds,
                session.start_percentage,
                session.end_percentage,
                Utc::now().to_rfc3339(),
            ],
        )?;

        Ok(())
    }

    pub fn get_reading_stats(&self, book_id: Option<&str>) -> AppResult<ReadingStatsSummaryDto> {
        if let Some(id) = book_id {
            if id.trim().is_empty() {
                return Err(AppError::MissingBookId);
            }
        }

        let mut stats = self.recompute_reading_stats_from_sessions(book_id)?;

        if self.reading_stats_drift_over_threshold(book_id, stats.avg_progress_percentage)? {
            stats = self.recompute_reading_stats_from_sessions(book_id)?;
        }

        Ok(stats)
    }

    pub fn index_book_text(&mut self, payload: IndexBookTextInput) -> AppResult<()> {
        let book_id = payload.book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let tx = self.connection.transaction()?;

        tx.execute(
            "DELETE FROM book_text_fts WHERE book_id = ?1",
            params![book_id],
        )?;
        tx.execute(
            "DELETE FROM book_text_chunks WHERE book_id = ?1",
            params![book_id],
        )?;

        let now = Utc::now().to_rfc3339();
        for chunk in payload.chunks {
            if chunk.locator.trim().is_empty() || chunk.text_content.trim().is_empty() {
                continue;
            }

            let chunk_id = Uuid::new_v4().to_string();
            tx.execute(
                "INSERT INTO book_text_chunks (id, book_id, locator, chunk_index, text_content, created_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                params![
                    chunk_id,
                    book_id,
                    chunk.locator,
                    chunk.chunk_index,
                    chunk.text_content,
                    now,
                ],
            )?;

            tx.execute(
                "INSERT INTO book_text_fts (chunk_id, book_id, locator, text_content)
                 VALUES (?1, ?2, ?3, ?4)",
                params![chunk_id, book_id, chunk.locator, chunk.text_content],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    pub fn search_book_text(
        &self,
        payload: SearchBookTextInput,
    ) -> AppResult<SearchBookTextResponse> {
        let book_id = payload.book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let query = Self::build_fts_match_query(&payload.query)?;
        let page = payload.page.max(1);
        let page_size = if payload.page_size <= 0 {
            DEFAULT_SEARCH_PAGE_SIZE
        } else {
            payload.page_size.min(MAX_SEARCH_PAGE_SIZE)
        };

        let total: i64 = self.connection.query_row(
            "SELECT COUNT(*)
             FROM book_text_fts
             WHERE book_id = ?1
               AND book_text_fts MATCH ?2",
            params![book_id, &query],
            |row| row.get(0),
        )?;

        let offset = (page - 1) * page_size;
        if offset >= total {
            return Ok(SearchBookTextResponse {
                items: Vec::new(),
                total,
                page,
                page_size,
            });
        }

        let mut statement = self.connection.prepare(
            "SELECT fts.chunk_id,
                    fts.book_id,
                    fts.locator,
                    snippet(book_text_fts, 3, '[', ']', '...', 18) AS snippet,
                    bm25(book_text_fts) AS rank
             FROM book_text_fts fts
             JOIN book_text_chunks chunks
               ON chunks.id = fts.chunk_id
             WHERE fts.book_id = ?1
               AND book_text_fts MATCH ?2
             ORDER BY rank ASC, chunks.chunk_index ASC, fts.chunk_id ASC
             LIMIT ?3 OFFSET ?4",
        )?;

        let rows = statement.query_map(params![book_id, &query, page_size, offset], |row| {
            Ok(SearchResultDto {
                chunk_id: row.get(0)?,
                book_id: row.get(1)?,
                locator: row.get(2)?,
                snippet: row.get(3)?,
                rank: row.get(4)?,
            })
        })?;

        let items = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(SearchBookTextResponse {
            items,
            total,
            page,
            page_size,
        })
    }

    pub fn list_highlights(&self, book_id: Option<&str>) -> AppResult<Vec<HighlightDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, created_at, updated_at
             FROM highlights
             WHERE (?1 IS NULL OR book_id = ?1) AND deleted_at IS NULL
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

    pub fn list_bookmarks(&self, book_id: Option<&str>) -> AppResult<Vec<BookmarkDto>> {
        let mut statement = self.connection.prepare(
            "SELECT id, book_id, page, position, title, created_at
             FROM bookmarks
             WHERE (?1 IS NULL OR book_id = ?1) AND deleted_at IS NULL
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

    fn validate_setting(setting: &AppSettingDto) -> AppResult<()> {
        let key = setting.key.trim();
        if key.is_empty() {
            return Err(AppError::InvalidInput(
                "Setting key is required".to_string(),
            ));
        }
        if key.len() > 128 {
            return Err(AppError::InvalidInput(
                "Setting key exceeds 128 characters".to_string(),
            ));
        }
        if !key
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
        {
            return Err(AppError::InvalidInput(format!(
                "Setting key contains unsupported characters: {}",
                setting.key
            )));
        }

        let parsed: Value = serde_json::from_str(&setting.value_json).map_err(|_| {
            AppError::InvalidInput(format!(
                "Setting '{}' must contain valid JSON in valueJson",
                setting.key
            ))
        })?;

        if !matches!(
            parsed,
            Value::String(_) | Value::Bool(_) | Value::Number(_) | Value::Null
        ) {
            return Err(AppError::InvalidInput(format!(
                "Setting '{}' value must be scalar JSON type",
                setting.key
            )));
        }

        Ok(())
    }

    pub fn has_desktop_parity_schema(&self) -> AppResult<bool> {
        const REQUIRED: [&str; 5] = [
            "app_settings",
            "book_covers",
            "reading_sessions",
            "book_text_chunks",
            "book_text_fts",
        ];

        for table in REQUIRED {
            let exists: Option<i32> = self
                .connection
                .query_row(
                    "SELECT 1 FROM sqlite_master WHERE name = ?1 LIMIT 1",
                    params![table],
                    |row| row.get(0),
                )
                .optional()?;
            if exists.is_none() {
                return Ok(false);
            }
        }

        Ok(true)
    }

    fn validate_percentage(label: &str, value: Option<f64>) -> AppResult<()> {
        if let Some(v) = value {
            if !(0.0..=100.0).contains(&v) {
                return Err(AppError::InvalidInput(format!(
                    "{} must be between 0 and 100",
                    label
                )));
            }
        }

        Ok(())
    }

    fn recompute_reading_stats_from_sessions(
        &self,
        book_id: Option<&str>,
    ) -> AppResult<ReadingStatsSummaryDto> {
        if let Some(id) = book_id {
            let total_seconds: i64 = self.connection.query_row(
                "SELECT COALESCE(SUM(duration_seconds), 0)
                 FROM reading_sessions
                 WHERE book_id = ?1",
                params![id],
                |row| row.get(0),
            )?;

            let total_sessions: i64 = self.connection.query_row(
                "SELECT COUNT(*) FROM reading_sessions WHERE book_id = ?1",
                params![id],
                |row| row.get(0),
            )?;

            let max_progress: Option<f64> = self.connection.query_row(
                "SELECT MAX(COALESCE(end_percentage, start_percentage))
                 FROM reading_sessions
                 WHERE book_id = ?1",
                params![id],
                |row| row.get(0),
            )?;

            let avg_progress = max_progress.unwrap_or(0.0);
            return Ok(ReadingStatsSummaryDto {
                total_minutes_read: ((total_seconds as f64) / 60.0).round() as i64,
                total_sessions,
                books_started: if total_sessions > 0 { 1 } else { 0 },
                books_completed: if avg_progress >= 100.0 { 1 } else { 0 },
                avg_progress_percentage: avg_progress,
            });
        }

        let total_seconds: i64 = self.connection.query_row(
            "SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions",
            [],
            |row| row.get(0),
        )?;
        let total_sessions: i64 =
            self.connection
                .query_row("SELECT COUNT(*) FROM reading_sessions", [], |row| {
                    row.get(0)
                })?;
        let books_started: i64 = self.connection.query_row(
            "SELECT COUNT(DISTINCT book_id) FROM reading_sessions",
            [],
            |row| row.get(0),
        )?;
        let books_completed: i64 = self.connection.query_row(
            "SELECT COUNT(*)
             FROM (
                SELECT book_id, MAX(COALESCE(end_percentage, start_percentage, 0)) AS max_progress
                FROM reading_sessions
                GROUP BY book_id
             ) x
             WHERE x.max_progress >= 100.0",
            [],
            |row| row.get(0),
        )?;
        let avg_progress_percentage: f64 = self.connection.query_row(
            "SELECT COALESCE(AVG(max_progress), 0.0)
             FROM (
                SELECT MAX(COALESCE(end_percentage, start_percentage, 0.0)) AS max_progress
                FROM reading_sessions
                GROUP BY book_id
             )",
            [],
            |row| row.get(0),
        )?;

        Ok(ReadingStatsSummaryDto {
            total_minutes_read: ((total_seconds as f64) / 60.0).round() as i64,
            total_sessions,
            books_started,
            books_completed,
            avg_progress_percentage,
        })
    }

    fn reading_stats_drift_over_threshold(
        &self,
        book_id: Option<&str>,
        event_avg_progress: f64,
    ) -> AppResult<bool> {
        let baseline_progress: f64 = if let Some(id) = book_id {
            self.connection
                .query_row(
                    "SELECT COALESCE(percentage, 0.0)
                 FROM reading_progress
                 WHERE book_id = ?1 AND deleted_at IS NULL
                 ORDER BY updated_at DESC
                 LIMIT 1",
                    params![id],
                    |row| row.get(0),
                )
                .optional()?
                .unwrap_or(0.0)
        } else {
            self.connection.query_row(
                "SELECT COALESCE(AVG(percentage), 0.0)
                 FROM reading_progress
                 WHERE deleted_at IS NULL",
                [],
                |row| row.get(0),
            )?
        };

        Ok((event_avg_progress - baseline_progress).abs() > 1.0)
    }

    fn build_fts_match_query(query: &str) -> AppResult<String> {
        let tokens: Vec<String> = query
            .split_whitespace()
            .map(|token| token.trim())
            .filter(|token| !token.is_empty())
            .map(|token| token.replace('"', "\"\""))
            .map(|token| format!("\"{}\"", token))
            .collect();

        if tokens.is_empty() {
            return Err(AppError::InvalidInput(
                "Search query cannot be empty".to_string(),
            ));
        }

        Ok(tokens.join(" AND "))
    }

    fn resolve_covers_dir(&self, app: &tauri::AppHandle) -> AppResult<PathBuf> {
        let app_data_dir = app
            .path()
            .app_data_dir()
            .map_err(|err| AppError::InvalidInput(err.to_string()))?;
        Ok(app_data_dir.join("covers"))
    }

    fn deferred_cleanup_queue_path(&self, app: &tauri::AppHandle) -> AppResult<PathBuf> {
        let app_data_dir = app
            .path()
            .app_data_dir()
            .map_err(|err| AppError::InvalidInput(err.to_string()))?;
        Ok(app_data_dir.join("cover_cleanup_queue.txt"))
    }

    fn deferred_cleanup_log_path(&self, app: &tauri::AppHandle) -> AppResult<PathBuf> {
        let app_data_dir = app
            .path()
            .app_data_dir()
            .map_err(|err| AppError::InvalidInput(err.to_string()))?;
        Ok(app_data_dir.join("cover_cleanup.log"))
    }

    fn enqueue_cover_cleanup(&self, app: &tauri::AppHandle, storage_path: &str) -> AppResult<()> {
        let queue_path = self.deferred_cleanup_queue_path(app)?;
        if let Some(parent) = queue_path.parent() {
            fs::create_dir_all(parent)?;
        }

        let existing = fs::read_to_string(&queue_path).unwrap_or_default();
        if existing.lines().any(|line| line.trim() == storage_path) {
            return Ok(());
        }

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(queue_path)?;
        writeln!(file, "{}", storage_path)?;
        Ok(())
    }

    fn run_deferred_cover_cleanup(&self, app: &tauri::AppHandle) -> AppResult<()> {
        let queue_path = self.deferred_cleanup_queue_path(app)?;
        if !queue_path.exists() {
            return Ok(());
        }

        let queue = fs::read_to_string(&queue_path).unwrap_or_default();
        let mut remaining: Vec<String> = Vec::new();

        for raw_line in queue.lines() {
            let candidate = raw_line.trim();
            if candidate.is_empty() {
                continue;
            }

            let path = PathBuf::from(candidate);
            if !path.exists() {
                continue;
            }

            match fs::remove_file(&path) {
                Ok(_) => {}
                Err(err) => {
                    if err.kind() == std::io::ErrorKind::NotFound {
                        continue;
                    }
                    remaining.push(candidate.to_string());
                    self.log_recoverable_cover_error(
                        app,
                        &format!(
                            "deferred_cover_cleanup_retry_failed path={} error={}",
                            candidate, err
                        ),
                    )?;
                }
            }
        }

        if remaining.is_empty() {
            let _ = fs::remove_file(queue_path);
        } else {
            fs::write(queue_path, format!("{}\n", remaining.join("\n")))?;
        }

        Ok(())
    }

    fn log_recoverable_cover_error(&self, app: &tauri::AppHandle, message: &str) -> AppResult<()> {
        let log_path = self.deferred_cleanup_log_path(app)?;
        if let Some(parent) = log_path.parent() {
            fs::create_dir_all(parent)?;
        }
        let now = Utc::now().to_rfc3339();
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(log_path)?;
        writeln!(file, "[{}] {}", now, message)?;
        Ok(())
    }

    fn find_cover_source_path(book_source_path: &Path) -> Option<PathBuf> {
        let stem = book_source_path.file_stem()?.to_str()?;
        let parent = book_source_path.parent()?;
        let supported_extensions = ["jpg", "jpeg", "png", "webp"];

        for ext in supported_extensions {
            let candidate = parent.join(format!("{}.{}", stem, ext));
            if candidate.exists() {
                return Some(candidate);
            }
        }

        None
    }

    fn mime_type_from_extension(ext: &str) -> &'static str {
        match ext {
            "jpg" | "jpeg" => "image/jpeg",
            "png" => "image/png",
            "webp" => "image/webp",
            _ => "application/octet-stream",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn apply_test_migrations(connection: &Connection) {
        connection
            .execute_batch(include_str!("../migrations/0001_init.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../migrations/0002_books.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../migrations/0003_highlights.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!(
                "../migrations/0004_desktop_feature_parity.sql"
            ))
            .unwrap();
        connection
            .execute_batch(include_str!("../migrations/0005_hidden_books.sql"))
            .unwrap();
    }

    fn new_repository() -> LibraryRepository {
        let connection = Connection::open_in_memory().unwrap();
        apply_test_migrations(&connection);
        LibraryRepository::new(connection)
    }

    fn insert_book(repository: &LibraryRepository, id: &str, file_path: &str) {
        let now = Utc::now().to_rfc3339();
        repository
            .connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', ?2, 'epub', 'local', 0, 100, ?3, ?3, 1)",
                params![id, file_path, now],
            )
            .unwrap();
    }

    #[test]
    fn settings_validation_rejects_invalid_payload_without_mutating_existing_values() {
        let mut repository = new_repository();

        repository
            .upsert_settings(vec![AppSettingDto {
                key: "ui.theme".to_string(),
                value_json: "\"light\"".to_string(),
                updated_at: Utc::now().to_rfc3339(),
            }])
            .unwrap();

        let result = repository.upsert_settings(vec![AppSettingDto {
            key: "ui.theme".to_string(),
            value_json: "{\"nested\":true}".to_string(),
            updated_at: Utc::now().to_rfc3339(),
        }]);

        assert!(matches!(result, Err(AppError::InvalidInput(_))));

        let settings = repository.get_settings().unwrap();
        assert_eq!(settings.len(), 1);
        assert_eq!(settings[0].key, "ui.theme");
        assert_eq!(settings[0].value_json, "\"light\"");
    }

    #[test]
    fn cover_source_path_prefers_supported_sidecar_assets() {
        let temp_dir = std::env::temp_dir().join(format!("nextpage_cover_test_{}", Uuid::new_v4()));
        fs::create_dir_all(&temp_dir).unwrap();

        let book_path = temp_dir.join("sample.epub");
        fs::write(&book_path, b"dummy").unwrap();
        let png_path = temp_dir.join("sample.png");
        fs::write(&png_path, b"cover").unwrap();

        let found = LibraryRepository::find_cover_source_path(&book_path);
        assert_eq!(found, Some(png_path));

        let _ = fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn search_sanitization_quotes_tokens_for_fts_match() {
        let query = LibraryRepository::build_fts_match_query("alpha \"beta\" gamma").unwrap();
        assert!(query.contains("\"alpha\""));
        assert!(query.contains("\"\"\"beta\"\"\""));
        assert!(query.contains("\"gamma\""));
        assert!(query.contains("AND"));
    }

    #[test]
    fn search_result_page_size_is_bounded_to_200() {
        let mut repository = new_repository();
        insert_book(&repository, "book-search", "C:/library/book-search.epub");

        let chunks = (0..250)
            .map(|index| crate::models::IndexBookTextChunkInput {
                locator: format!("cfi-{index}"),
                chunk_index: index,
                text_content: format!("keyword repeated text {index}"),
            })
            .collect::<Vec<_>>();

        repository
            .index_book_text(IndexBookTextInput {
                book_id: "book-search".to_string(),
                chunks,
            })
            .unwrap();

        let response = repository
            .search_book_text(SearchBookTextInput {
                book_id: "book-search".to_string(),
                query: "keyword".to_string(),
                page: 1,
                page_size: 500,
            })
            .unwrap();

        assert_eq!(response.page_size, 200);
        assert_eq!(response.items.len(), 200);
        assert_eq!(response.total, 250);
    }

    #[test]
    fn stats_drift_threshold_respects_one_percent_tolerance() {
        let repository = new_repository();
        insert_book(&repository, "book-stats", "C:/library/book-stats.epub");

        repository
            .connection
            .execute(
                "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, deleted_at, version)
                 VALUES (?1, ?2, 'loc', ?3, ?4, NULL, 1)",
                params![Uuid::new_v4().to_string(), "book-stats", 10.0_f64, Utc::now().to_rfc3339()],
            )
            .unwrap();

        assert!(!repository
            .reading_stats_drift_over_threshold(Some("book-stats"), 10.5)
            .unwrap());
        assert!(repository
            .reading_stats_drift_over_threshold(Some("book-stats"), 12.5)
            .unwrap());
    }

    #[test]
    fn delete_book_metadata_marks_cover_deleted_and_returns_path() {
        let mut repository = new_repository();
        insert_book(
            &repository,
            "book-cover-delete",
            "C:/library/book-cover-delete.epub",
        );
        let now = Utc::now().to_rfc3339();

        repository
            .connection
            .execute(
                "INSERT INTO book_covers (id, book_id, storage_path, mime_type, width, height, byte_size, checksum, created_at, updated_at, deleted_at, version)
                 VALUES (?1, ?2, ?3, 'image/png', NULL, NULL, 10, NULL, ?4, ?4, NULL, 1)",
                params![
                    Uuid::new_v4().to_string(),
                    "book-cover-delete",
                    "C:/tmp/book-cover-delete.png",
                    now
                ],
            )
            .unwrap();

        let storage_path = repository
            .delete_book_metadata("book-cover-delete")
            .unwrap();
        assert_eq!(
            storage_path.as_deref(),
            Some("C:/tmp/book-cover-delete.png")
        );

        let deleted_cover_rows: i64 = repository
            .connection
            .query_row(
                "SELECT COUNT(*) FROM book_covers WHERE book_id = ?1 AND deleted_at IS NOT NULL",
                params!["book-cover-delete"],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(deleted_cover_rows, 1);
    }

    #[test]
    fn list_library_books_scales_to_large_dataset() {
        let repository = new_repository();
        let now = Utc::now().to_rfc3339();

        for index in 0..1_000 {
            repository
                .connection
                .execute(
                    "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                     VALUES (?1, ?2, 'Author', ?3, 'epub', 'local', 0, 100, ?4, ?4, 1)",
                    params![
                        format!("book-{index}"),
                        format!("Book {index}"),
                        format!("C:/library/book-{index}.epub"),
                        now
                    ],
                )
                .unwrap();
        }

        let rows = repository.list_library_books().unwrap();
        assert_eq!(rows.len(), 1_000);
    }

    #[test]
    fn search_returns_empty_when_paging_beyond_final_results() {
        let mut repository = new_repository();
        insert_book(
            &repository,
            "book-pagination",
            "C:/library/book-pagination.epub",
        );

        let chunks = (0..450)
            .map(|index| crate::models::IndexBookTextChunkInput {
                locator: format!("loc-{index}"),
                chunk_index: index,
                text_content: format!("needle phrase segment {index}"),
            })
            .collect::<Vec<_>>();

        repository
            .index_book_text(IndexBookTextInput {
                book_id: "book-pagination".to_string(),
                chunks,
            })
            .unwrap();

        let final_page = repository
            .search_book_text(SearchBookTextInput {
                book_id: "book-pagination".to_string(),
                query: "needle".to_string(),
                page: 3,
                page_size: 200,
            })
            .unwrap();
        assert_eq!(final_page.items.len(), 50);
        assert_eq!(final_page.total, 450);

        let out_of_range = repository
            .search_book_text(SearchBookTextInput {
                book_id: "book-pagination".to_string(),
                query: "needle".to_string(),
                page: 4,
                page_size: 200,
            })
            .unwrap();
        assert!(out_of_range.items.is_empty());
        assert_eq!(out_of_range.total, 450);
    }

    #[test]
    fn stats_aggregation_returns_expected_totals() {
        let repository = new_repository();
        insert_book(&repository, "book-a", "C:/library/book-a.epub");
        insert_book(&repository, "book-b", "C:/library/book-b.epub");

        repository
            .save_reading_session(ReadingSessionInput {
                book_id: "book-a".to_string(),
                started_at: Utc::now().to_rfc3339(),
                ended_at: Some((Utc::now() + chrono::Duration::seconds(120)).to_rfc3339()),
                duration_seconds: 120,
                start_percentage: Some(10.0),
                end_percentage: Some(20.0),
            })
            .unwrap();
        repository
            .save_reading_session(ReadingSessionInput {
                book_id: "book-b".to_string(),
                started_at: Utc::now().to_rfc3339(),
                ended_at: Some((Utc::now() + chrono::Duration::seconds(180)).to_rfc3339()),
                duration_seconds: 180,
                start_percentage: Some(30.0),
                end_percentage: Some(90.0),
            })
            .unwrap();

        let stats = repository.get_reading_stats(None).unwrap();
        assert_eq!(stats.total_sessions, 2);
        assert_eq!(stats.books_started, 2);
        assert_eq!(stats.total_minutes_read, 5);
        assert!((stats.avg_progress_percentage - 55.0).abs() <= 1.0);
    }

    #[test]
    fn hide_book_from_library_is_idempotent_and_removes_from_library_views() {
        let repository = new_repository();
        insert_book(&repository, "book-visible", "C:/library/book-visible.epub");

        let initial_library_rows = repository.list_library_books().unwrap();
        assert_eq!(initial_library_rows.len(), 1);
        assert_eq!(initial_library_rows[0].id, "book-visible");

        repository.hide_book_from_library("book-visible").unwrap();
        repository.hide_book_from_library("book-visible").unwrap();

        let remaining_library_rows = repository.list_library_books().unwrap();
        assert!(remaining_library_rows.is_empty());

        let remaining_books = repository.list_books().unwrap();
        assert!(remaining_books.is_empty());

        let hidden_at: Option<String> = repository
            .connection
            .query_row(
                "SELECT hidden_at FROM books WHERE id = ?1",
                params!["book-visible"],
                |row| row.get(0),
            )
            .unwrap();
        assert!(hidden_at.is_some());
    }

    #[test]
    fn hide_book_from_library_returns_error_for_unknown_book() {
        let repository = new_repository();
        let result = repository.hide_book_from_library("missing-book-id");
        assert!(matches!(result, Err(AppError::InvalidInput(_))));
    }

    #[test]
    fn save_progress_does_not_create_reading_session() {
        let repository = new_repository();
        insert_book(
            &repository,
            "book-progress-only",
            "C:/library/book-progress-only.epub",
        );

        repository
            .save_progress(SaveProgressInput {
                book_id: "book-progress-only".to_string(),
                cfi_location: "cfi-1".to_string(),
                percentage: 42.0,
            })
            .unwrap();

        let total_sessions: i64 = repository
            .connection
            .query_row(
                "SELECT COUNT(*) FROM reading_sessions WHERE book_id = ?1",
                params!["book-progress-only"],
                |row| row.get(0),
            )
            .unwrap();

        assert_eq!(total_sessions, 0);
    }

    #[test]
    fn save_reading_session_rejects_zero_signal_events() {
        let repository = new_repository();
        insert_book(
            &repository,
            "book-session-guard",
            "C:/library/book-session-guard.epub",
        );

        let now = Utc::now().to_rfc3339();
        let result = repository.save_reading_session(ReadingSessionInput {
            book_id: "book-session-guard".to_string(),
            started_at: now.clone(),
            ended_at: Some(now),
            duration_seconds: 0,
            start_percentage: Some(10.0),
            end_percentage: Some(10.0),
        });

        assert!(matches!(result, Err(AppError::InvalidInput(_))));
    }

    #[test]
    fn save_reading_session_accepts_valid_explicit_event() {
        let repository = new_repository();
        insert_book(
            &repository,
            "book-valid-session",
            "C:/library/book-valid-session.epub",
        );

        let started_at = Utc::now();
        let ended_at = started_at + chrono::Duration::seconds(45);

        repository
            .save_reading_session(ReadingSessionInput {
                book_id: "book-valid-session".to_string(),
                started_at: started_at.to_rfc3339(),
                ended_at: Some(ended_at.to_rfc3339()),
                duration_seconds: 45,
                start_percentage: Some(12.0),
                end_percentage: Some(14.0),
            })
            .unwrap();

        let total_sessions: i64 = repository
            .connection
            .query_row(
                "SELECT COUNT(*) FROM reading_sessions WHERE book_id = ?1",
                params!["book-valid-session"],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(total_sessions, 1);
    }
}
