use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    ActivityPoint, ReadingProgressDto, ReadingSessionInput, ReadingSessionSavedDto,
    ReadingStatsSummaryDto, RemoteReadingSessionRow, SaveProgressInput,
};
use chrono::{DateTime, Datelike, Duration, NaiveDate, Utc};
use rusqlite::{params, OptionalExtension};
use sha2::{Digest, Sha256};
use uuid::Uuid;

/// Deterministic session id shared with Android:
/// `"sess_" + sha256("$userId|$bookId|$startTimeEpochMillis").hex.take(32)`.
/// The hash input is exactly `{userId}|{bookId}|{epochMillis}` — pipe separators,
/// no quotes, epoch millis as decimal digits. Byte-parity with the Android
/// implementation is the convergence contract (any drift duplicates minutes).
fn reading_session_id(user_id: &str, book_id: &str, started_epoch_millis: i64) -> String {
    let hash_input = format!("{}|{}|{}", user_id, book_id, started_epoch_millis);
    let digest = Sha256::digest(hash_input.as_bytes());
    let hex = format!("{:x}", digest);
    format!("sess_{}", hex.chars().take(32).collect::<String>())
}

pub fn get_progress(
    repo: &LibraryRepository,
    book_id: &str,
) -> AppResult<Option<ReadingProgressDto>> {
    if book_id.trim().is_empty() {
        return Err(AppError::MissingBookId);
    }

    let mut statement = repo.connection.prepare(
        "SELECT id, book_id, cfi_location, percentage, updated_at
             FROM reading_progress
             WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY updated_at DESC
             LIMIT 1",
    )?;

    let progress = statement
        .query_row(params![book_id], |row| {
            Ok(ReadingProgressDto {
                id: row.get(0)?,
                book_id: row.get(1)?,
                cfi_location: row.get(2)?,
                percentage: row.get(3)?,
                updated_at: row.get(4)?,
            })
        })
        .optional()?;

    Ok(progress)
}

pub fn save_progress(repo: &LibraryRepository, payload: SaveProgressInput) -> AppResult<()> {
    if payload.book_id.trim().is_empty() {
        return Err(AppError::MissingBookId);
    }

    let now = Utc::now().to_rfc3339();

    let existing_id: Option<String> = repo
        .connection
        .query_row(
            "SELECT id FROM reading_progress WHERE book_id = ?1 AND deleted_at IS NULL LIMIT 1",
            params![&payload.book_id],
            |row| row.get(0),
        )
        .optional()?;

    match existing_id {
        Some(id) => {
            repo.connection.execute(
                "UPDATE reading_progress
                     SET cfi_location = ?1, percentage = ?2, updated_at = ?3, version = version + 1
                     WHERE id = ?4",
                params![payload.cfi_location, payload.percentage, now, id],
            )?;
        }
        None => {
            repo.connection.execute(
                    "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, version)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                    params![
                        Uuid::new_v4().to_string(),
                        &payload.book_id,
                        &payload.cfi_location,
                        payload.percentage,
                        &now,
                        1
                    ],
                )?;
        }
    }

    Ok(())
}

pub fn upsert_progress(repo: &LibraryRepository, progress: ReadingProgressDto) -> AppResult<()> {
    if progress.book_id.trim().is_empty() {
        return Err(AppError::MissingBookId);
    }

    // Consolidate by ACTIVE book row, never by id: the local id (from
    // save_progress) and the remote id (from a Supabase pull) differ, so an
    // ON CONFLICT(id) upsert would insert a second row for the same book and
    // the shelf JOIN would then render the book twice. Update the existing
    // active row in place; only insert when none exists.
    let existing_id: Option<String> = repo
        .connection
        .query_row(
            "SELECT id FROM reading_progress WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY updated_at DESC LIMIT 1",
            params![&progress.book_id],
            |row| row.get(0),
        )
        .optional()?;

    match existing_id {
        Some(id) => {
            repo.connection.execute(
                "UPDATE reading_progress
                 SET cfi_location = ?1, percentage = ?2, updated_at = ?3, version = version + 1
                 WHERE id = ?4",
                params![&progress.cfi_location, progress.percentage, &progress.updated_at, id],
            )?;
        }
        None => {
            // No active row yet: honor the caller-supplied id when present
            // (remote pull), else mint a fresh UUID.
            let id = if progress.id.trim().is_empty() {
                Uuid::new_v4().to_string()
            } else {
                progress.id.clone()
            };
            repo.connection.execute(
                "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, version)
                 VALUES (?1, ?2, ?3, ?4, ?5, 1)",
                params![
                    id,
                    &progress.book_id,
                    &progress.cfi_location,
                    progress.percentage,
                    &progress.updated_at
                ],
            )?;
        }
    }

    Ok(())
}

