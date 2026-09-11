use super::list_library_books_internal;
use super::map_command_error;
use crate::models::{
    BookDeleteInput, BookDto, BookImportInput, HideBookInput, LibraryBookDto,
    ListLibraryBooksInput, ScanFolderResultDto, UpsertBookCoverInput,
};
use crate::state::AppState;
use tauri::State;

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listBooks(state: State<'_, AppState>) -> Result<Vec<BookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.list_books().map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertBook(state: State<'_, AppState>, book: BookDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.upsert_book(book).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn listLibraryBooks(
    state: State<'_, AppState>,
    payload: Option<ListLibraryBooksInput>,
) -> Result<Vec<LibraryBookDto>, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    list_library_books_internal(&repository, payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn scanFolder(state: State<'_, AppState>, path: String) -> Result<ScanFolderResultDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.scan_folder(&path).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn importBook(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    input: BookImportInput,
) -> Result<BookDto, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.import_book(app, input).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBook(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: BookDeleteInput,
) -> Result<(), String> {
    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_book(app, payload).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn hideBookFromLibrary(
    state: State<'_, AppState>,
    payload: HideBookInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.hide_book_from_library(&payload.book_id).map_err(map_command_error)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn upsertBookCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    payload: UpsertBookCoverInput,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .upsert_book_cover_from_bytes(
            &app,
            &payload.book_id,
            &payload.data,
            payload.mime_type.as_deref(),
        )
        .map_err(map_command_error)?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn deleteBookCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    book_id: String,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.delete_book_cover(&app, &book_id).map_err(map_command_error)?;
    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn extractEpubCover(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    book_id: String,
    file_path: String,
) -> Result<bool, String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let path = std::path::PathBuf::from(&file_path);
    match repository.extract_epub_cover(&app, &path, &book_id) {
        Ok(Some(_)) => Ok(true),
        Ok(None) => Ok(false),
        Err(e) => Err(format!("{}", e)),
    }
}
