#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod db;
mod error;
mod models;
mod repository;
mod state;

use db::{open_and_migrate, resolve_db_path};
use repository::LibraryRepository;
use state::AppState;
use tauri::Manager;

fn build_state(app: &tauri::AppHandle) -> Result<AppState, String> {
    let db_path = resolve_db_path(app).map_err(|err| err.to_string())?;
    let connection = open_and_migrate(&db_path).map_err(|err| err.to_string())?;
    let repository = LibraryRepository::new(connection);
    Ok(AppState::new(repository))
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            let state = build_state(app.handle()).map_err(std::io::Error::other)?;
            app.manage(state);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::list_books,
            commands::get_progress,
            commands::save_progress
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
