#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use nextpage_desktop::commands;
use nextpage_desktop::db::{open_and_migrate, resolve_db_path};
use nextpage_desktop::repository::LibraryRepository;
use nextpage_desktop::state::AppState;
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
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
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
            commands::get_settings,
            commands::upsert_settings,
            commands::list_library_books,
            commands::get_progress,
            commands::save_progress,
            commands::upsert_progress,
            commands::save_reading_session,
            commands::get_reading_stats,
            commands::index_book_text,
            commands::search_book_text,
            commands::import_book,
            commands::delete_book,
            commands::get_file_bytes,
            commands::update_book_progress,
            commands::list_highlights,
            commands::save_highlight,
            commands::delete_highlight,
            commands::list_bookmarks,
            commands::save_bookmark,
            commands::delete_bookmark
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
