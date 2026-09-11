use super::map_command_error;
use crate::state::AppState;
use std::fs;
use std::path::PathBuf;
use tauri::State;

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileBytes(file_path: String) -> Result<Vec<u8>, String> {
    let path = PathBuf::from(&file_path);
    fs::read(&path).map_err(|err| format!("Failed to read file: {}", err))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn getFileSize(file_path: String) -> Result<u64, String> {
    let path = PathBuf::from(&file_path);
    let metadata =
        std::fs::metadata(&path).map_err(|err| format!("Failed to read file metadata: {}", err))?;
    Ok(metadata.len())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn readFileRange(file_path: String, offset: u64, length: u64) -> Result<Vec<u8>, String> {
    use std::io::{Read, Seek, SeekFrom};
    let path = PathBuf::from(&file_path);
    let mut file =
        std::fs::File::open(&path).map_err(|err| format!("Failed to open file: {}", err))?;
    file.seek(SeekFrom::Start(offset)).map_err(|err| format!("Failed to seek in file: {}", err))?;
    let mut buf = vec![0u8; length as usize];
    let n = file.read(&mut buf).map_err(|err| format!("Failed to read file: {}", err))?;
    buf.truncate(n);
    Ok(buf)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn updateBookProgress(
    state: State<'_, AppState>,
    book_id: String,
    current_page: i32,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.update_book_progress(&book_id, current_page).map_err(|e| format!("{}", e))
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn fileExists(path: String) -> Result<bool, String> {
    Ok(PathBuf::from(&path).exists())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub async fn saveBookFile(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    id: String,
    data: Vec<u8>,
    title: Option<String>,
    author: Option<String>,
    format: Option<String>,
) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository
        .save_book_file(&app, &id, &data, title.as_deref(), author.as_deref(), format.as_deref())
        .map_err(map_command_error)
}