pub fn save_reading_session(
    repo: &LibraryRepository,
    session: ReadingSessionInput,
) -> AppResult<ReadingSessionSavedDto> {
    let book_id = session.book_id.trim();
    if book_id.is_empty() {
        return Err(AppError::MissingBookId);
    }
    if session.started_at.trim().is_empty() {
        return Err(AppError::InvalidInput("Reading session startedAt is required".to_string()));
    }
    if session.duration_seconds < 0 {
        return Err(AppError::InvalidInput(
            "Reading session durationSeconds cannot be negative".to_string(),
        ));
    }
    if session.duration_seconds == 0 {
        return Err(AppError::InvalidInput(
            "Reading session durationSeconds must be greater than zero".to_string(),
        ));
    }
    if session.ended_at.is_none() {
        return Err(AppError::InvalidInput("Reading session endedAt is required".to_string()));
    }

    let started_at =
        chrono::DateTime::parse_from_rfc3339(session.started_at.trim()).map_err(|_| {
            AppError::InvalidInput("Reading session startedAt must be RFC3339".to_string())
        })?;
    let ended_at_raw = session.ended_at.as_deref().unwrap_or_default();
    let ended_at = chrono::DateTime::parse_from_rfc3339(ended_at_raw.trim()).map_err(|_| {
        AppError::InvalidInput("Reading session endedAt must be RFC3339".to_string())
    })?;
    if ended_at <= started_at {
        return Err(AppError::InvalidInput(
            "Reading session endedAt must be after startedAt".to_string(),
        ));
    }

    LibraryRepository::validate_percentage("startPercentage", session.start_percentage)?;
    LibraryRepository::validate_percentage("endPercentage", session.end_percentage)?;

    if let (Some(start), Some(end)) = (session.start_percentage, session.end_percentage) {
        if (start - end).abs() < f64::EPSILON {
            return Err(AppError::InvalidInput(
                "Reading session startPercentage and endPercentage cannot be equal".to_string(),
            ));
        }
    }

    let started_utc = started_at.with_timezone(&Utc);
    let started_epoch_millis = started_utc.timestamp_millis();
    let id = reading_session_id(&session.user_id, book_id, started_epoch_millis);
    let duration_minutes = session.duration_seconds / 60;
    let date = started_utc.date_naive().and_hms_opt(0, 0, 0).unwrap().and_utc().to_rfc3339();
    let updated_at_epoch_millis = Utc::now().timestamp_millis();

    repo.connection.execute(
            "INSERT OR REPLACE INTO reading_sessions (id, book_id, started_at, ended_at, duration_seconds, start_percentage, end_percentage, created_at, user_id, date, updated_at_epoch_millis)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![
                &id,
                book_id,
                session.started_at,
                session.ended_at,
                session.duration_seconds,
                session.start_percentage,
                session.end_percentage,
                Utc::now().to_rfc3339(),
                session.user_id,
                &date,
                updated_at_epoch_millis,
            ],
        )?;

    Ok(ReadingSessionSavedDto { id, duration_minutes, date, updated_at_epoch_millis })
}

pub fn get_reading_stats(
    repo: &LibraryRepository,
    book_id: Option<&str>,
) -> AppResult<ReadingStatsSummaryDto> {
    if let Some(id) = book_id {
        if id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
    }

    let mut stats = repo.recompute_reading_stats_from_sessions(book_id)?;

    if repo.reading_stats_drift_over_threshold(book_id, stats.avg_progress_percentage)? {
        stats = repo.recompute_reading_stats_from_sessions(book_id)?;
    }

    Ok(stats)
}

fn validate_period(period: &str) -> AppResult<&'static str> {
    match period {
        "week" => Ok("week"),
        "month" => Ok("month"),
        "year" => Ok("year"),
        "all" => Ok("all"),
        other => Err(AppError::InvalidInput(format!(
            "Unknown reading activity period: {} (expected: week | month | year | all)",
            other
        ))),
    }
}

