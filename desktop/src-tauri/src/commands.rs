use tauri::State;

use crate::models::{BookDto, ReadingProgressDto, SaveProgressInput};
use crate::state::AppState;

#[tauri::command(rename_all = "camelCase")]
pub fn list_books(state: State<'_, AppState>) -> Result<Vec<BookDto>, String> {
    let repository = state.repository.lock().map_err(|err| err.to_string())?;
    repository.list_books().map_err(|err| err.to_string())
}

#[tauri::command(rename_all = "camelCase")]
pub fn get_progress(
    state: State<'_, AppState>,
    book_id: String,
) -> Result<Option<ReadingProgressDto>, String> {
    let repository = state.repository.lock().map_err(|err| err.to_string())?;
    repository
        .get_progress(&book_id)
        .map_err(|err| err.to_string())
}

#[tauri::command(rename_all = "camelCase")]
pub fn save_progress(state: State<'_, AppState>, payload: SaveProgressInput) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|err| err.to_string())?;
    repository
        .save_progress(payload)
        .map_err(|err| err.to_string())
}
