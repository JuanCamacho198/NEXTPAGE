// TRANSITION FACADE: keep public command names stable (snake_case + camelCase aliases) during backend refactor.
// Rollback: revert per feature module by restoring previous wrapper exports without touching invoke_handler symbols.

pub mod settings;
pub mod library;
pub mod progress;
pub mod highlights;
pub mod bookmarks;
pub mod collections;
pub mod search;
pub mod files;

#[allow(unused_imports)]
pub use settings::*;
#[allow(unused_imports)]
pub use library::*;
#[allow(unused_imports)]
pub use progress::*;
#[allow(unused_imports)]
pub use highlights::*;
#[allow(unused_imports)]
pub use bookmarks::*;
#[allow(unused_imports)]
pub use collections::*;
#[allow(unused_imports)]
pub use search::*;
#[allow(unused_imports)]
pub use files::*;

use std::fs;
use std::path::PathBuf;

use tauri::State;

use crate::db::verify_queue_health;
use crate::error::AppError;
use crate::logger::{ErrorEventDto, LogEventDto, LogLevel, DEFAULT_MAX_LOG_LINES, SETTING_MAX_LOG_LINES_KEY};
use crate::models::{
    AppSettingDto, BookCollectionInput, BookDeleteInput, BookDto, BookImportInput, BookmarkDto,
    CollectionDto, CommandErrorDto, CreateCollectionInput, HideBookInput, HighlightDto,
    IndexBookTextInput, LibraryBookDto, ListLibraryBooksInput, ReadingProgressDto,
    ReadingSessionInput, ReadingStatsSummaryDto, SaveBookmarkInput, SaveHighlightInput,
    SaveProgressInput, ScanFolderResultDto, SearchBookTextInput, SearchBookTextResponse,
    UpsertBookCoverInput,
};
use crate::state::AppState;

const LIBRARY_RESPONSE_VERSION: i32 = 1;