fn validate_granularity(granularity: &str) -> AppResult<&'static str> {
    match granularity {
        "day" => Ok("day"),
        "week" => Ok("week"),
        "month" => Ok("month"),
        other => Err(AppError::InvalidInput(format!(
            "Unknown reading activity granularity: {} (expected: day | week | month)",
            other
        ))),
    }
}

fn since_for_period(period: &str) -> AppResult<Option<DateTime<Utc>>> {
    match period {
        "week" => Ok(Some(Utc::now() - Duration::days(7))),
        "month" => Ok(Some(Utc::now() - Duration::days(30))),
        "year" => Ok(Some(Utc::now() - Duration::days(365))),
        "all" => Ok(None),
        _ => Err(AppError::InvalidInput(format!("Unknown reading activity period: {}", period))),
    }
}

fn bucket_expr(granularity: &str) -> &'static str {
    match granularity {
        "day" => "strftime('%Y-%m-%d', started_at)",
        "week" => "strftime('%Y-%W', started_at)",
        "month" => "strftime('%Y-%m', started_at)",
        _ => "strftime('%Y-%m-%d', started_at)",
    }
}

fn day_bucket(date: NaiveDate) -> String {
    date.format("%Y-%m-%d").to_string()
}

fn week_bucket(date: NaiveDate) -> String {
    let iso = date.iso_week();
    format!("{:04}-{:02}", iso.year(), iso.week())
}

fn month_bucket(date: NaiveDate) -> String {
    date.format("%Y-%m").to_string()
}

fn emit_bucket(date: NaiveDate, granularity: &str) -> String {
    match granularity {
        "day" => day_bucket(date),
        "week" => week_bucket(date),
        "month" => month_bucket(date),
        _ => day_bucket(date),
    }
}

fn next_bucket_date(date: NaiveDate, granularity: &str) -> NaiveDate {
    match granularity {
        "day" => date + Duration::days(1),
        "week" => date + Duration::weeks(1),
        "month" => {
            let (mut year, mut month) = (date.year(), date.month());
            month += 1;
            if month > 12 {
                month = 1;
                year += 1;
            }
            NaiveDate::from_ymd_opt(year, month, 1).unwrap_or(date)
        }
        _ => date + Duration::days(1),
    }
}

pub fn get_reading_activity(
    repo: &LibraryRepository,
    period: &str,
    granularity: &str,
    book_id: Option<&str>,
) -> AppResult<Vec<ActivityPoint>> {
    if let Some(id) = book_id {
        if id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
    }
    let period = validate_period(period)?;
    let granularity = validate_granularity(granularity)?;
    let since = since_for_period(period)?;

    let bucket_sql = bucket_expr(granularity);

    let sql = format!(
        "SELECT {} AS bucket,
                CAST(SUM(duration_seconds) AS INTEGER) / 60 AS minutes
         FROM reading_sessions
         WHERE (?1 IS NULL OR started_at >= ?1)
           AND (?2 IS NULL OR book_id = ?2)
         GROUP BY bucket
         ORDER BY bucket ASC",
        bucket_sql
    );

    let bucket_index: Vec<(String, i64)> = if let Some(since_dt) = since {
        let since_str = since_dt.to_rfc3339();
        let mut statement = repo.connection.prepare(&sql)?;
        let rows = statement.query_map(params![since_str, book_id], |row| {
            Ok((row.get::<_, String>(0)?, row.get::<_, i64>(1)?))
        })?;
        rows.collect::<Result<Vec<_>, _>>()?
    } else {
        let mut statement = repo.connection.prepare(&sql)?;
        let rows = statement.query_map(params![Option::<String>::None, book_id], |row| {
            Ok((row.get::<_, String>(0)?, row.get::<_, i64>(1)?))
        })?;
        rows.collect::<Result<Vec<_>, _>>()?
    };

    let now = Utc::now().date_naive();
    let start_date = match (since, period) {
        (Some(dt), _) => dt.date_naive() + Duration::days(1),
        (None, _) => {
            let mut statement = repo.connection.prepare(
                "SELECT MIN(started_at) FROM reading_sessions WHERE (?1 IS NULL OR book_id = ?1)",
            )?;
            let min_row: Option<String> =
                statement.query_row(params![book_id], |row| row.get(0)).optional()?.flatten();
            if let Some(min_str) = min_row {
                if let Ok(parsed) = DateTime::parse_from_rfc3339(&min_str) {
                    return build_dense_series(
                        parsed.with_timezone(&Utc).date_naive(),
                        now,
                        granularity,
                        bucket_index,
                    );
                }
            }
            now
        }
    };

    build_dense_series(start_date, now, granularity, bucket_index)
}

