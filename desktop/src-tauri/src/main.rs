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
use tauri::{AppHandle, Manager};
use tauri_plugin_deep_link::DeepLinkExt;

fn build_state(app: &AppHandle) -> Result<AppState, String> {
    let db_path = resolve_db_path(app).map_err(|err| err.to_string())?;
    let connection = open_and_migrate(&db_path).map_err(|err| err.to_string())?;
    let repository = LibraryRepository::new(connection);
    Ok(AppState::new(repository))
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_deep_link::init())
        .setup(|app| {
            let state = build_state(app.handle()).map_err(std::io::Error::other)?;
            app.manage(state);

            #[cfg(desktop)]
            app.deep_link().register("nextpage-desktop")?;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::list_books,
            commands::upsert_book,
            commands::get_progress,
            commands::save_progress,
            commands::upsert_progress
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
