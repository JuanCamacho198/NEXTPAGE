use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{HighlightDto, SaveHighlightInput};
use chrono::Utc;
use rusqlite::params;
use uuid::Uuid;

pub fn list_highlights(
    repo: &LibraryRepository,
    book_id: Option<&str>,
) -> AppResult<Vec<HighlightDto>> {
    let mut statement = repo.connection.prepare(
            "SELECT id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at
             FROM highlights
             WHERE (?1 IS NULL OR book_id = ?1) AND deleted_at IS NULL
             ORDER BY page ASC, rect_top ASC",
        )?;

    let rows = statement.query_map(params![book_id], |row| {
        Ok(HighlightDto {
            id: row.get(0)?,
            book_id: row.get(1)?,
            color: row.get(2)?,
            text: row.get(3)?,
            page: row.get(4)?,
            rect_left: row.get(5)?,
            rect_right: row.get(6)?,
            rect_top: row.get(7)?,
            rect_bottom: row.get(8)?,
            cfi: row.get(9)?,
            note: row.get(10)?,
            created_at: row.get(11)?,
            updated_at: row.get(12)?,
        })
    })?;

    let highlights = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(highlights)
}

pub fn save_highlight(
    repo: &LibraryRepository,
    payload: SaveHighlightInput,
) -> AppResult<HighlightDto> {
    if payload.book_id.trim().is_empty() {
        return Err(AppError::MissingBookId);
    }

    let page = payload.resolve_page_number()?;
    if page <= 0 {
        return Err(AppError::InvalidInput(
            "Highlight pageNumber must be greater than 0".to_string(),
        ));
    }

    if payload.color.trim().is_empty() {
        return Err(AppError::InvalidInput("Highlight color is required".to_string()));
    }

    if payload.text.trim().is_empty() {
        return Err(AppError::InvalidInput("Highlight text is required".to_string()));
    }

    let now = Utc::now().to_rfc3339();
    let id = Uuid::new_v4().to_string();

    repo.connection.execute(
            "INSERT INTO highlights (id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, 1)",
            params![
                id,
                payload.book_id,
                payload.color,
                payload.text,
                page,
                payload.rect_left,
                payload.rect_right,
                payload.rect_top,
                payload.rect_bottom,
                payload.cfi,
                payload.note,
                now,
                now
            ],
        )?;

    Ok(HighlightDto {
        id,
        book_id: payload.book_id,
        color: payload.color,
        text: payload.text,
        page,
        rect_left: payload.rect_left,
        rect_right: payload.rect_right,
        rect_top: payload.rect_top,
        rect_bottom: payload.rect_bottom,
        cfi: payload.cfi,
        note: payload.note,
        created_at: now.clone(),
        updated_at: now,
    })
}

pub fn delete_highlight(repo: &LibraryRepository, id: &str) -> AppResult<()> {
    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "UPDATE highlights SET deleted_at = ?1, version = version + 1 WHERE id = ?2",
        params![now, id],
    )?;
    Ok(())
}