fn build_dense_series(
    start_date: NaiveDate,
    now: NaiveDate,
    granularity: &str,
    bucket_index: Vec<(String, i64)>,
) -> AppResult<Vec<ActivityPoint>> {
    let mut by_bucket: std::collections::HashMap<String, i64> = std::collections::HashMap::new();
    for (key, minutes) in bucket_index {
        by_bucket.insert(key, minutes);
    }

    let mut out: Vec<ActivityPoint> = Vec::new();
    let mut cursor = start_date;
    let mut guard = 0_i64;
    let max_iterations: i64 = 400;
    while guard < max_iterations {
        let key = emit_bucket(cursor, granularity);
        let minutes = by_bucket.get(&key).copied().unwrap_or(0);
        out.push(ActivityPoint { bucket: key, minutes });
        if granularity == "day" && cursor >= now {
            break;
        }
        if granularity == "week" && cursor >= now {
            break;
        }
        if granularity == "month" && cursor.year() == now.year() && cursor.month() >= now.month() {
            break;
        }
        cursor = next_bucket_date(cursor, granularity);
        if granularity == "all" && cursor > now {
            break;
        }
        guard += 1;
    }
    if granularity == "all" {
        let final_key = emit_bucket(now, granularity);
        if out.last().map(|p| p.bucket != final_key).unwrap_or(true) {
            let minutes = by_bucket.get(&final_key).copied().unwrap_or(0);
            out.push(ActivityPoint { bucket: final_key, minutes });
        }
    }

    Ok(out)
}

pub fn get_reading_stats_for_range(
    repo: &LibraryRepository,
    from_rfc3339: &str,
    to_rfc3339: &str,
    book_id: Option<&str>,
) -> AppResult<ReadingStatsSummaryDto> {
    if let Some(id) = book_id {
        if id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
    }

    let from = DateTime::parse_from_rfc3339(from_rfc3339)
        .map_err(|_| AppError::InvalidInput(format!("from is not RFC3339: {}", from_rfc3339)))?
        .with_timezone(&Utc);
    let to = DateTime::parse_from_rfc3339(to_rfc3339)
        .map_err(|_| AppError::InvalidInput(format!("to is not RFC3339: {}", to_rfc3339)))?
        .with_timezone(&Utc);

    let from_str = from.to_rfc3339();
    let to_str = to.to_rfc3339();

    let total_seconds: i64 = if book_id.is_some() {
        repo.connection.query_row(
            "SELECT COALESCE(SUM(duration_seconds), 0)
             FROM reading_sessions
             WHERE book_id = ?1 AND started_at BETWEEN ?2 AND ?3",
            params![book_id, from_str, to_str],
            |row| row.get(0),
        )?
    } else {
        repo.connection.query_row(
            "SELECT COALESCE(SUM(duration_seconds), 0)
             FROM reading_sessions
             WHERE started_at BETWEEN ?1 AND ?2",
            params![from_str, to_str],
            |row| row.get(0),
        )?
    };

    let total_sessions: i64 = if book_id.is_some() {
        repo.connection.query_row(
            "SELECT COUNT(*) FROM reading_sessions
             WHERE book_id = ?1 AND started_at BETWEEN ?2 AND ?3",
            params![book_id, from_str, to_str],
            |row| row.get(0),
        )?
    } else {
        repo.connection.query_row(
            "SELECT COUNT(*) FROM reading_sessions WHERE started_at BETWEEN ?1 AND ?2",
            params![from_str, to_str],
            |row| row.get(0),
        )?
    };

    let max_progress: Option<f64> = if book_id.is_some() {
        repo.connection
            .query_row(
                "SELECT MAX(COALESCE(end_percentage, start_percentage))
                 FROM reading_sessions
                 WHERE book_id = ?1 AND started_at BETWEEN ?2 AND ?3",
                params![book_id, from_str, to_str],
                |row| row.get(0),
            )
            .optional()?
            .flatten()
    } else {
        None
    };

    let books_started: i64 = if book_id.is_some() {
        if total_sessions > 0 {
            1
        } else {
            0
        }
    } else {
        repo.connection.query_row(
            "SELECT COUNT(DISTINCT book_id) FROM reading_sessions
             WHERE started_at BETWEEN ?1 AND ?2",
            params![from_str, to_str],
            |row| row.get(0),
        )?
    };

    let books_completed: i64 = if book_id.is_some() {
        if max_progress.unwrap_or(0.0) >= 100.0 {
            1
        } else {
            0
        }
    } else {
        repo.connection.query_row(
            "SELECT COUNT(*)
             FROM (
                SELECT book_id, MAX(COALESCE(end_percentage, start_percentage, 0)) AS max_progress
                FROM reading_sessions
                WHERE started_at BETWEEN ?1 AND ?2
                GROUP BY book_id
             ) x
             WHERE x.max_progress >= 100.0",
            params![from_str, to_str],
            |row| row.get(0),
        )?
    };

    let avg_progress_percentage: f64 = if book_id.is_some() {
        max_progress.unwrap_or(0.0)
    } else {
        repo.connection.query_row(
            "SELECT COALESCE(AVG(max_progress), 0.0)
             FROM (
                SELECT MAX(COALESCE(end_percentage, start_percentage, 0.0)) AS max_progress
                FROM reading_sessions
                WHERE started_at BETWEEN ?1 AND ?2
                GROUP BY book_id
             )",
            params![from_str, to_str],
            |row| row.get(0),
        )?
    };

    Ok(ReadingStatsSummaryDto {
        total_minutes_read: ((total_seconds as f64) / 60.0).round() as i64,
        total_sessions,
        books_started,
        books_completed,
        avg_progress_percentage,
    })
}

