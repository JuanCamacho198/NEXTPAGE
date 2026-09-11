use super::LibraryRepository;
use crate::error::AppResult;
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use serde_json::Value;

pub(super) fn is_feature_enabled(repo: &LibraryRepository, feature_name: &str) -> AppResult<bool> {
    let mut statement =
        repo.connection.prepare("SELECT value_json FROM app_settings WHERE key = ?1")?;

    let result: Option<String> =
        statement.query_row(params![feature_name], |row| row.get(0)).optional()?;

    match result {
        Some(value) => {
            let v: Value = serde_json::from_str(&value).unwrap_or(Value::Bool(true));
            Ok(v.as_bool().unwrap_or(true))
        }
        None => Ok(false),
    }
}

pub(super) fn has_metrics_table(repo: &LibraryRepository) -> AppResult<bool> {
    let mut statement = repo
        .connection
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='metrics'")?;

    let exists: Option<String> = statement.query_row([], |row| row.get(0)).optional()?;

    Ok(exists.is_some())
}

pub(super) fn ensure_metrics_table(repo: &LibraryRepository) -> AppResult<()> {
    repo.connection.execute(
        "CREATE TABLE IF NOT EXISTS metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            value REAL NOT NULL,
            tags TEXT,
            timestamp TEXT NOT NULL
        )",
        [],
    )?;

    repo.connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_metrics_name_timestamp ON metrics(name, timestamp)",
        [],
    )?;

    Ok(())
}

pub(super) fn record_metric(
    repo: &LibraryRepository,
    name: &str,
    value: f64,
    tags: Option<&str>,
) -> AppResult<()> {
    if !has_metrics_table(repo)? {
        ensure_metrics_table(repo)?;
    }

    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "INSERT INTO metrics (name, value, tags, timestamp) VALUES (?1, ?2, ?3, ?4)",
        params![name, value, tags, now],
    )?;

    Ok(())
}

pub(super) fn get_metrics(
    repo: &LibraryRepository,
    name: &str,
    days: i32,
) -> AppResult<Vec<(String, f64, String)>> {
    if !has_metrics_table(repo)? {
        return Ok(Vec::new());
    }

    let since = Utc::now() - chrono::Duration::days(days as i64);
    let since_str = since.to_rfc3339();

    let mut statement = repo.connection.prepare(
        "SELECT timestamp, value, tags FROM metrics WHERE name = ?1 AND timestamp >= ?2 ORDER BY timestamp DESC LIMIT 1000",
    )?;

    let rows = statement.query_map(params![name, since_str], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, f64>(1)?,
            row.get::<_, Option<String>>(2)?.unwrap_or_default(),
        ))
    })?;

    let metrics = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(metrics)
}

pub(super) fn get_metrics_summary(
    repo: &LibraryRepository,
    name: &str,
    days: i32,
) -> AppResult<(i64, f64, f64, f64)> {
    if !has_metrics_table(repo)? {
        return Ok((0, 0.0, 0.0, 0.0));
    }

    let since = Utc::now() - chrono::Duration::days(days as i64);
    let since_str = since.to_rfc3339();

    let mut statement = repo.connection.prepare(
        "SELECT COUNT(*), COALESCE(SUM(value), 0), COALESCE(MIN(value), 0), COALESCE(MAX(value), 0)
         FROM metrics WHERE name = ?1 AND timestamp >= ?2",
    )?;

    let result = statement.query_row(params![name, since_str], |row| {
        Ok((
            row.get::<_, i64>(0)?,
            row.get::<_, f64>(1)?,
            row.get::<_, f64>(2)?,
            row.get::<_, f64>(3)?,
        ))
    })?;

    Ok(result)
}
