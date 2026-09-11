use super::map_command_error;
use crate::models::{IndexBookTextInput, SearchBookTextInput, SearchBookTextResponse};
use crate::state::AppState;
use tauri::State;

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