pub fn get_reading_streak(
    repo: &LibraryRepository,
    book_id: Option<&str>,
    user_id: &str,
) -> AppResult<i64> {
    if let Some(id) = book_id {
        if id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }
    }

    // TODO(read-stats): localize streak days to user's local timezone; consider removing the 45-day cap once we can index on DATE(started_at, 'localtime').
    const CAP: i64 = 45;
    let since_dt = Utc::now() - Duration::days(CAP);
    let since_str = since_dt.to_rfc3339();

    let mut statement = repo.connection.prepare(
        "SELECT DISTINCT strftime('%Y-%m-%d', started_at) AS day
         FROM reading_sessions
         WHERE started_at >= ?1
           AND (?2 IS NULL OR book_id = ?2)
           AND (user_id = ?3 OR user_id = '')
         ORDER BY day DESC",
    )?;

    let days: Vec<NaiveDate> = statement
        .query_map(params![since_str, book_id, user_id], |row| {
            let raw: String = row.get(0)?;
            Ok(raw)
        })?
        .filter_map(|row| row.ok())
        .filter_map(|raw| NaiveDate::parse_from_str(&raw, "%Y-%m-%d").ok())
        .collect();

    let set: std::collections::HashSet<NaiveDate> = days.into_iter().collect();
    let today = Utc::now().date_naive();

    if set.is_empty() {
        return Ok(0);
    }

    let mut count: i64;
    let mut cursor: NaiveDate;

    if set.contains(&today) {
        count = 1;
        cursor = today - Duration::days(1);
    } else {
        let yesterday = today - Duration::days(1);
        if set.contains(&yesterday) {
            count = 0;
            cursor = yesterday;
        } else {
            return Ok(0);
        }
    }

    while set.contains(&cursor) && count < CAP {
        count += 1;
        cursor -= Duration::days(1);
    }

    Ok(count)
}

