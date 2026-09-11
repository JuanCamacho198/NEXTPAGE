use super::map_command_error;
use crate::models::{
    CreateTagInput, HighlightDto, RemoteHighlightRow, SaveHighlightInput, SaveHighlightTagsInput,
    TagDto, UpdateHighlightInput, UpsertRemoteSummary,
};
use crate::state::AppState;
use tauri::State;

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertRemoteHighlights(
    state: State<'_, AppState>,
    rows: Vec<RemoteHighlightRow>,
) -> Result<UpsertRemoteSummary, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_remote_highlights(&rows).map_err(map_command_error)
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
