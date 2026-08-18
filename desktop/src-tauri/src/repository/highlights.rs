use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{HighlightDto, SaveHighlightInput, UpdateHighlightInput};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
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
    // Honor the client-supplied id (sync pulls re-apply the same row and
    // MUST NOT create a duplicate). Fall back to a fresh UUID for legacy
    // callers that do not send one.
    let id = payload
        .id
        .clone()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| Uuid::new_v4().to_string());

    // UPSERT on the primary key: an existing row with the same id is
    // updated in place (fields + version bump), preserving created_at.
    repo.connection.execute(
            "INSERT INTO highlights (id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, 1)
             ON CONFLICT(id) DO UPDATE SET
               book_id = excluded.book_id,
               color = excluded.color,
               text = excluded.text,
               page = excluded.page,
               rect_left = excluded.rect_left,
               rect_right = excluded.rect_right,
               rect_top = excluded.rect_top,
               rect_bottom = excluded.rect_bottom,
               cfi = excluded.cfi,
               note = excluded.note,
               updated_at = excluded.updated_at,
               version = highlights.version + 1",
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::UpdateHighlightInput;
    use crate::repository::tests::new_repository;
    use chrono::Utc;
    use rusqlite::params;
    use uuid::Uuid;

    fn insert_book_and_highlight(repo: &LibraryRepository) -> String {
        let now = Utc::now().to_rfc3339();
        repo.connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', 'C:/book.epub', 'epub', 'local', 0, 100, ?2, ?2, 1)",
                params!["book-1", now],
            )
            .unwrap();
        let id = Uuid::new_v4().to_string();
        repo.connection
            .execute(
                "INSERT INTO highlights (id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at, version)
                 VALUES (?1, 'book-1', '#FACC15', 'sample', 1, 0, 10, 0, 10, NULL, NULL, ?2, ?2, 1)",
                params![id, now],
            )
            .unwrap();
        id
    }

    #[test]
    fn update_highlight_color_persists() {
        let repo = new_repository();
        let id = insert_book_and_highlight(&repo);
        let updated = update_highlight(
            &repo,
            UpdateHighlightInput { id: id.clone(), color: Some("#4ADE80".to_string()), note: None },
        )
        .unwrap();
        assert_eq!(updated.color, "#4ADE80");

        let color: String = repo
            .connection
            .query_row("SELECT color FROM highlights WHERE id = ?1", params![id], |row| row.get(0))
            .unwrap();
        assert_eq!(color, "#4ADE80");
    }

    #[test]
    fn update_highlight_note_persists() {
        let repo = new_repository();
        let id = insert_book_and_highlight(&repo);
        let updated = update_highlight(
            &repo,
            UpdateHighlightInput { id: id.clone(), color: None, note: Some("A note".to_string()) },
        )
        .unwrap();
        assert_eq!(updated.note, Some("A note".to_string()));
    }

    #[test]
    fn update_highlight_clears_note_with_empty_string() {
        let repo = new_repository();
        let id = insert_book_and_highlight(&repo);
        update_highlight(
            &repo,
            UpdateHighlightInput { id: id.clone(), color: None, note: Some("A note".to_string()) },
        )
        .unwrap();
        let updated = update_highlight(
            &repo,
            UpdateHighlightInput { id: id.clone(), color: None, note: Some("".to_string()) },
        )
        .unwrap();
        assert_eq!(updated.note, Some("".to_string()));
    }

    #[test]
    fn update_highlight_not_found_returns_error() {
        let repo = new_repository();
        let result = update_highlight(
            &repo,
            UpdateHighlightInput {
                id: "missing-id".to_string(),
                color: Some("#4ADE80".to_string()),
                note: None,
            },
        );
        assert!(matches!(result, Err(AppError::NotFound(_))));
    }

    #[test]
    fn save_highlight_respects_client_id_and_upserts() {
        let repo = new_repository();
        let now = Utc::now().to_rfc3339();
        repo.connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', 'C:/book.epub', 'epub', 'local', 0, 100, ?2, ?2, 1)",
                params!["book-1", now],
            )
            .unwrap();

        let base = SaveHighlightInput {
            id: Some("client-id-1".to_string()),
            book_id: "book-1".to_string(),
            color: "#FACC15".to_string(),
            text: "sample".to_string(),
            page_number: Some(1),
            page: None,
            rect_left: 0.0,
            rect_right: 10.0,
            rect_top: 0.0,
            rect_bottom: 10.0,
            cfi: Some("epubcfi(/6/1!)".to_string()),
            note: None,
        };

        // First save inserts exactly one row.
        let first = save_highlight(&repo, base.clone()).unwrap();
        assert_eq!(first.id, "client-id-1");
        let count: i64 = repo
            .connection
            .query_row(
                "SELECT count(*) FROM highlights WHERE id = 'client-id-1'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(count, 1);

        // Re-applying the same id (sync pull) MUST upsert, not duplicate.
        let second = save_highlight(&repo, base).unwrap();
        assert_eq!(second.id, "client-id-1");
        let count: i64 = repo
            .connection
            .query_row(
                "SELECT count(*) FROM highlights WHERE id = 'client-id-1'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn save_highlight_generates_id_when_absent() {
        let repo = new_repository();
        let now = Utc::now().to_rfc3339();
        repo.connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', 'C:/book.epub', 'epub', 'local', 0, 100, ?2, ?2, 1)",
                params!["book-1", now],
            )
            .unwrap();

        let saved = save_highlight(
            &repo,
            SaveHighlightInput {
                id: None,
                book_id: "book-1".to_string(),
                color: "#FACC15".to_string(),
                text: "sample".to_string(),
                page_number: Some(1),
                page: None,
                rect_left: 0.0,
                rect_right: 10.0,
                rect_top: 0.0,
                rect_bottom: 10.0,
                cfi: None,
                note: None,
            },
        )
        .unwrap();
        assert!(!saved.id.is_empty());
    }
}

