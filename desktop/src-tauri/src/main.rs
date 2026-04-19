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
            commands::listBooks,
            commands::upsert_book,
            commands::upsertBook,
            commands::get_settings,
            commands::getSettings,
            commands::upsert_settings,
            commands::upsertSettings,
            commands::list_library_books,
            commands::listLibraryBooks,
            commands::scan_folder,
            commands::scanFolder,
            commands::get_progress,
            commands::getProgress,
            commands::save_progress,
            commands::saveProgress,
            commands::upsert_progress,
            commands::upsertProgress,
            commands::save_reading_session,
            commands::saveReadingSession,
            commands::get_reading_stats,
            commands::getReadingStats,
            commands::index_book_text,
            commands::indexBookText,
            commands::search_book_text,
            commands::searchBookText,
            commands::import_book,
            commands::importBook,
            commands::delete_book,
            commands::hide_book_from_library,
            commands::hideBookFromLibrary,
            commands::get_file_bytes,
            commands::getFileBytes,
            commands::update_book_progress,
            commands::updateBookProgress,
            commands::file_exists,
            commands::fileExists,
            commands::save_book_file,
            commands::saveBookFile,
            commands::upsert_book_cover,
            commands::upsertBookCover,
            commands::list_highlights,
            commands::listHighlights,
            commands::save_highlight,
            commands::saveHighlight,
            commands::delete_highlight,
            commands::deleteHighlight,
            commands::list_bookmarks,
            commands::listBookmarks,
            commands::save_bookmark,
            commands::saveBookmark,
            commands::deleteBookmark,
            commands::delete_bookmark,
            commands::create_collection,
            commands::createCollection,
            commands::delete_collection,
            commands::deleteCollection,
            commands::list_collections,
            commands::listCollections,
            commands::add_book_to_collection,
            commands::addBookToCollection,
            commands::remove_book_from_collection,
            commands::removeBookFromCollection,
            commands::get_book_collections,
            commands::getBookCollections
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
