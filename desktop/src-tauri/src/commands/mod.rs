// Facade cleanup: only camelCase commands remain.
// All snake_case variants and their camelCase aliases have been merged.
// Internal helpers (_internal suffix) kept as snake_case.

pub mod bookmarks;
pub mod collections;
pub mod epub_reader;
pub mod files;
pub mod highlights;
pub mod library;
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
    ReadingStatsSummaryDto, SaveBookmarkInput, SaveHighlightInput, SaveHighlightTagsInput,
    SaveProgressInput, ScanFolderResultDto, SearchBookTextInput, SearchBookTextResponse, TagDto,
    UpdateHighlightInput, UpsertBookCoverInput,
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
) -> Result<(), String> {
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
) -> Result<i64, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(0);
    }
    repository.get_reading_streak(book_id.as_deref()).map_err(map_command_error)
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
    state: State<'_, AppState>,
    id: String,
    data: Vec<u8>,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_book_file(&id, &data).map_err(map_command_error)
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
    repository
        .set_reading_status(&book_id, status.as_deref())
        .map_err(map_command_error)
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