pub fn update_highlight(
    repo: &LibraryRepository,
    input: UpdateHighlightInput,
) -> AppResult<HighlightDto> {
    let existing: Option<HighlightDto> = repo
        .connection
        .query_row(
            "SELECT id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at
             FROM highlights
             WHERE id = ?1 AND deleted_at IS NULL",
            params![input.id],
            |row| {
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
            },
        )
        .optional()?;

    let mut highlight =
        existing.ok_or_else(|| AppError::NotFound(format!("Highlight {} not found", input.id)))?;

    let mut updates: Vec<(&str, &dyn rusqlite::ToSql)> = Vec::new();

    if let Some(color) = input.color.as_ref() {
        if color.trim().is_empty() {
            return Err(AppError::InvalidInput("Highlight color cannot be empty".to_string()));
        }
        highlight.color = color.clone();
        updates.push(("color = ?", color as &dyn rusqlite::ToSql));
    }

    if input.note.is_some() {
        highlight.note = input.note.clone();
        updates.push(("note = ?", &highlight.note as &dyn rusqlite::ToSql));
    }

    if updates.is_empty() {
        return Ok(highlight);
    }

    let now = Utc::now().to_rfc3339();
    let set_clause = updates.iter().map(|(clause, _)| *clause).collect::<Vec<_>>().join(", ");
    let mut params_values: Vec<&dyn rusqlite::ToSql> =
        updates.iter().map(|(_, value)| *value).collect();
    params_values.push(&now);
    params_values.push(&input.id);

    repo.connection.execute(
        &format!(
            "UPDATE highlights SET {}, updated_at = ?, version = version + 1 WHERE id = ?",
            set_clause
        ),
        params_values.as_slice(),
    )?;

    highlight.updated_at = now;
    Ok(highlight)
}
