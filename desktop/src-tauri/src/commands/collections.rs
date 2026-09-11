use super::map_command_error;
use crate::models::{BookCollectionInput, CollectionDto, CreateCollectionInput};
use crate::state::AppState;
use tauri::State;

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