fn map_command_error(error: AppError) -> String {
    let dto = match error {
        AppError::InvalidInput(message) => CommandErrorDto::validation(message),
        AppError::Compatibility(message) => CommandErrorDto::compatibility(message),
        AppError::DbConstraint(message) => CommandErrorDto::db_constraint(message),
        AppError::SyncConflict(message) => CommandErrorDto::sync_conflict(message),
        AppError::ImportError(message) => CommandErrorDto::import_error(message),
        AppError::ThumbnailFail(message) => CommandErrorDto::thumbnail_error(message),
        AppError::MigrationFail(message) => CommandErrorDto::migration_fail(message),
        other => CommandErrorDto::internal(other.to_string()),
    };

    serde_json::to_string(&dto).unwrap_or_else(|_| {
        "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Command failed\",\"recoverable\":false}"
            .to_string()
    })
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_books(state: State<'_, AppState>) -> Result<Vec<BookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_books().map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBooks(state: State<'_, AppState>) -> Result<Vec<BookDto>, String> {
    list_books(state)
}

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_book(state: State<'_, AppState>, book: BookDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_book(book).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertBook(state: State<'_, AppState>, book: BookDto) -> Result<(), String> {
    upsert_book(state, book)
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_settings(state: State<'_, AppState>) -> Result<Vec<AppSettingDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    // Return empty if schema not ready
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(vec![]);
    }
    repository.get_settings().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getSettings(state: State<'_, AppState>) -> Result<Vec<AppSettingDto>, String> {
    get_settings(state)
}

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_settings(
    state: State<'_, AppState>,
    settings: Vec<AppSettingDto>,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    // No-op if schema not ready
    if !repository.has_desktop_parity_schema().unwrap_or(true) {
        return Ok(());
    }
    repository
        .upsert_settings(settings)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertSettings(
    state: State<'_, AppState>,
    settings: Vec<AppSettingDto>,
) -> Result<(), String> {
    upsert_settings(state, settings)
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_library_books(
    state: State<'_, AppState>,
    payload: Option<ListLibraryBooksInput>,
) -> Result<Vec<LibraryBookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    list_library_books_internal(&repository, payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listLibraryBooks(
    state: State<'_, AppState>,
    payload: Option<ListLibraryBooksInput>,
) -> Result<Vec<LibraryBookDto>, String> {
    list_library_books(state, payload)
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

#[tauri::command(rename_all = "camelCase")]
pub fn scan_folder(
    state: State<'_, AppState>,
    path: String,
) -> Result<ScanFolderResultDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.scan_folder(&path).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn scanFolder(state: State<'_, AppState>, path: String) -> Result<ScanFolderResultDto, String> {
    scan_folder(state, path)
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_progress(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Option<ReadingProgressDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .get_progress(&book_id)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getProgress(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Option<ReadingProgressDto>, String> {
    get_progress(state, book_id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_progress(state: State<'_, AppState>, payload: SaveProgressInput) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_progress(payload)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveProgress(state: State<'_, AppState>, payload: SaveProgressInput) -> Result<(), String> {
    save_progress(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_progress(
    state: State<'_, AppState>,
    progress: ReadingProgressDto,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .upsert_progress(progress)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertProgress(
    state: State<'_, AppState>,
    progress: ReadingProgressDto,
) -> Result<(), String> {
    upsert_progress(state, progress)
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_reading_session(
    state: State<'_, AppState>,
    payload: ReadingSessionInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_reading_session(payload)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveReadingSession(
    state: State<'_, AppState>,
    payload: ReadingSessionInput,
) -> Result<(), String> {
    save_reading_session(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_reading_stats(
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
    repository
        .get_reading_stats(book_id.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getReadingStats(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<ReadingStatsSummaryDto, String> {
    get_reading_stats(state, book_id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn index_book_text(
    state: State<'_, AppState>,
    payload: IndexBookTextInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .index_book_text(payload)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn indexBookText(
    state: State<'_, AppState>,
    payload: IndexBookTextInput,
) -> Result<(), String> {
    index_book_text(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn search_book_text(
    state: State<'_, AppState>,
    payload: SearchBookTextInput,
) -> Result<SearchBookTextResponse, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .search_book_text(payload)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn searchBookText(
    state: State<'_, AppState>,
    payload: SearchBookTextInput,
) -> Result<SearchBookTextResponse, String> {
    search_book_text(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub async fn import_book(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    input: BookImportInput,
) -> Result<BookDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .import_book(app, input)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn importBook(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    input: BookImportInput,
) -> Result<BookDto, String> {
    import_book(app, state, input).await
}

#[tauri::command(rename_all = "camelCase")]
pub fn delete_book(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: BookDeleteInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .delete_book(app, payload)
        .map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn hide_book_from_library(
    state: State<'_, AppState>,
    payload: HideBookInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .hide_book_from_library(&payload.book_id)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn hideBookFromLibrary(
    state: State<'_, AppState>,
    payload: HideBookInput,
) -> Result<(), String> {
    hide_book_from_library(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub async fn get_file_bytes(file_path: String) -> Result<Vec<u8>, String> {
    let path = PathBuf::from(&file_path);
    fs::read(&path).map_err(|err| format!("Failed to read file: {}", err))
}

#[tauri::command(rename_all = "camelCase")]
pub async fn get_file_size(file_path: String) -> Result<u64, String> {
    let path = PathBuf::from(&file_path);
    let metadata = std::fs::metadata(&path).map_err(|err| format!("Failed to read file metadata: {}", err))?;
    Ok(metadata.len())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileSize(file_path: String) -> Result<u64, String> {
    get_file_size(file_path).await
}

#[tauri::command(rename_all = "camelCase")]
pub async fn read_file_range(
    file_path: String,
    offset: u64,
    length: u64,
) -> Result<Vec<u8>, String> {
    use std::io::{Read, Seek, SeekFrom};
    let path = PathBuf::from(&file_path);
    let mut file = std::fs::File::open(&path).map_err(|err| format!("Failed to open file: {}", err))?;
    file.seek(SeekFrom::Start(offset))
        .map_err(|err| format!("Failed to seek in file: {}", err))?;
    let mut buf = vec![0u8; length as usize];
    let n = file.read(&mut buf).map_err(|err| format!("Failed to read file: {}", err))?;
    buf.truncate(n);
    Ok(buf)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn readFileRange(
    file_path: String,
    offset: u64,
    length: u64,
) -> Result<Vec<u8>, String> {
    read_file_range(file_path, offset, length).await
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileBytes(file_path: String) -> Result<Vec<u8>, String> {
    get_file_bytes(file_path).await
}

#[tauri::command(rename_all = "camelCase")]
pub async fn update_book_progress(
    state: State<'_, AppState>,
    book_id: String,
    current_page: i32,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .update_book_progress(&book_id, current_page)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn updateBookProgress(
    state: State<'_, AppState>,
    book_id: String,
    current_page: i32,
) -> Result<(), String> {
    update_book_progress(state, book_id, current_page).await
}

#[tauri::command(rename_all = "camelCase")]
pub async fn file_exists(path: String) -> Result<bool, String> {
    Ok(PathBuf::from(&path).exists())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn fileExists(path: String) -> Result<bool, String> {
    file_exists(path).await
}

#[tauri::command(rename_all = "camelCase")]
pub async fn save_book_file(
    state: State<'_, AppState>,
    id: String,
    data: Vec<u8>,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_book_file(&id, &data)
        .map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_book_cover(
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
pub fn upsertBookCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: UpsertBookCoverInput,
) -> Result<(), String> {
    upsert_book_cover(app, state, payload)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn saveBookFile(
    state: State<'_, AppState>,
    id: String,
    data: Vec<u8>,
) -> Result<(), String> {
    save_book_file(state, id, data).await
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_highlights(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<HighlightDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .list_highlights(book_id.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listHighlights(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<HighlightDto>, String> {
    list_highlights(state, book_id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_highlight(
    state: State<'_, AppState>,
    payload: SaveHighlightInput,
) -> Result<HighlightDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_highlight(payload)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveHighlight(
    state: State<'_, AppState>,
    highlight: SaveHighlightInput,
) -> Result<HighlightDto, String> {
    save_highlight(state, highlight)
}

#[tauri::command(rename_all = "camelCase")]
pub fn delete_highlight(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .delete_highlight(&id)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteHighlight(state: State<'_, AppState>, id: String) -> Result<(), String> {
    delete_highlight(state, id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_bookmarks(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<BookmarkDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .list_bookmarks(book_id.as_deref())
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBookmarks(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<BookmarkDto>, String> {
    list_bookmarks(state, book_id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_bookmark(
    state: State<'_, AppState>,
    payload: SaveBookmarkInput,
) -> Result<BookmarkDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_bookmark(payload)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveBookmark(
    state: State<'_, AppState>,
    bookmark: SaveBookmarkInput,
) -> Result<BookmarkDto, String> {
    save_bookmark(state, bookmark)
}

#[tauri::command(rename_all = "camelCase")]
pub fn delete_bookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .delete_bookmark(&id)
        .map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    delete_bookmark(state, id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn create_collection(
    state: State<'_, AppState>,
    payload: CreateCollectionInput,
) -> Result<CollectionDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .create_collection(&payload.name, payload.color.as_deref())
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn createCollection(
    state: State<'_, AppState>,
    payload: CreateCollectionInput,
) -> Result<CollectionDto, String> {
    create_collection(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn delete_collection(state: State<'_, AppState>, id: i64) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_collection(id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteCollection(state: State<'_, AppState>, id: i64) -> Result<(), String> {
    delete_collection(state, id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_collections(state: State<'_, AppState>) -> Result<Vec<CollectionDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_collections().map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listCollections(state: State<'_, AppState>) -> Result<Vec<CollectionDto>, String> {
    list_collections(state)
}

#[tauri::command(rename_all = "camelCase")]
pub fn add_book_to_collection(
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
pub fn addBookToCollection(
    state: State<'_, AppState>,
    payload: BookCollectionInput,
) -> Result<(), String> {
    add_book_to_collection(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn remove_book_from_collection(
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
pub fn removeBookFromCollection(
    state: State<'_, AppState>,
    payload: BookCollectionInput,
) -> Result<(), String> {
    remove_book_from_collection(state, payload)
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_book_collections(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Vec<CollectionDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .get_book_collections(&book_id)
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getBookCollections(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Vec<CollectionDto>, String> {
    get_book_collections(state, book_id)
}

#[tauri::command(rename_all = "camelCase")]
pub fn report_error_event(
    state: State<'_, AppState>,
    event: ErrorEventDto,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_to_file(&event, max_lines)
}

#[tauri::command(rename_all = "camelCase")]
pub fn log_event(
    state: State<'_, AppState>,
    event: LogEventDto,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_generic(&event, max_lines)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DiagnoseResult {
    pub database: String,
    pub queue: String,
    pub filesystem: String,
    pub log_file: String,
    pub details: std::collections::HashMap<String, serde_json::Value>,
}

#[tauri::command(rename_all = "camelCase")]
pub fn diagnose(state: State<'_, AppState>) -> DiagnoseResult {
    let mut details = std::collections::HashMap::new();

    // Database health
    let (database_status, db_detail) = match state.repository.lock() {
        Ok(repo) => {
            match repo.connection() {
                Ok(conn) => {
                    match conn.query_row("SELECT 1", [], |row| row.get::<_, i32>(0)) {
                        Ok(1) => {
                            details.insert("db_query_ok".to_string(), serde_json::Value::Bool(true));
                            ("healthy".to_string(), "DB responded OK".to_string())
                        },
                        Err(e) => {
                            details.insert("db_error".to_string(), serde_json::Value::String(e.to_string()));
                            ("degraded".to_string(), format!("DB query failed: {}", e))
                        }
                    }
                },
                Err(e) => {
                    details.insert("db_error".to_string(), serde_json::Value::String(e.to_string()));
                    ("degraded".to_string(), format!("DB connection failed: {}", e))
                }
            }
        },
        Err(e) => {
            details.insert("db_lock_error".to_string(), serde_json::Value::String(e.to_string()));
            ("unhealthy".to_string(), format!("DB lock failed: {}", e))
        }
    };

    // Queue health
    let (queue_status, queue_detail) = match state.queue_repository.lock() {
        Ok(queue_repo) => {
            match queue_repo.connection() {
                Ok(conn) => {
                    match crate::db::verify_queue_health(conn) {
                        Ok(health) => {
                            let status = if health.warnings.is_empty() { "healthy" } else { "degraded" };
                            details.insert("queue_warnings".to_string(), serde_json::Value::Array(
                                health.warnings.iter().map(|w| serde_json::Value::String(w.clone())).collect()
                            ));
                            (status.to_string(), format!("Queue health: {:?}", health.status))
                        },
                        Err(e) => ("degraded".to_string(), format!("Queue check failed: {}", e)),
                    }
                },
                Err(e) => ("degraded".to_string(), format!("Queue connection failed: {}", e)),
            }
        },
        Err(e) => ("unhealthy".to_string(), format!("Queue lock failed: {}", e)),
    };

    // Filesystem health
    let (fs_status, fs_detail) = match state.repository.lock() {
        Ok(repo) => {
            let books = repo.list_books().unwrap_or_default();
            let missing = books.iter().filter(|b| !std::path::Path::new(&b.file_path).exists()).count();
            details.insert("total_books".to_string(), serde_json::Value::Number(serde_json::Number::from(books.len())));
            details.insert("missing_files".to_string(), serde_json::Value::Number(serde_json::Number::from(missing)));
            if missing > 0 {
                ("degraded".to_string(), format!("{} of {} book files missing", missing, books.len()))
            } else {
                ("healthy".to_string(), format!("{} book files OK", books.len()))
            }
        },
        Err(e) => ("unhealthy".to_string(), format!("FS check failed: {}", e)),
    };

    // Log file
    let (log_status, log_detail) = match state.logger.lock() {
        Ok(logger) => {
            let log_path = logger.get_log_path().clone();
            if log_path.exists() {
                match log_path.metadata() {
                    Ok(meta) => {
                        details.insert("log_size_bytes".to_string(), serde_json::Value::Number(serde_json::Number::from(meta.len())));
                        details.insert("log_path".to_string(), serde_json::Value::String(log_path.to_string_lossy().to_string()));
                        ("healthy".to_string(), format!("Log file exists ({} bytes)", meta.len()))
                    },
                    Err(e) => ("degraded".to_string(), format!("Log metadata error: {}", e)),
                }
            } else {
                ("healthy".to_string(), "No log file yet".to_string())
            }
        },
        Err(e) => ("unhealthy".to_string(), format!("Logger lock failed: {}", e)),
    };

    DiagnoseResult {
        database: database_status,
        queue: queue_status,
        filesystem: fs_status,
        log_file: log_status,
        details,
    }
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_logs(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.read_all_logs()
}

fn get_max_log_lines_internal(repository: &crate::repository::LibraryRepository) -> Result<usize, String> {
    let settings = repository.get_settings().map_err(|e| format!("{}", e))?;
    let item = settings.iter().find(|s| s.key == SETTING_MAX_LOG_LINES_KEY);
    match item {
        Some(setting) => {
            match serde_json::from_str::<serde_json::Value>(&setting.value_json) {
                Ok(val) => {
                    if let Some(n) = val.as_u64() {
                        Ok(n as usize)
                    } else {
                        Ok(DEFAULT_MAX_LOG_LINES)
                    }
                },
                Err(_) => Ok(DEFAULT_MAX_LOG_LINES),
            }
        },
        None => Ok(DEFAULT_MAX_LOG_LINES),
    }
}





