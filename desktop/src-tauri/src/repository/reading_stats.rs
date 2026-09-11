use rusqlite::{params, OptionalExtension};

use crate::error::AppResult;
use crate::models::ReadingStatsSummaryDto;

use super::LibraryRepository;

pub(super) fn recompute_reading_stats_from_sessions(
    repo: &LibraryRepository,
    book_id: Option<&str>,
) -> AppResult<ReadingStatsSummaryDto> {
    if let Some(id) = book_id {
        let total_seconds: i64 = repo.connection.query_row(
            "SELECT COALESCE(SUM(duration_seconds), 0)
             FROM reading_sessions
             WHERE book_id = ?1",
            params![id],
            |row| row.get(0),
        )?;

        let total_sessions: i64 = repo.connection.query_row(
            "SELECT COUNT(*) FROM reading_sessions WHERE book_id = ?1",
            params![id],
            |row| row.get(0),
        )?;

        let max_progress: Option<f64> = repo.connection.query_row(
            "SELECT MAX(COALESCE(end_percentage, start_percentage))
             FROM reading_sessions
             WHERE book_id = ?1",
            params![id],
            |row| row.get(0),
        )?;

        let avg_progress = max_progress.unwrap_or(0.0);
        return Ok(ReadingStatsSummaryDto {
            total_minutes_read: ((total_seconds as f64) / 60.0).round() as i64,
            total_sessions,
            books_started: if total_sessions > 0 { 1 } else { 0 },
            books_completed: if avg_progress >= 100.0 { 1 } else { 0 },
            avg_progress_percentage: avg_progress,
        });
    }

    let total_seconds: i64 = repo.connection.query_row(
        "SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions",
        [],
        |row| row.get(0),
    )?;
    let total_sessions: i64 =
        repo.connection.query_row("SELECT COUNT(*) FROM reading_sessions", [], |row| row.get(0))?;
    let books_started: i64 = repo.connection.query_row(
        "SELECT COUNT(DISTINCT book_id) FROM reading_sessions",
        [],
        |row| row.get(0),
    )?;
    let books_completed: i64 = repo.connection.query_row(
        "SELECT COUNT(*)
         FROM (
            SELECT book_id, MAX(COALESCE(end_percentage, start_percentage, 0)) AS max_progress
            FROM reading_sessions
            GROUP BY book_id
         ) x
         WHERE x.max_progress >= 100.0",
        [],
        |row| row.get(0),
    )?;
    let avg_progress_percentage: f64 = repo.connection.query_row(
        "SELECT COALESCE(AVG(max_progress), 0.0)
         FROM (
            SELECT MAX(COALESCE(end_percentage, start_percentage, 0.0)) AS max_progress
            FROM reading_sessions
            GROUP BY book_id
         )",
        [],
        |row| row.get(0),
    )?;

    Ok(ReadingStatsSummaryDto {
        total_minutes_read: ((total_seconds as f64) / 60.0).round() as i64,
        total_sessions,
        books_started,
        books_completed,
        avg_progress_percentage,
    })
}
pub(super) fn reading_stats_drift_over_threshold(
    repo: &LibraryRepository,
    book_id: Option<&str>,
    event_avg_progress: f64,
) -> AppResult<bool> {
    let baseline_progress: f64 = if let Some(id) = book_id {
        repo.connection
            .query_row(
                "SELECT COALESCE(percentage, 0.0)
             FROM reading_progress
             WHERE book_id = ?1 AND deleted_at IS NULL
             ORDER BY updated_at DESC
             LIMIT 1",
                params![id],
                |row| row.get(0),
            )
            .optional()?
            .unwrap_or(0.0)
    } else {
        repo.connection.query_row(
            "SELECT COALESCE(AVG(percentage), 0.0)
             FROM reading_progress
             WHERE deleted_at IS NULL",
            [],
            |row| row.get(0),
        )?
    };

    Ok((event_avg_progress - baseline_progress).abs() > 1.0)
}
#[cfg(test)]
mod tests {
    use crate::repository::tests::{insert_book, new_repository};
    use chrono::Utc;
    use rusqlite::params;
    use uuid::Uuid;

    #[test]
    fn stats_drift_threshold_respects_one_percent_tolerance() {
        let repository = new_repository();
        insert_book(&repository, "book-stats", "C:/library/book-stats.epub");

        repository
            .connection
            .execute(
                "INSERT INTO reading_progress (id, book_id, cfi_location, percentage, updated_at, deleted_at, version)
                 VALUES (?1, ?2, 'loc', ?3, ?4, NULL, 1)",
                params![Uuid::new_v4().to_string(), "book-stats", 10.0_f64, Utc::now().to_rfc3339()],
            )
            .unwrap();

        assert!(!repository.reading_stats_drift_over_threshold(Some("book-stats"), 10.5).unwrap());
        assert!(repository.reading_stats_drift_over_threshold(Some("book-stats"), 12.5).unwrap());
    }
}
