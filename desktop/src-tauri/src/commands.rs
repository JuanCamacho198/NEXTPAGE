use std::fs;
use std::path::PathBuf;

use tauri::State;

use crate::models::{
    BookmarkDto, BookDto, BookImportInput, HighlightDto, ReadingProgressDto, SaveBookmarkInput,
    SaveHighlightInput, SaveProgressInput,
};
use crate::state::AppState;

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
