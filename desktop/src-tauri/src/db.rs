use std::fs;
use std::path::{Path, PathBuf};

use chrono::Utc;
use rusqlite::OptionalExtension;
use rusqlite::{params, Connection};
use tauri::{AppHandle, Manager};

use crate::error::{AppError, AppResult};

const MIGRATIONS: [(&str, &str); 8] = [
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
    (
        "0006_collections",
        include_str!("../migrations/0006_collections.sql"),
    ),
    (
        "0007_highlight_note_and_page_contract",
        include_str!("../migrations/0007_highlight_note_and_page_contract.sql"),
    ),
    (
        "0008_queue_and_perf_indexes",
        include_str!("../migrations/0008_queue_and_perf_indexes.sql"),
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
                    || (name == "0005_hidden_books" && has_column(&tx, "books", "hidden_at")?)
                    || (name == "0007_highlight_note_and_page_contract"
                        && has_column(&tx, "highlights", "note")?) =>
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

    ensure_queue_schema(&connection)?;
    recover_expired_job_leases(&connection)?;

    Ok(connection)
}

fn ensure_queue_schema(connection: &Connection) -> AppResult<()> {
    let jobs_table_exists: Option<i32> = connection
        .query_row(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='jobs' LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()?;

    if jobs_table_exists.is_none() {
        return Err(AppError::InvalidInput(
            "Queue schema missing after migration: jobs table not found".to_string(),
        ));
    }

    let due_index_exists: Option<i32> = connection
        .query_row(
            "SELECT 1 FROM sqlite_master WHERE type='index' AND name='idx_jobs_state_next_run_at' LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()?;

    if due_index_exists.is_none() {
        return Err(AppError::InvalidInput(
            "Queue schema missing after migration: idx_jobs_state_next_run_at not found"
                .to_string(),
        ));
    }

    Ok(())
}

fn recover_expired_job_leases(connection: &Connection) -> AppResult<()> {
    let now = Utc::now().to_rfc3339();
    connection.execute(
        "UPDATE jobs
         SET state = 'queued',
             lease_expires_at = NULL,
             next_run_at = ?1,
             updated_at = ?1,
             last_error = COALESCE(last_error, 'lease expired before startup recovery')
         WHERE state = 'running'
           AND lease_expires_at IS NOT NULL
           AND lease_expires_at <= ?1",
        params![now],
    )?;

    Ok(())
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

pub fn verify_queue_health(connection: &Connection) -> AppResult<QueueHealth> {
    let jobs_table_exists: Option<i32> = connection
        .query_row(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='jobs' LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()?;

    if jobs_table_exists.is_none() {
        return Ok(QueueHealth {
            status: HealthStatus::Missing,
            warnings: vec!["jobs table does not exist".to_string()],
        });
    }

    let due_index_exists: Option<i32> = connection
        .query_row(
            "SELECT 1 FROM sqlite_master WHERE type='index' AND name='idx_jobs_state_next_run_at' LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()?;

    let dedupe_index_exists: Option<i32> = connection
        .query_row(
            "SELECT 1 FROM sqlite_master WHERE type='index' AND name='idx_jobs_dedupe_key' LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()?;

    let mut warnings = Vec::new();
    if due_index_exists.is_none() {
        warnings.push("idx_jobs_state_next_run_at index missing".to_string());
    }
    if dedupe_index_exists.is_none() {
        warnings.push("idx_jobs_dedupe_key index missing".to_string());
    }

    let now = Utc::now().to_rfc3339();
    let orphaned: i32 = connection.query_row(
        "SELECT COUNT(*) FROM jobs
         WHERE state = 'running'
           AND lease_expires_at IS NOT NULL
           AND lease_expires_at <= ?1",
        [&now],
        |row| row.get(0),
    )?;

    if orphaned > 0 {
        warnings.push(format!("{} orphaned running jobs found", orphaned));
    }

    let status = if warnings.is_empty() {
        HealthStatus::Healthy
    } else {
        HealthStatus::Degraded
    };

    Ok(QueueHealth { status, warnings })
}

#[derive(Debug, Clone)]
pub struct QueueHealth {
    pub status: HealthStatus,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HealthStatus {
    Healthy,
    Degraded,
    Missing,
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
