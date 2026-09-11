use super::map_command_error;
use crate::models::AppSettingDto;
use crate::state::AppState;
use tauri::State;

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