/// Merge remote (Supabase) reading sessions into the local table (D11).
/// Per row: FK guard (book absent locally -> skip, documented) -> LWW
/// (local `updated_at_epoch_millis >= remote` -> skip; tie = no-op so pull-back
/// of rows this device just pushed never double-counts) -> INSERT OR REPLACE by
/// deterministic id. Remote rows have no ended_at (stored NULL) and carry
/// `duration_minutes` (mapped back to seconds via minutes * 60, documented
/// precision loss). Returns the number of rows applied.
pub fn upsert_remote_reading_sessions(
    repo: &LibraryRepository,
    rows: &[RemoteReadingSessionRow],
) -> AppResult<i64> {
    let mut applied = 0_i64;

    for row in rows {
        let book_exists: bool = repo.connection.query_row(
            "SELECT EXISTS(SELECT 1 FROM books WHERE id = ?1)",
            params![&row.book_id],
            |r| r.get(0),
        )?;
        if !book_exists {
            continue;
        }

        let local_clock: Option<i64> = repo
            .connection
            .query_row(
                "SELECT updated_at_epoch_millis FROM reading_sessions WHERE id = ?1",
                params![&row.id],
                |r| r.get(0),
            )
            .optional()?;
        if let Some(local) = local_clock {
            if local >= row.updated_at_epoch_millis {
                continue;
            }
        }

        repo.connection.execute(
            "INSERT OR REPLACE INTO reading_sessions (id, book_id, started_at, ended_at, duration_seconds, start_percentage, end_percentage, created_at, user_id, date, updated_at_epoch_millis)
             VALUES (?1, ?2, ?3, NULL, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                &row.id,
                &row.book_id,
                &row.started_at,
                row.duration_minutes * 60,
                row.start_percentage,
                row.end_percentage,
                Utc::now().to_rfc3339(),
                &row.user_id,
                &row.date,
                row.updated_at_epoch_millis,
            ],
        )?;
        applied += 1;
    }

    Ok(applied)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::{insert_book, new_repository};
    use chrono::Duration as ChronoDuration;
    use rusqlite::params;

    fn session_input(
        user_id: &str,
        book_id: &str,
        started_at: &str,
        duration_seconds: i64,
    ) -> ReadingSessionInput {
        let started = DateTime::parse_from_rfc3339(started_at).unwrap();
        let ended = started + ChronoDuration::seconds(duration_seconds);
        ReadingSessionInput {
            user_id: user_id.to_string(),
            book_id: book_id.to_string(),
            started_at: started_at.to_string(),
            ended_at: Some(ended.to_rfc3339()),
            duration_seconds,
            start_percentage: Some(10.0),
            end_percentage: Some(20.0),
        }
    }

    fn remote_row(id: &str, book_id: &str, minutes: i64, clock: i64) -> RemoteReadingSessionRow {
        RemoteReadingSessionRow {
            id: id.to_string(),
            user_id: "u-remote".to_string(),
            book_id: book_id.to_string(),
            started_at: "2026-08-13T10:00:00Z".to_string(),
            duration_minutes: minutes,
            date: "2026-08-13T00:00:00+00:00".to_string(),
            updated_at_epoch_millis: clock,
            start_percentage: Some(0.0),
            end_percentage: Some(50.0),
        }
    }

    #[test]
    fn reading_session_id_is_deterministic_and_matches_android_vector() {
        let id1 = reading_session_id("u1", "b1", 1786615200000);
        let id2 = reading_session_id("u1", "b1", 1786615200000);
        assert_eq!(id1, id2);
        // Known-good vector computed independently (sha256("u1|b1|1786615200000") hex[..32]).
        assert_eq!(id1, "sess_953ac281a5845cd84a6522795bc747ff");
        assert!(id1.starts_with("sess_"));
        assert_eq!(id1.len(), 5 + 32);
        assert!(id1[5..].chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn upsert_progress_consolidates_by_active_book_row() {
        let repo = new_repository();
        insert_book(&repo, "book-1", "C:/book-1.epub");

        // Simulate a local save that mints its own id...
        repo.save_progress(SaveProgressInput {
            book_id: "book-1".to_string(),
            cfi_location: "cfi-local".to_string(),
            percentage: 10.0,
        })
        .unwrap();

        // ...then a remote pull with a DIFFERENT id must update the existing
        // active row, not insert a second one (which would duplicate the book
        // in the shelf via the list_library_books JOIN). The remote id is NOT
        // adopted — the local id stays as the single local row's key.
        repo.upsert_progress(ReadingProgressDto {
            id: "remote-id".to_string(),
            book_id: "book-1".to_string(),
            cfi_location: "cfi-remote".to_string(),
            percentage: 20.0,
            updated_at: "2026-08-18T17:00:00+00:00".to_string(),
        })
        .unwrap();

        let rows: Vec<(String, String, f64)> = repo
            .connection
            .prepare(
                "SELECT id, cfi_location, percentage FROM reading_progress
                 WHERE book_id = 'book-1' AND deleted_at IS NULL",
            )
            .unwrap()
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))
            .unwrap()
            .collect::<Result<Vec<_>, _>>()
            .unwrap();

        assert_eq!(rows.len(), 1, "remote pull with different id must not duplicate");
        assert_eq!(rows[0].1, "cfi-remote");
        assert_eq!(rows[0].2, 20.0);
    }

    #[test]
    fn upsert_progress_inserts_when_no_active_row_exists() {
        let repo = new_repository();
        insert_book(&repo, "book-2", "C:/book-2.epub");

        repo.upsert_progress(ReadingProgressDto {
            id: "remote-id-2".to_string(),
            book_id: "book-2".to_string(),
            cfi_location: "cfi-2".to_string(),
            percentage: 5.0,
            updated_at: "2026-08-18T17:00:00+00:00".to_string(),
        })
        .unwrap();

        let rows: Vec<(String, String, f64)> = repo
            .connection
            .prepare(
                "SELECT id, cfi_location, percentage FROM reading_progress
                 WHERE book_id = 'book-2' AND deleted_at IS NULL",
            )
            .unwrap()
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))
            .unwrap()
            .collect::<Result<Vec<_>, _>>()
            .unwrap();

        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].0, "remote-id-2");
        assert_eq!(rows[0].1, "cfi-2");
        assert_eq!(rows[0].2, 5.0);
    }

    #[test]
    fn reading_session_id_changes_when_start_time_changes() {
        let id1 = reading_session_id("u1", "b1", 1786615200000);
        let id2 = reading_session_id("u1", "b1", 1786615201000);
        assert_ne!(id1, id2);
    }

    #[test]
    fn save_reading_session_populates_user_columns_and_floor_duration() {
        let repo = new_repository();
        insert_book(&repo, "book-floor", "C:/library/book-floor.epub");

        // Distinct start times (same UTC day) so each save is its own row.
        let cases = [
            ("2026-08-13T10:00:00Z", 45_i64, 0_i64),
            ("2026-08-13T11:00:00Z", 119_i64, 1_i64),
            ("2026-08-13T12:00:00Z", 120_i64, 2_i64),
        ];
        for (started_at, seconds, expected_minutes) in cases {
            let saved = save_reading_session(
                &repo,
                session_input("u-floor", "book-floor", started_at, seconds),
            )
            .unwrap();
            assert_eq!(saved.duration_minutes, expected_minutes);
            assert_eq!(saved.date, "2026-08-13T00:00:00+00:00");
            assert!(saved.updated_at_epoch_millis > 0);

            let (user_id, date, clock): (String, Option<String>, i64) = repo
                .connection
                .query_row(
                    "SELECT user_id, date, updated_at_epoch_millis FROM reading_sessions WHERE id = ?1",
                    params![saved.id],
                    |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
                )
                .unwrap();
            assert_eq!(user_id, "u-floor");
            assert_eq!(date.as_deref(), Some("2026-08-13T00:00:00+00:00"));
            assert_eq!(clock, saved.updated_at_epoch_millis);
        }
    }

    #[test]
    fn save_reading_session_same_triple_replaces_no_duplicate() {
        let repo = new_repository();
        insert_book(&repo, "book-replace", "C:/library/book-replace.epub");

        let first = save_reading_session(
            &repo,
            session_input("u-r", "book-replace", "2026-08-13T10:00:00Z", 300),
        )
        .unwrap();
        let second = save_reading_session(
            &repo,
            session_input("u-r", "book-replace", "2026-08-13T10:00:00Z", 600),
        )
        .unwrap();
        assert_eq!(first.id, second.id);

        let count: i64 = repo
            .connection
            .query_row(
                "SELECT COUNT(*) FROM reading_sessions WHERE id = ?1",
                params![first.id],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(count, 1);

        // OR REPLACE keeps the latest payload.
        let duration: i64 = repo
            .connection
            .query_row(
                "SELECT duration_seconds FROM reading_sessions WHERE id = ?1",
                params![first.id],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(duration, 600);
    }

    #[test]
    fn upsert_remote_skips_unknown_book_fk_guard() {
        let repo = new_repository();
        insert_book(&repo, "book-known", "C:/library/book-known.epub");

        let applied = upsert_remote_reading_sessions(
            &repo,
            &[
                remote_row("sess_absent", "book-absent", 10, 1000),
                remote_row("sess_known", "book-known", 10, 1000),
            ],
        )
        .unwrap();
        assert_eq!(applied, 1);

        let count: i64 = repo
            .connection
            .query_row("SELECT COUNT(*) FROM reading_sessions", [], |r| r.get(0))
            .unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn upsert_remote_lww_newer_applies_older_and_tie_skip() {
        let repo = new_repository();
        insert_book(&repo, "book-lww", "C:/library/book-lww.epub");

        // Seed local row at clock 1000.
        let applied =
            upsert_remote_reading_sessions(&repo, &[remote_row("sess_lww", "book-lww", 10, 1000)])
                .unwrap();
        assert_eq!(applied, 1);

        // Newer remote (2000) applies.
        let applied =
            upsert_remote_reading_sessions(&repo, &[remote_row("sess_lww", "book-lww", 20, 2000)])
                .unwrap();
        assert_eq!(applied, 1);
        let seconds: i64 = repo
            .connection
            .query_row(
                "SELECT duration_seconds FROM reading_sessions WHERE id = 'sess_lww'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(seconds, 20 * 60);

        // Older remote (500) skipped.
        let applied =
            upsert_remote_reading_sessions(&repo, &[remote_row("sess_lww", "book-lww", 30, 500)])
                .unwrap();
        assert_eq!(applied, 0);

        // Tie (2000 == local) skipped.
        let applied =
            upsert_remote_reading_sessions(&repo, &[remote_row("sess_lww", "book-lww", 40, 2000)])
                .unwrap();
        assert_eq!(applied, 0);

        let (seconds, ended_at): (i64, Option<String>) = repo
            .connection
            .query_row(
                "SELECT duration_seconds, ended_at FROM reading_sessions WHERE id = 'sess_lww'",
                [],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .unwrap();
        assert_eq!(seconds, 20 * 60);
        assert!(ended_at.is_none());
    }

    #[test]
    fn upsert_remote_duplicate_id_results_in_single_row() {
        let repo = new_repository();
        insert_book(&repo, "book-dup", "C:/library/book-dup.epub");

        let applied = upsert_remote_reading_sessions(
            &repo,
            &[
                remote_row("sess_dup", "book-dup", 5, 100),
                remote_row("sess_dup", "book-dup", 6, 200),
            ],
        )
        .unwrap();
        assert_eq!(applied, 2);

        let count: i64 = repo
            .connection
            .query_row("SELECT COUNT(*) FROM reading_sessions WHERE id = 'sess_dup'", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn get_reading_streak_filters_by_user_and_includes_legacy_rows() {
        use chrono::Duration;

        let repo = new_repository();
        insert_book(&repo, "book-iso", "C:/library/book-iso.epub");

        let today = Utc::now().date_naive().and_hms_opt(10, 0, 0).unwrap().and_utc();
        let yesterday = (today - Duration::days(1)).to_rfc3339();
        let two_days_ago = (today - Duration::days(2)).to_rfc3339();
        let today_rfc = today.to_rfc3339();

        // u1: three consecutive days (today, yesterday, day-before).
        let _ = save_reading_session(&repo, session_input("u1", "book-iso", &two_days_ago, 300))
            .unwrap();
        let _ =
            save_reading_session(&repo, session_input("u1", "book-iso", &yesterday, 300)).unwrap();
        let _ =
            save_reading_session(&repo, session_input("u1", "book-iso", &today_rfc, 300)).unwrap();

        // u2: today only.
        let _ = save_reading_session(
            &repo,
            session_input("u2", "book-iso", &(today + Duration::seconds(1)).to_rfc3339(), 300),
        )
        .unwrap();

        // Legacy (''): yesterday only — visible to every user via OR user_id = ''.
        // Same timestamp as u1's yesterday row but different user -> different id.
        let _ =
            save_reading_session(&repo, session_input("", "book-iso", &yesterday, 300)).unwrap();

        // u1: {D, D-1, D-2} (+ legacy D-1) -> 3.
        assert_eq!(get_reading_streak(&repo, None, "u1").unwrap(), 3);
        // u2: {D} + legacy {D-1} -> 2 (today-alive walk-back).
        assert_eq!(get_reading_streak(&repo, None, "u2").unwrap(), 2);
        // Other user: only legacy {D-1} -> yesterday-alive counts that day -> 1.
        assert_eq!(get_reading_streak(&repo, None, "u-other").unwrap(), 1);
    }
}
