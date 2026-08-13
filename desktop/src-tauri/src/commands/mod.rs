// Facade cleanup: only camelCase commands remain.
// All snake_case variants and their camelCase aliases have been merged.
// Internal helpers (_internal suffix) kept as snake_case.

pub mod bookmarks;
pub mod collections;
pub mod epub_reader;
pub mod files;
pub mod highlights;
pub mod library;
pub mod outbox;
pub mod progress;
pub mod search;
pub mod settings;

#[allow(unused_imports)]
pub use bookmarks::*;
#[allow(unused_imports)]
pub use collections::*;
#[allow(unused_imports)]
pub use epub_reader::*;
#[allow(unused_imports)]
pub use files::*;
#[allow(unused_imports)]
pub use highlights::*;
#[allow(unused_imports)]
pub use library::*;
#[allow(unused_imports)]
pub use progress::*;
#[allow(unused_imports)]
pub use search::*;
#[allow(unused_imports)]
pub use settings::*;

use std::fs;
use std::path::PathBuf;

use tauri::State;

use crate::error::AppError;
use crate::logger::{ErrorEventDto, LogEventDto, DEFAULT_MAX_LOG_LINES, SETTING_MAX_LOG_LINES_KEY};
use crate::models::{
    ActivityPoint, AddDictionaryWordInput, AppSettingDto, BookCollectionInput, BookDeleteInput,
    BookDto, BookImportInput, BookmarkDto, CollectionDto, CommandErrorDto, CreateCollectionInput,
    CreateTagInput, DictionaryWordDto, HideBookInput, HighlightDto, IndexBookTextInput,
    LibraryBookDto, ListLibraryBooksInput, ReadingProgressDto, ReadingSessionInput,
    ReadingSessionSavedDto, ReadingStatsSummaryDto, RemoteReadingSessionRow, SaveBookmarkInput,
    SaveHighlightInput, SaveHighlightTagsInput, SaveProgressInput, ScanFolderResultDto,
    SearchBookTextInput, SearchBookTextResponse, SyncOutboxRowDto, TagDto, UpdateHighlightInput,
    UpsertBookCoverInput,
};
use crate::state::AppState;

const LIBRARY_RESPONSE_VERSION: i32 = 1;

// All command functions use camelCase for IPC compatibility with the Frontend.
// #[allow(non_snake_case)] suppresses the Rust convention warning.

