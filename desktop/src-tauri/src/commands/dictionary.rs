use super::map_command_error;
use crate::models::{AddDictionaryWordInput, DictionaryWordDto};
use crate::state::AppState;
use tauri::State;

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
pub fn updateDictionaryWord(
    state: State<'_, AppState>,
    payload: crate::models::UpdateDictionaryWordInput,
) -> Result<DictionaryWordDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.update_dictionary_word(payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn searchDictionaryWords(
    state: State<'_, AppState>,
    payload: crate::models::SearchDictionaryWordsInput,
) -> Result<Vec<DictionaryWordDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .search_dictionary_words(
            &payload.query,
            payload.limit.unwrap_or(20),
            payload.fuzzy.unwrap_or(false),
            payload.user_id.as_deref(),
        )
        .map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn exportDictionary(state: State<'_, AppState>, format: String) -> Result<String, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.export_dictionary(&format).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn importDictionary(
    state: State<'_, AppState>,
    payload: String,
    format: String,
    user_id: Option<String>,
) -> Result<crate::models::ImportDictionaryResult, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.import_dictionary(&payload, &format, user_id.as_deref()).map_err(map_command_error)
}
