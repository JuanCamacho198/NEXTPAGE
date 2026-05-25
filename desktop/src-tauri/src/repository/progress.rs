use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    ReadingProgressDto, ReadingSessionInput, ReadingStatsSummaryDto, SaveProgressInput,
};
use chrono::Utc;
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
