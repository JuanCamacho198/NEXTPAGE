#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use nextpage_desktop::commands;
use nextpage_desktop::db::{open_and_migrate, resolve_db_path};
use nextpage_desktop::queue::repository::QueueRepository;
use nextpage_desktop::repository::LibraryRepository;
use nextpage_desktop::state::AppState;
use rusqlite::Connection;
use tauri::{AppHandle, Manager};
use tauri_plugin_deep_link::DeepLinkExt;

fn build_state(app: &AppHandle) -> Result<AppState, String> {
    let db_path = resolve_db_path(app).map_err(|err| err.to_string())?;
    let connection = open_and_migrate(&db_path).map_err(|err| err.to_string())?;
    let queue_connection = Connection::open(&db_path).map_err(|err| err.to_string())?;
    queue_connection.execute_batch("PRAGMA foreign_keys = ON;").map_err(|err| err.to_string())?;
    let repository = LibraryRepository::new(connection);
    let queue_repository = QueueRepository::new(queue_connection);
    let app_data_dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    Ok(AppState::new(repository, queue_repository, app_data_dir, db_path))
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_deep_link::init())
        .plugin(tauri_plugin_oauth::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_os::init())
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
            commands::listBooks,
            commands::upsertBook,
            commands::getSettings,
            commands::upsertSettings,
            commands::listLibraryBooks,
            commands::scanFolder,
            commands::getProgress,
            commands::saveProgress,
            commands::upsertProgress,
            commands::saveReadingSession,
            commands::getReadingStats,
            commands::getReadingActivity,
            commands::getReadingStatsForRange,
            commands::getReadingStreak,
            commands::upsertRemoteReadingSessions,
            commands::indexBookText,
            commands::searchBookText,
            commands::importBook,
            commands::deleteBook,
            commands::hideBookFromLibrary,
            commands::getFileBytes,
            commands::getFileSize,
            commands::readFileRange,
            commands::updateBookProgress,
            commands::fileExists,
            commands::saveBookFile,
            commands::upsertBookCover,
            commands::extractEpubCover,
            commands::deleteBookCover,
            commands::listHighlights,
            commands::saveHighlight,
            commands::updateHighlight,
            commands::deleteHighlight,
            commands::listTags,
            commands::listTagsForHighlight,
            commands::createTag,
            commands::saveHighlightTags,
            commands::listDictionaryWords,
            commands::addDictionaryWord,
            commands::removeDictionaryWord,
            commands::listBookmarks,
            commands::saveBookmark,
            commands::deleteBookmark,
            commands::createCollection,
            commands::deleteCollection,
            commands::listCollections,
            commands::addBookToCollection,
            commands::removeBookFromCollection,
            commands::getBookCollections,
            commands::setReadingStatus,
            commands::addSyncOutboxItem,
            commands::addCoalescedSyncOutboxItem,
            commands::listSyncOutboxReady,
            commands::markSyncOutboxFailed,
            commands::deleteSyncOutboxItem,
            commands::pruneSyncOutbox,
            commands::reportErrorEvent,
            commands::logEvent,
            commands::diagnose,
            commands::getLogs,
            commands::parse_epub,
            commands::get_epub_chapter,
            commands::get_epub_resource,
            commands::is_epub_cached,
            commands::clear_epub_cache,
            commands::index_epub_text
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
