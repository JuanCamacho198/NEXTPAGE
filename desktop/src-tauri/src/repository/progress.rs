use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    ActivityPoint, ReadingProgressDto, ReadingSessionInput, ReadingStatsSummaryDto,
    SaveProgressInput,
};
use chrono::{DateTime, Datelike, Duration, NaiveDate, Utc};
use rusqlite::{params, OptionalExtension};
use uuid::Uuid;

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
    repo.connection.execute(
        "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, 1)
             ON CONFLICT(id) DO UPDATE SET
               book_id = excluded.book_id,
               cfi_location = excluded.cfi_location,
               percentage = excluded.percentage,
               updated_at = excluded.updated_at,
               version = version + 1",
        params![
            progress.id,
            &progress.book_id,
            &progress.cfi_location,
            progress.percentage,
            &progress.updated_at
        ],
    )?;

    Ok(())
}

pub fn save_reading_session(
    repo: &LibraryRepository,
    session: ReadingSessionInput,
) -> AppResult<()> {
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

    repo.connection.execute(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_seconds, start_percentage, end_percentage, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                Uuid::new_v4().to_string(),
                book_id,
                session.started_at,
                session.ended_at,
                session.duration_seconds,
                session.start_percentage,
                session.end_percentage,
                Utc::now().to_rfc3339(),
            ],
        )?;

    Ok(())
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

pub fn get_reading_streak(repo: &LibraryRepository, book_id: Option<&str>) -> AppResult<i64> {
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
         ORDER BY day DESC",
    )?;

    let days: Vec<NaiveDate> = statement
        .query_map(params![since_str, book_id], |row| {
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
