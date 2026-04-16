use std::fs;
use std::path::PathBuf;

use tauri::State;

use crate::error::AppError;
use crate::models::{
    AppSettingDto, BookDeleteInput, BookmarkDto, BookDto, BookImportInput, CommandErrorDto,
    HighlightDto, IndexBookTextInput, LibraryBookDto, ListLibraryBooksInput, ReadingProgressDto,
    ReadingSessionInput, ReadingStatsSummaryDto, SaveBookmarkInput, SaveHighlightInput,
    SaveProgressInput, SearchBookTextInput, SearchBookTextResponse,
};
use crate::state::AppState;

const LIBRARY_RESPONSE_VERSION: i32 = 1;

fn map_command_error(error: AppError) -> String {
    let dto = match error {
        AppError::InvalidInput(message) => CommandErrorDto::validation(message),
        AppError::Compatibility(message) => CommandErrorDto::compatibility(message),
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

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_book(state: State<'_, AppState>, book: BookDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_book(book).map_err(|e| format!("{}", e))
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_settings(state: State<'_, AppState>) -> Result<Vec<AppSettingDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.get_settings().map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn upsert_settings(
    state: State<'_, AppState>,
    settings: Vec<AppSettingDto>,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .upsert_settings(settings)
        .map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_library_books(
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
        return Err(AppError::Compatibility(
            "Desktop parity schema is not available. Please run migrations and retry.".to_string(),
        ));
    }

    repository.list_library_books()
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

#[tauri::command(rename_all = "camelCase")]
pub fn save_progress(state: State<'_, AppState>, payload: SaveProgressInput) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_progress(payload)
        .map_err(|e| format!("{}", e))
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

#[tauri::command(rename_all = "camelCase")]
pub fn get_reading_stats(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<ReadingStatsSummaryDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .get_reading_stats(book_id.as_deref())
        .map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn index_book_text(
    state: State<'_, AppState>,
    payload: IndexBookTextInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.index_book_text(payload).map_err(map_command_error)
}

#[tauri::command(rename_all = "camelCase")]
pub fn search_book_text(
    state: State<'_, AppState>,
    payload: SearchBookTextInput,
) -> Result<SearchBookTextResponse, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.search_book_text(payload).map_err(map_command_error)
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
pub async fn get_file_bytes(file_path: String) -> Result<Vec<u8>, String> {
    let path = PathBuf::from(&file_path);
    fs::read(&path).map_err(|err| format!("Failed to read file: {}", err))
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

#[tauri::command(rename_all = "camelCase")]
pub fn list_highlights(state: State<'_, AppState>, book_id: String) -> Result<Vec<HighlightDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .list_highlights(&book_id)
        .map_err(|e| format!("{}", e))
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_highlight(
    state: State<'_, AppState>,
    payload: SaveHighlightInput,
) -> Result<HighlightDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_highlight(payload)
        .map_err(|e| format!("{}", e))
}

#[tauri::command(rename_all = "camelCase")]
pub fn delete_highlight(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_highlight(&id).map_err(|e| format!("{}", e))
}

#[tauri::command(rename_all = "camelCase")]
pub fn list_bookmarks(state: State<'_, AppState>, book_id: String) -> Result<Vec<BookmarkDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .list_bookmarks(&book_id)
        .map_err(|e| format!("{}", e))
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

#[tauri::command(rename_all = "camelCase")]
pub fn delete_bookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_bookmark(&id).map_err(|e| format!("{}", e))
}
