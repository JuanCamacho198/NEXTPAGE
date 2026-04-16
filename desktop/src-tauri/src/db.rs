use std::fs;
use std::path::{Path, PathBuf};

use rusqlite::Connection;
use tauri::{AppHandle, Manager};

use crate::error::AppResult;

const MIGRATIONS: [&str; 3] = [
    include_str!("../migrations/0001_init.sql"),
    include_str!("../migrations/0002_books.sql"),
    include_str!("../migrations/0003_highlights.sql"),
];

pub fn resolve_db_path(app: &AppHandle) -> AppResult<PathBuf> {
    let app_data_dir = app.path().app_data_dir()?;
    fs::create_dir_all(&app_data_dir)?;
    Ok(app_data_dir.join("nextpage.db"))
}

pub fn open_and_migrate(db_path: &Path) -> AppResult<Connection> {
    let connection = Connection::open(db_path)?;
    connection.execute_batch("PRAGMA foreign_keys = ON;")?;

    for sql in MIGRATIONS {
        connection.execute_batch(sql)?;
    }

    Ok(connection)
}
