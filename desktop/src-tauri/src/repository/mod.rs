// TRANSITION FACADE: LibraryRepository remains the stable public API while domain slices are extracted.
// REMOVE ONLY AFTER VERIFY: remove facade delegation only when parity + serde contract checks are green in verify.

pub mod settings;
pub mod library;
pub mod progress;
pub mod highlights;
pub mod bookmarks;
pub mod collections;
pub mod search;
pub mod files;

use std::collections::HashSet;
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
    CollectionDto, HighlightDto, IndexBookTextInput, LibraryBookDto, ReadingProgressDto,
    ReadingSessionInput, ReadingStatsSummaryDto, SaveBookmarkInput, SaveHighlightInput,
    SaveProgressInput, ScanFolderResultDto, SearchBookTextInput,
    SearchBookTextResponse,
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

    pub fn connection(&self) -> &Connection {
        &self.connection
    }

    pub fn list_books(&self) -> AppResult<Vec<BookDto>> {
        library::list_books(self)
    }

    pub fn upsert_book(&self, book: BookDto) -> AppResult<()> {
        library::upsert_book(self, book)
    }

    pub fn get_settings(&self) -> AppResult<Vec<AppSettingDto>> {
        settings::get_settings(self)
    }

    pub fn upsert_settings(&mut self, settings: Vec<AppSettingDto>) -> AppResult<()> {
        self::settings::upsert_settings(self, settings)
    }

    pub fn is_feature_enabled(&self, feature_name: &str) -> AppResult<bool> {
        let mut statement = self
            .connection
            .prepare("SELECT value_json FROM app_settings WHERE key = ?1")?;

        let result: Option<String> = statement
            .query_row(params![feature_name], |row| row.get(0))
            .optional()?;

        match result {
            Some(value) => {
                let v: Value = serde_json::from_str(&value).unwrap_or(Value::Bool(true));
                Ok(v.as_bool().unwrap_or(true))
            }
            None => Ok(false),
        }
    }

    pub fn has_metrics_table(&self) -> AppResult<bool> {
        let mut statement = self
            .connection
            .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='metrics'")?;

        let exists: Option<String> = statement.query_row([], |row| row.get(0)).optional()?;

        Ok(exists.is_some())
    }

    pub fn ensure_metrics_table(&self) -> AppResult<()> {
        self.connection.execute(
            "CREATE TABLE IF NOT EXISTS metrics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                value REAL NOT NULL,
                tags TEXT,
                timestamp TEXT NOT NULL
            )",
            [],
        )?;

        self.connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_metrics_name_timestamp ON metrics(name, timestamp)",
            [],
        )?;

        Ok(())
    }

    pub fn record_metric(&self, name: &str, value: f64, tags: Option<&str>) -> AppResult<()> {
        if !self.has_metrics_table()? {
            self.ensure_metrics_table()?;
        }

        let now = Utc::now().to_rfc3339();
        self.connection.execute(
            "INSERT INTO metrics (name, value, tags, timestamp) VALUES (?1, ?2, ?3, ?4)",
            params![name, value, tags, now],
        )?;

        Ok(())
    }

    pub fn get_metrics(&self, name: &str, days: i32) -> AppResult<Vec<(String, f64, String)>> {
        if !self.has_metrics_table()? {
            return Ok(Vec::new());
        }

        let since = Utc::now() - chrono::Duration::days(days as i64);
        let since_str = since.to_rfc3339();

        let mut statement = self.connection.prepare(
            "SELECT timestamp, value, tags FROM metrics WHERE name = ?1 AND timestamp >= ?2 ORDER BY timestamp DESC LIMIT 1000",
        )?;

        let rows = statement.query_map(params![name, since_str], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, f64>(1)?,
                row.get::<_, Option<String>>(2)?.unwrap_or_default(),
            ))
        })?;

        let metrics = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(metrics)
    }

    pub fn get_metrics_summary(&self, name: &str, days: i32) -> AppResult<(i64, f64, f64, f64)> {
        if !self.has_metrics_table()? {
            return Ok((0, 0.0, 0.0, 0.0));
        }

        let since = Utc::now() - chrono::Duration::days(days as i64);
        let since_str = since.to_rfc3339();

        let mut statement = self.connection.prepare(
            "SELECT COUNT(*), COALESCE(SUM(value), 0), COALESCE(MIN(value), 0), COALESCE(MAX(value), 0)
             FROM metrics WHERE name = ?1 AND timestamp >= ?2",
        )?;

        let result = statement.query_row(params![name, since_str], |row| {
            Ok((
                row.get::<_, i64>(0)?,
                row.get::<_, f64>(1)?,
                row.get::<_, f64>(2)?,
                row.get::<_, f64>(3)?,
            ))
        })?;

        Ok(result)
    }

    pub fn import_book(&self, app: tauri::AppHandle, input: BookImportInput) -> AppResult<BookDto> {
        library::import_book(self, app, input)
    }

    pub fn scan_folder(&self, path: &str) -> AppResult<ScanFolderResultDto> {
        library::scan_folder(self, path)
    }

    pub fn delete_book(&mut self, app: tauri::AppHandle, input: BookDeleteInput) -> AppResult<()> {
        library::delete_book(self, app, input)
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

    pub fn upsert_book_cover_from_bytes(
        &self,
        app: &tauri::AppHandle,
        book_id: &str,
        data: &[u8],
        mime_type: Option<&str>,
    ) -> AppResult<BookCoverDto> {
        if book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
        if data.is_empty() {
            return Err(AppError::InvalidInput(
                "Cover binary payload cannot be empty".to_string(),
            ));
        }

        let normalized_mime = mime_type
            .map(|value| value.trim().to_ascii_lowercase())
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| "image/png".to_string());

        let extension = Self::extension_from_mime_type(&normalized_mime).ok_or_else(|| {
            AppError::InvalidInput(format!("Unsupported cover mime type: {}", normalized_mime))
        })?;

        let covers_dir = self.resolve_covers_dir(app)?;
        fs::create_dir_all(&covers_dir)?;
        let storage_path = covers_dir.join(format!("{}.{}", book_id, extension));

        fs::write(&storage_path, data)?;
        let metadata = fs::metadata(&storage_path)?;
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
                normalized_mime,
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
            mime_type: normalized_mime,
            width: None,
            height: None,
            byte_size: metadata.len() as i64,
        })
    }

    pub fn update_book_progress(&self, book_id: &str, current_page: i32) -> AppResult<()> {
        files::update_book_progress(self, book_id, current_page)
    }

    pub fn save_book_file(&self, id: &str, data: &[u8]) -> AppResult<()> {
        files::save_book_file(self, id, data)
    }

    pub fn hide_book_from_library(&self, book_id: &str) -> AppResult<()> {
        files::hide_book_from_library(self, book_id)
    }

    pub fn get_progress(&self, book_id: &str) -> AppResult<Option<ReadingProgressDto>> {
        progress::get_progress(self, book_id)
    }

    pub fn save_progress(&self, payload: SaveProgressInput) -> AppResult<()> {
        progress::save_progress(self, payload)
    }

    pub fn upsert_progress(&self, progress: ReadingProgressDto) -> AppResult<()> {
        progress::upsert_progress(self, progress)
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
                    b.updated_at,
                    (SELECT GROUP_CONCAT(collection_id, ',') FROM book_collections bc2 WHERE bc2.book_id = b.id) AS collection_ids
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
            let collection_ids_str: Option<String> = row.get(10)?;
            let collection_ids: Vec<i64> = collection_ids_str
                .map(|s| s.split(',').filter_map(|x| x.parse().ok()).collect())
                .unwrap_or_default();
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
                collection_ids,
            })
        })?;

        let books = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(books)
    }

    pub fn save_reading_session(&self, session: ReadingSessionInput) -> AppResult<()> {
        progress::save_reading_session(self, session)
    }

    pub fn get_reading_stats(&self, book_id: Option<&str>) -> AppResult<ReadingStatsSummaryDto> {
        progress::get_reading_stats(self, book_id)
    }

    pub fn index_book_text(&mut self, payload: IndexBookTextInput) -> AppResult<()> {
        search::index_book_text(self, payload)
    }

    pub fn search_book_text(
        &self,
        payload: SearchBookTextInput,
    ) -> AppResult<SearchBookTextResponse> {
        search::search_book_text(self, payload)
    }

    pub fn list_highlights(&self, book_id: Option<&str>) -> AppResult<Vec<HighlightDto>> {
        highlights::list_highlights(self, book_id)
    }

    pub fn save_highlight(&self, payload: SaveHighlightInput) -> AppResult<HighlightDto> {
        highlights::save_highlight(self, payload)
    }

    pub fn delete_highlight(&self, id: &str) -> AppResult<()> {
        highlights::delete_highlight(self, id)
    }

    pub fn list_bookmarks(&self, book_id: Option<&str>) -> AppResult<Vec<BookmarkDto>> {
        bookmarks::list_bookmarks(self, book_id)
    }

    pub fn save_bookmark(&self, payload: SaveBookmarkInput) -> AppResult<BookmarkDto> {
        bookmarks::save_bookmark(self, payload)
    }

    pub fn delete_bookmark(&self, id: &str) -> AppResult<()> {
        bookmarks::delete_bookmark(self, id)
    }

    pub fn create_collection(&self, name: &str, color: Option<&str>) -> AppResult<CollectionDto> {
        collections::create_collection(self, name, color)
    }

    pub fn delete_collection(&self, id: i64) -> AppResult<()> {
        collections::delete_collection(self, id)
    }

    pub fn list_collections(&self) -> AppResult<Vec<CollectionDto>> {
        collections::list_collections(self)
    }

    pub fn add_book_to_collection(&self, book_id: &str, collection_id: i64) -> AppResult<()> {
        collections::add_book_to_collection(self, book_id, collection_id)
    }

    pub fn remove_book_from_collection(&self, book_id: &str, collection_id: i64) -> AppResult<()> {
        collections::remove_book_from_collection(self, book_id, collection_id)
    }

    pub fn get_book_collections(&self, book_id: &str) -> AppResult<Vec<CollectionDto>> {
        collections::get_book_collections(self, book_id)
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

    fn existing_book_filenames_lowercase(&self) -> AppResult<HashSet<String>> {
        let mut statement = self.connection.prepare(
            "SELECT file_path
             FROM books
             WHERE deleted_at IS NULL",
        )?;

        let rows = statement.query_map([], |row| row.get::<_, String>(0))?;
        let mut names = HashSet::new();

        for file_path in rows {
            let file_path = file_path?;
            let file_name = Path::new(&file_path)
                .file_name()
                .and_then(|value| value.to_str())
                .map(|value| value.to_ascii_lowercase());
            if let Some(value) = file_name {
                names.insert(value);
            }
        }

        Ok(names)
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

    fn extension_from_mime_type(mime_type: &str) -> Option<&'static str> {
        match mime_type {
            "image/jpeg" | "image/jpg" => Some("jpg"),
            "image/png" => Some("png"),
            "image/webp" => Some("webp"),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn apply_test_migrations(connection: &Connection) {
        connection
            .execute_batch(include_str!("../../migrations/0001_init.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0002_books.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0003_highlights.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0004_desktop_feature_parity.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0005_hidden_books.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0006_collections.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0007_highlight_note_and_page_contract.sql"))
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



