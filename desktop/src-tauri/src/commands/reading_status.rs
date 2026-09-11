use super::map_command_error;
use crate::state::AppState;
use tauri::State;

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
