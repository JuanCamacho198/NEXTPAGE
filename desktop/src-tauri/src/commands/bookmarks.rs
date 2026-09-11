use crate::models::{BookmarkDto, SaveBookmarkInput};
use crate::state::AppState;
use tauri::State;

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBookmarks(
    state: State<'_, AppState>,
    book_id: Option<String>,
) -> Result<Vec<BookmarkDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_bookmarks(book_id.as_deref()).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn saveBookmark(
    state: State<'_, AppState>,
    payload: SaveBookmarkInput,
) -> Result<BookmarkDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.save_bookmark(payload).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_bookmark(&id).map_err(|e| format!("{}", e))
}
