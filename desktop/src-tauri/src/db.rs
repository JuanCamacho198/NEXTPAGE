use std::fs;
use std::path::{Path, PathBuf};

use chrono::Utc;
use rusqlite::Connection;
use rusqlite::OptionalExtension;
use tauri::{AppHandle, Manager};

use crate::error::AppResult;

const MIGRATIONS: [(&str, &str); 5] = [
    ("0001_init", include_str!("../migrations/0001_init.sql")),
    ("0002_books", include_str!("../migrations/0002_books.sql")),
    (
        "0003_highlights",
        include_str!("../migrations/0003_highlights.sql"),
    ),
    (
        "0004_desktop_feature_parity",
        include_str!("../migrations/0004_desktop_feature_parity.sql"),
    ),
    (
        "0005_hidden_books",
        include_str!("../migrations/0005_hidden_books.sql"),
    ),
];

pub fn resolve_db_path(app: &AppHandle) -> AppResult<PathBuf> {
    let app_data_dir = app.path().app_data_dir()?;
    fs::create_dir_all(&app_data_dir)?;
    Ok(app_data_dir.join("nextpage.db"))
}

pub fn open_and_migrate(db_path: &Path) -> AppResult<Connection> {
    let mut connection = Connection::open(db_path)?;
    connection.execute_batch("PRAGMA foreign_keys = ON;")?;

    connection.execute(
        "CREATE TABLE IF NOT EXISTS schema_migrations (
           name TEXT PRIMARY KEY,
           applied_at TEXT NOT NULL
         )",
        [],
    )?;

    for (name, sql) in MIGRATIONS {
        let applied: Option<i32> = connection
            .query_row(
                "SELECT 1 FROM schema_migrations WHERE name = ?1 LIMIT 1",
                [name],
                |row| row.get(0),
            )
            .optional()?;
        if applied.is_some() {
            continue;
        }

        let tx = connection.transaction()?;
        let migration_result = tx.execute_batch(sql);
        match migration_result {
            Ok(_) => {}
            Err(error)
                if (name == "0002_books" && is_duplicate_column_replay_safe(&tx)?)
                    || (name == "0005_hidden_books" && has_column(&tx, "books", "hidden_at")?) =>
            {
                // Existing DBs may already have these columns from pre-tracker launches.
                // Treat as previously applied once all expected columns are present.
                let _ = error;
            }
            Err(error) => return Err(error.into()),
        }

        tx.execute(
            "INSERT INTO schema_migrations (name, applied_at) VALUES (?1, ?2)",
            (name, Utc::now().to_rfc3339()),
        )?;
        tx.commit()?;
    }

    Ok(connection)
}

fn is_duplicate_column_replay_safe(connection: &Connection) -> AppResult<bool> {
    let mut statement = connection.prepare("PRAGMA table_info(books)")?;
    let rows = statement.query_map([], |row| row.get::<_, String>(1))?;
    let mut names: Vec<String> = Vec::new();
    for name in rows {
        names.push(name?);
    }

    Ok(["sync_status", "current_page", "total_pages"]
        .iter()
        .all(|required| names.iter().any(|name| name == required)))
}

fn has_column(connection: &Connection, table_name: &str, column_name: &str) -> AppResult<bool> {
    let mut statement = connection.prepare(&format!("PRAGMA table_info({})", table_name))?;
    let rows = statement.query_map([], |row| row.get::<_, String>(1))?;
    for name in rows {
        if name? == column_name {
            return Ok(true);
        }
    }
    Ok(false)
}