fn map_command_error(error: AppError) -> String {
    let dto = match error {
        AppError::InvalidInput(message) => CommandErrorDto::validation(message),
        AppError::Compatibility(message) => CommandErrorDto::compatibility(message),
        AppError::DbConstraint(message) => CommandErrorDto::db_constraint(message),
        AppError::SyncConflict(message) => CommandErrorDto::sync_conflict(message),
        AppError::ImportError(message) => CommandErrorDto::import_error(message),
        AppError::ThumbnailFail(message) => CommandErrorDto::thumbnail_error(message),
        AppError::MigrationFail(message) => CommandErrorDto::migration_fail(message),
        AppError::NotFound(message) => CommandErrorDto::not_found(message),
        other => CommandErrorDto::internal(other.to_string()),
    };

    serde_json::to_string(&dto).unwrap_or_else(|_| {
        "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Command failed\",\"recoverable\":false}"
            .to_string()
    })
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBooks(state: State<'_, AppState>) -> Result<Vec<BookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_books().map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertBook(state: State<'_, AppState>, book: BookDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_book(book).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getSettings(state: State<'_, AppState>) -> Result<Vec<AppSettingDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    // Return empty if schema not ready
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(vec![]);
    }
    repository.get_settings().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertSettings(
    state: State<'_, AppState>,
    settings: Vec<AppSettingDto>,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    // No-op if schema not ready
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(());
    }
    repository.upsert_settings(settings).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listLibraryBooks(
    state: State<'_, AppState>,
    payload: Option<ListLibraryBooksInput>,
) -> Result<Vec<LibraryBookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    list_library_books_internal(&repository, payload).map_err(map_command_error)
}

pub fn list_library_books_internal(
    repository: &crate::repository::LibraryRepository,
    payload: Option<ListLibraryBooksInput>,
) -> Result<Vec<LibraryBookDto>, AppError> {
    if let Some(input) = payload {
        if let Some(version) = input.response_version {
            if version != LIBRARY_RESPONSE_VERSION {
                return Err(AppError::Compatibility(format!(
                    "Unsupported listLibraryBooks responseVersion {} (supported: {})",
                    version, LIBRARY_RESPONSE_VERSION
                )));
            }
        }
    }

    if !repository.has_desktop_parity_schema()? {
        // Schema not ready - return empty list, don't fail
        return Ok(vec![]);
    }

    repository.list_library_books()
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn scanFolder(state: State<'_, AppState>, path: String) -> Result<ScanFolderResultDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.scan_folder(&path).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getProgress(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Option<ReadingProgressDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.get_progress(&book_id).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveProgress(state: State<'_, AppState>, payload: SaveProgressInput) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_progress(payload).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertProgress(
    state: State<'_, AppState>,
    progress: ReadingProgressDto,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_progress(progress).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveReadingSession(
    state: State<'_, AppState>,
    payload: ReadingSessionInput,
) -> Result<ReadingSessionSavedDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_reading_session(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getReadingStats(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<ReadingStatsSummaryDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    // Return default stats if schema not ready
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(ReadingStatsSummaryDto {
            total_minutes_read: 0,
            total_sessions: 0,
            books_started: 0,
            books_completed: 0,
            avg_progress_percentage: 0.0,
        });
    }
    repository.get_reading_stats(book_id.as_deref()).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getReadingActivity(
    state: State<'_, AppState>,
    period: String,
    granularity: String,
    book_id: Option<String>,
) -> Result<Vec<ActivityPoint>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(vec![]);
    }
    repository
        .get_reading_activity(&period, &granularity, book_id.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getReadingStatsForRange(
    state: State<'_, AppState>,
    from: String,
    to: String,
    book_id: Option<String>,
) -> Result<ReadingStatsSummaryDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(ReadingStatsSummaryDto {
            total_minutes_read: 0,
            total_sessions: 0,
            books_started: 0,
            books_completed: 0,
            avg_progress_percentage: 0.0,
        });
    }
    repository
        .get_reading_stats_for_range(&from, &to, book_id.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getReadingStreak(
    state: State<'_, AppState>,
    book_id: Option<String>,
    user_id: String,
) -> Result<i64, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(0);
    }
    repository.get_reading_streak(book_id.as_deref(), &user_id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertRemoteReadingSessions(
    state: State<'_, AppState>,
    rows: Vec<RemoteReadingSessionRow>,
) -> Result<i64, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_remote_reading_sessions(&rows).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn indexBookText(
    state: State<'_, AppState>,
    payload: IndexBookTextInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.index_book_text(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn searchBookText(
    state: State<'_, AppState>,
    payload: SearchBookTextInput,
) -> Result<SearchBookTextResponse, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.search_book_text(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn importBook(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    input: BookImportInput,
) -> Result<BookDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.import_book(app, input).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBook(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: BookDeleteInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_book(app, payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn hideBookFromLibrary(
    state: State<'_, AppState>,
    payload: HideBookInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.hide_book_from_library(&payload.book_id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileBytes(file_path: String) -> Result<Vec<u8>, String> {
    let path = PathBuf::from(&file_path);
    fs::read(&path).map_err(|err| format!("Failed to read file: {}", err))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileSize(file_path: String) -> Result<u64, String> {
    let path = PathBuf::from(&file_path);
    let metadata =
        std::fs::metadata(&path).map_err(|err| format!("Failed to read file metadata: {}", err))?;
    Ok(metadata.len())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn readFileRange(file_path: String, offset: u64, length: u64) -> Result<Vec<u8>, String> {
    use std::io::{Read, Seek, SeekFrom};
    let path = PathBuf::from(&file_path);
    let mut file =
        std::fs::File::open(&path).map_err(|err| format!("Failed to open file: {}", err))?;
    file.seek(SeekFrom::Start(offset)).map_err(|err| format!("Failed to seek in file: {}", err))?;
    let mut buf = vec![0u8; length as usize];
    let n = file.read(&mut buf).map_err(|err| format!("Failed to read file: {}", err))?;
    buf.truncate(n);
    Ok(buf)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn updateBookProgress(
    state: State<'_, AppState>,
    book_id: String,
    current_page: i32,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.update_book_progress(&book_id, current_page).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn fileExists(path: String) -> Result<bool, String> {
    Ok(PathBuf::from(&path).exists())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn saveBookFile(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    id: String,
    data: Vec<u8>,
    title: Option<String>,
    author: Option<String>,
    format: Option<String>,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_book_file(&app, &id, &data, title.as_deref(), author.as_deref(), format.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertBookCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: UpsertBookCoverInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .upsert_book_cover_from_bytes(
            &app,
            &payload.book_id,
            &payload.data,
            payload.mime_type.as_deref(),
        )
        .map_err(map_command_error)?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBookCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    book_id: String,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_book_cover(&app, &book_id).map_err(map_command_error)?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn extractEpubCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    book_id: String,
    file_path: String,
) -> Result<bool, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let path = std::path::PathBuf::from(&file_path);
    match repository.extract_epub_cover(&app, &path, &book_id) {
        Ok(Some(_)) => Ok(true),
        Ok(None) => Ok(false),
        Err(e) => Err(format!("{}", e)),
    }
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listHighlights(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<HighlightDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_highlights(book_id.as_deref()).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveHighlight(
    state: State<'_, AppState>,
    payload: SaveHighlightInput,
) -> Result<HighlightDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_highlight(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteHighlight(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_highlight(&id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn updateHighlight(
    state: State<'_, AppState>,
    payload: UpdateHighlightInput,
) -> Result<HighlightDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.update_highlight(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listTags(state: State<'_, AppState>) -> Result<Vec<TagDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_tags().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listTagsForHighlight(
    state: State<'_, AppState>,
    highlight_id: String,
) -> Result<Vec<TagDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_tags_for_highlight(&highlight_id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn createTag(state: State<'_, AppState>, payload: CreateTagInput) -> Result<TagDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.create_tag(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveHighlightTags(
    state: State<'_, AppState>,
    payload: SaveHighlightTagsInput,
) -> Result<Vec<TagDto>, String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_highlight_tags(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listDictionaryWords(state: State<'_, AppState>) -> Result<Vec<DictionaryWordDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_dictionary_words().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn addDictionaryWord(
    state: State<'_, AppState>,
    payload: AddDictionaryWordInput,
) -> Result<DictionaryWordDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.add_dictionary_word(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn removeDictionaryWord(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.remove_dictionary_word(&id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBookmarks(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<BookmarkDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_bookmarks(book_id.as_deref()).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveBookmark(
    state: State<'_, AppState>,
    payload: SaveBookmarkInput,
) -> Result<BookmarkDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_bookmark(payload).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_bookmark(&id).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn createCollection(
    state: State<'_, AppState>,
    payload: CreateCollectionInput,
) -> Result<CollectionDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.create_collection(&payload.name, payload.color.as_deref()).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteCollection(state: State<'_, AppState>, id: i64) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_collection(id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listCollections(state: State<'_, AppState>) -> Result<Vec<CollectionDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_collections().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn addBookToCollection(
    state: State<'_, AppState>,
    payload: BookCollectionInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .add_book_to_collection(&payload.book_id, payload.collection_id)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn removeBookFromCollection(
    state: State<'_, AppState>,
    payload: BookCollectionInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .remove_book_from_collection(&payload.book_id, payload.collection_id)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getBookCollections(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Vec<CollectionDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.get_book_collections(&book_id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn setReadingStatus(
    state: State<'_, AppState>,
    book_id: String,
    status: Option<String>,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.set_reading_status(&book_id, status.as_deref()).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn reportErrorEvent(state: State<'_, AppState>, event: ErrorEventDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_to_file(&event, max_lines)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn logEvent(state: State<'_, AppState>, event: LogEventDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_generic(&event, max_lines)
}

#[tauri::command(rename_all = "camelCase")]
pub fn diagnose(state: State<'_, AppState>) -> crate::services::diagnostics::DiagnoseResult {
    crate::services::diagnostics::run_diagnose(&state)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getLogs(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.read_all_logs()
}

// ─── Sync Outbox Commands ─────────────────────────────────────────

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn addSyncOutboxItem(
    state: State<'_, AppState>,
    entity_type: String,
    entity_id: Option<String>,
    operation: String,
    payload_json: String,
) -> Result<String, String> {
    use uuid::Uuid;
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    let id = Uuid::new_v4().to_string();
    let now = chrono::Utc::now().to_rfc3339();
    conn.execute(
        "INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_json, created_at, next_retry_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6)",
        rusqlite::params![id, entity_type, entity_id, operation, payload_json, now],
    )
    .map_err(|e| format!("Failed to insert outbox item: {}", e))?;
    Ok(id)
}

/// Upper bound for a single coalesced outbox payload (R3: no unbounded IPC payloads).
/// A READING_PROGRESS payload is a few hundred bytes; 64 KiB is a generous cap.
const MAX_COALESCE_PAYLOAD_BYTES: usize = 64 * 1024;

/// Transactional UPDATE-else-INSERT that coalesces an outbox row per
/// (entity_type, entity_id) + payload `userId` (D5, SR-4.1).
///
/// Latest-wins merge (D6): the row is updated only when the stored payload's
/// `updatedAt` is <= the incoming event's `updatedAt` (ISO-8601 strings are
/// lexicographically comparable). If no row matches the key yet, one is
/// inserted. Rows are NEVER deleted here — coalescing must never drop data.
///
/// R3 security: inputs are validated before touching the DB (non-empty entity
/// key, UPSERT-only, bounded payload, required `userId`/`updatedAt` strings).
/// Nothing is logged; the payload is stored verbatim and never contains tokens.
pub fn add_coalesced_sync_outbox_item_internal(
    conn: &rusqlite::Connection,
    entity_type: &str,
    entity_id: &str,
    operation: &str,
    payload_json: &str,
) -> Result<String, AppError> {
    // R3: validate inputs server-side.
    if entity_type.trim().is_empty() || entity_type.len() > 64 {
        return Err(AppError::InvalidInput(
            "entityType must be a non-empty string (<= 64 chars)".to_string(),
        ));
    }
    if entity_id.trim().is_empty() {
        return Err(AppError::InvalidInput("entityId must be non-empty".to_string()));
    }
    if operation != "UPSERT" {
        return Err(AppError::InvalidInput(
            "addCoalescedSyncOutboxItem only supports UPSERT operations".to_string(),
        ));
    }
    if payload_json.len() > MAX_COALESCE_PAYLOAD_BYTES {
        return Err(AppError::InvalidInput(format!(
            "payloadJson exceeds the {} byte coalescing limit",
            MAX_COALESCE_PAYLOAD_BYTES
        )));
    }
    let payload: serde_json::Value = serde_json::from_str(payload_json)
        .map_err(|e| AppError::InvalidInput(format!("payloadJson must be valid JSON: {}", e)))?;
    let user_id = payload
        .get("userId")
        .and_then(|v| v.as_str())
        .filter(|s| !s.is_empty())
        .ok_or_else(|| {
            AppError::InvalidInput("payloadJson must contain a non-empty userId string".to_string())
        })?;
    let updated_at =
        payload.get("updatedAt").and_then(|v| v.as_str()).filter(|s| !s.is_empty()).ok_or_else(
            || {
                AppError::InvalidInput(
                    "payloadJson must contain a non-empty updatedAt string".to_string(),
                )
            },
        )?;

    let now = chrono::Utc::now().to_rfc3339();
    let tx = conn.unchecked_transaction()?;

    // Latest-wins UPDATE: only overwrite when the stored row is not newer.
    let updated = tx.execute(
        "UPDATE sync_outbox
         SET payload_json = ?1, operation = 'UPSERT', retry_count = 0, last_error = NULL, next_retry_at = ?2
         WHERE entity_type = ?3 AND entity_id = ?4
           AND json_extract(payload_json, '$.userId') = ?5
           AND json_extract(payload_json, '$.updatedAt') <= ?6",
        rusqlite::params![payload_json, now, entity_type, entity_id, user_id, updated_at],
    )?;

    let id = if updated > 0 {
        // Updated in place — return the existing row's id.
        tx.query_row(
            "SELECT id FROM sync_outbox
             WHERE entity_type = ?1 AND entity_id = ?2 AND json_extract(payload_json, '$.userId') = ?3
             LIMIT 1",
            rusqlite::params![entity_type, entity_id, user_id],
            |row| row.get::<_, String>(0),
        )?
    } else {
        // No match: either the key is new, or the stored row is newer (stale
        // event). Insert only when no row exists for the key so a stale event
        // can never create a duplicate row (SR-4.1 at-most-one-row invariant).
        let id = uuid::Uuid::new_v4().to_string();
        tx.execute(
            "INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_json, created_at, next_retry_at)
             SELECT ?1, ?2, ?3, 'UPSERT', ?4, ?5, ?5
             WHERE NOT EXISTS (
               SELECT 1 FROM sync_outbox
               WHERE entity_type = ?2 AND entity_id = ?3 AND json_extract(payload_json, '$.userId') = ?6
             )",
            rusqlite::params![id, entity_type, entity_id, payload_json, now, user_id],
        )?;
        id
    };

    tx.commit()?;
    Ok(id)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn addCoalescedSyncOutboxItem(
    state: State<'_, AppState>,
    entity_type: String,
    entity_id: Option<String>,
    operation: String,
    payload_json: String,
) -> Result<String, String> {
    let entity_id = entity_id.ok_or_else(|| {
        map_command_error(AppError::InvalidInput("entityId is required".to_string()))
    })?;
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    add_coalesced_sync_outbox_item_internal(
        conn,
        &entity_type,
        &entity_id,
        &operation,
        &payload_json,
    )
    .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listSyncOutboxReady(state: State<'_, AppState>) -> Result<Vec<SyncOutboxRowDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    let now = chrono::Utc::now().to_rfc3339();
    let mut statement = conn
        .prepare(
            "SELECT id, entity_type, entity_id, operation, payload_json, retry_count, last_error, created_at, next_retry_at
             FROM sync_outbox
             WHERE next_retry_at <= ?1 AND retry_count < 50
             ORDER BY created_at ASC",
        )
        .map_err(|e| format!("Failed to prepare: {}", e))?;
    let rows = statement
        .query_map(rusqlite::params![now], |row| {
            Ok(SyncOutboxRowDto {
                id: row.get(0)?,
                entity_type: row.get(1)?,
                entity_id: row.get(2)?,
                operation: row.get(3)?,
                payload_json: row.get(4)?,
                retry_count: row.get(5)?,
                last_error: row.get(6)?,
                created_at: row.get(7)?,
                next_retry_at: row.get(8)?,
            })
        })
        .map_err(|e| format!("Failed to query: {}", e))?;
    let items = rows.collect::<Result<Vec<_>, _>>().map_err(|e| format!("{}", e))?;
    Ok(items)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn markSyncOutboxFailed(
    state: State<'_, AppState>,
    id: String,
    error: String,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    // Read current retry_count
    let current_retry: i32 = conn
        .query_row(
            "SELECT retry_count FROM sync_outbox WHERE id = ?1",
            rusqlite::params![id],
            |row| row.get(0),
        )
        .map_err(|e| format!("Failed to read retry_count: {}", e))?;
    let next_delay = std::cmp::min(60, 2_i32.saturating_pow((current_retry + 1) as u32));
    let next_retry_at =
        (chrono::Utc::now() + chrono::Duration::seconds(next_delay as i64)).to_rfc3339();
    conn.execute(
        "UPDATE sync_outbox SET retry_count = retry_count + 1, last_error = ?1, next_retry_at = ?2 WHERE id = ?3",
        rusqlite::params![error, next_retry_at, id],
    )
    .map_err(|e| format!("Failed to update outbox item: {}", e))?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteSyncOutboxItem(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    conn.execute("DELETE FROM sync_outbox WHERE id = ?1", rusqlite::params![id])
        .map_err(|e| format!("Failed to delete outbox item: {}", e))?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn pruneSyncOutbox(state: State<'_, AppState>) -> Result<i32, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let conn = repository.connection();
    let cutoff = (chrono::Utc::now() - chrono::Duration::days(7)).to_rfc3339();
    let deleted = conn
        .execute(
            "DELETE FROM sync_outbox WHERE created_at < ?1 AND retry_count >= 10 AND entity_type <> 'READING_SESSION'",
            rusqlite::params![cutoff],
        )
        .map_err(|e| format!("Failed to prune outbox: {}", e))?;
    Ok(deleted as i32)
}

fn get_max_log_lines_internal(
    repository: &crate::repository::LibraryRepository,
) -> Result<usize, String> {
    let settings = repository.get_settings().map_err(|e| format!("{}", e))?;
    let item = settings.iter().find(|s| s.key == SETTING_MAX_LOG_LINES_KEY);
    match item {
        Some(setting) => match serde_json::from_str::<serde_json::Value>(&setting.value_json) {
            Ok(val) => {
                if let Some(n) = val.as_u64() {
                    Ok(n as usize)
                } else {
                    Ok(DEFAULT_MAX_LOG_LINES)
                }
            }
            Err(_) => Ok(DEFAULT_MAX_LOG_LINES),
        },
        None => Ok(DEFAULT_MAX_LOG_LINES),
    }
}

#[cfg(test)]
mod outbox_tests {
    use super::*;
    use rusqlite::Connection;

    fn outbox_connection() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(include_str!("../../migrations/0013_sync_outbox.sql")).unwrap();
        conn
    }

    fn progress_payload(user_id: &str, book_id: &str, updated_at: &str) -> String {
        format!(
            r#"{{"userId":"{}","bookId":"{}","cfiLocation":"epubcfi(/6/2)","percentage":12.5,"updatedAt":"{}"}}"#,
            user_id, book_id, updated_at
        )
    }

    fn count_rows(conn: &Connection) -> i64 {
        conn.query_row("SELECT COUNT(*) FROM sync_outbox", [], |r| r.get(0)).unwrap()
    }

    #[test]
    fn coalesce_updates_existing_row_latest_wins() {
        let conn = outbox_connection();
        let first = add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        // Newer event for the same (user, book) updates the row in place.
        let second = add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:30Z"),
        )
        .unwrap();
        assert_eq!(first, second, "UPDATE path must return the existing row id");
        assert_eq!(count_rows(&conn), 1, "latest-wins must never grow the table");
        let stored: String = conn
            .query_row("SELECT payload_json FROM sync_outbox WHERE id = ?1", [&first], |r| r.get(0))
            .unwrap();
        assert!(stored.contains("10:00:30Z"), "payload must hold the latest event");
    }

    #[test]
    fn coalesce_stale_event_never_creates_duplicate() {
        let conn = outbox_connection();
        add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:30Z"),
        )
        .unwrap();
        // Older event arriving late: must NOT overwrite, must NOT insert a duplicate.
        add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        assert_eq!(count_rows(&conn), 1, "stale event must not create a second row");
        let stored: String =
            conn.query_row("SELECT payload_json FROM sync_outbox", [], |r| r.get(0)).unwrap();
        assert!(stored.contains("10:00:30Z"), "newest payload must survive");
    }

    #[test]
    fn coalesce_distinct_users_distinct_rows() {
        let conn = outbox_connection();
        add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u2", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        assert_eq!(count_rows(&conn), 2, "different users must keep separate rows");
    }

    #[test]
    fn coalesce_resets_retry_state() {
        let conn = outbox_connection();
        let id = add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        // Simulate a failed row with backoff.
        conn.execute(
            "UPDATE sync_outbox SET retry_count = 5, last_error = 'RLS denied', next_retry_at = '2030-01-01T00:00:00Z' WHERE id = ?1",
            [&id],
        )
        .unwrap();
        // A newer event coalesces: retry state must reset (fresh event, fresh attempt).
        add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:30Z"),
        )
        .unwrap();
        let (retry, last_error, next_retry): (i32, Option<String>, String) = conn
            .query_row(
                "SELECT retry_count, last_error, next_retry_at FROM sync_outbox WHERE id = ?1",
                [&id],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .unwrap();
        assert_eq!(retry, 0);
        assert!(last_error.is_none(), "coalescing must clear last_error");
        assert!(next_retry.as_str() < "2030-01-01T00:00:00Z", "next_retry_at must be reset to now");
    }

    #[test]
    fn coalesce_never_drops_rows() {
        let conn = outbox_connection();
        let id = add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z"),
        )
        .unwrap();
        // Coalescing must never delete: even after many merges the row survives.
        for i in 1..=10 {
            let ts = format!("2026-08-07T10:00:{}Z", i * 3);
            add_coalesced_sync_outbox_item_internal(
                &conn,
                "READING_PROGRESS",
                "book-1",
                "UPSERT",
                &progress_payload("u1", "book-1", &ts),
            )
            .unwrap();
        }
        let exists: i32 = conn
            .query_row("SELECT COUNT(*) FROM sync_outbox WHERE id = ?1", [&id], |r| r.get(0))
            .unwrap();
        assert_eq!(exists, 1, "row must survive coalescing");
    }

    #[test]
    fn coalesce_rejects_invalid_inputs() {
        let conn = outbox_connection();
        // Empty entity type
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "",
            "book-1",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z")
        )
        .is_err());
        // Empty entity id
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "",
            "UPSERT",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z")
        )
        .is_err());
        // DELETE operations are not coalescible (must not rewrite tombstones)
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "DELETE",
            &progress_payload("u1", "book-1", "2026-08-07T10:00:00Z")
        )
        .is_err());
        // Missing userId in payload
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            r#"{"bookId":"book-1","updatedAt":"2026-08-07T10:00:00Z"}"#,
        )
        .is_err());
        // Missing updatedAt in payload
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            r#"{"userId":"u1","bookId":"book-1"}"#,
        )
        .is_err());
        // Unparseable JSON
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            "not-json",
        )
        .is_err());
        // Oversized payload (R3 bound)
        let huge = format!(
            r#"{{"userId":"u1","bookId":"book-1","updatedAt":"2026-08-07T10:00:00Z","pad":"{}"}}"#,
            "x".repeat(MAX_COALESCE_PAYLOAD_BYTES)
        );
        assert!(add_coalesced_sync_outbox_item_internal(
            &conn,
            "READING_PROGRESS",
            "book-1",
            "UPSERT",
            &huge,
        )
        .is_err());
    }
}
