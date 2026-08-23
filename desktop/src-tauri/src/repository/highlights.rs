use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    HighlightDto, RemoteHighlightRow, SaveHighlightInput, UpdateHighlightInput, UpsertRemoteSummary,
};
use chrono::{DateTime, TimeZone, Utc};
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
    if page < 0 {
        return Err(AppError::InvalidInput(
            "Highlight pageNumber must be non-negative".to_string(),
        ));
    }

    if payload.color.trim().is_empty() {
        return Err(AppError::InvalidInput("Highlight color is required".to_string()));
    }

    if payload.text.trim().is_empty() {
        return Err(AppError::InvalidInput("Highlight text is required".to_string()));
    }

    // CFI-first invariant (spec: highlights.rs cfi_range non-null EPUB)
    if let Ok(Some(fmt)) = repo
        .connection
        .query_row("SELECT format FROM books WHERE id = ?1", params![&payload.book_id], |r| {
            r.get::<_, String>(0)
        })
        .optional()
    {
        if fmt.eq_ignore_ascii_case("epub") {
            let has_cfi = payload.cfi.as_ref().map(|s| !s.trim().is_empty()).unwrap_or(false);
            if !has_cfi {
                return Err(AppError::InvalidInput(
                    "EPUB highlights require cfiRange (CFI-first)".to_string(),
                ));
            }
        }
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

fn epoch_millis_to_rfc3339(epoch_millis: i64) -> String {
    Utc.timestamp_millis_opt(epoch_millis).single().unwrap_or_else(Utc::now).to_rfc3339()
}

fn rfc3339_to_epoch_millis(value: &str) -> i64 {
    DateTime::parse_from_rfc3339(value).map(|dt| dt.timestamp_millis()).unwrap_or(0)
}

/// Bulk upsert for remote highlights (DHR-1..5, WS2).
/// Per-row order: (1) validation (blank id/bookId/text/color, negative page,
/// epub+empty-CFI → skipped_invalid) → (2) FK guard (books.id) →
/// skipped_unknown_book → (3) LWW + tombstone branches → (4) INSERT..ON CONFLICT
/// with remote clocks preserved (RFC3339 from epoch), created_at preserved on
/// conflict, rects zero-filled. Row-by-row, no transaction (sessions precedent).
pub fn upsert_remote_highlights(
    repo: &LibraryRepository,
    rows: &[RemoteHighlightRow],
) -> AppResult<UpsertRemoteSummary> {
    let mut applied: i64 = 0;
    let mut skipped_unknown_book: i64 = 0;
    let mut skipped_invalid: i64 = 0;

    for row in rows {
        // ── 1) validation: blank fields / negative page / epub+empty-cfi ──
        let id_blank = row.id.trim().is_empty();
        let book_blank = row.book_id.trim().is_empty();
        let text_blank = row.text_content.trim().is_empty();
        let color_blank = row.color.trim().is_empty();
        if id_blank || book_blank || text_blank || color_blank {
            skipped_invalid += 1;
            continue;
        }
        if let Some(page) = row.page {
            if page < 0 {
                skipped_invalid += 1;
                continue;
            }
        }
        // CFI-first invariant for EPUB (same as save_highlight:65-80), PDF exempt.
        let cfi_empty = row.cfi_range.as_ref().map(|s| s.trim().is_empty()).unwrap_or(true);
        if cfi_empty {
            // Only mark invalid if the local book is EPUB; if the book is absent
            // locally we defer to the FK guard (skipped_unknown_book).
            if let Ok(Some(fmt)) = repo
                .connection
                .query_row("SELECT format FROM books WHERE id = ?1", params![&row.book_id], |r| {
                    r.get::<_, String>(0)
                })
                .optional()
            {
                if fmt.eq_ignore_ascii_case("epub") {
                    skipped_invalid += 1;
                    continue;
                }
            }
        }

        // ── 2) FK guard ── (stub creation if missing so bulk pull before catalog sync doesn't lose data)
        let book_exists: bool = repo.connection.query_row(
            "SELECT EXISTS(SELECT 1 FROM books WHERE id = ?1)",
            params![&row.book_id],
            |r| r.get(0),
        )?;
        if !book_exists {
            eprintln!(
                "FK guard: skipping highlight {} for unknown book {} — attempting stub creation",
                row.id, row.book_id
            );
            let now = Utc::now().to_rfc3339();
            let stub_format =
                if row.cfi_range.as_ref().map(|s| !s.trim().is_empty()).unwrap_or(false) {
                    "epub"
                } else {
                    "pdf"
                };
            let stub_title =
                format!("Unknown Title (sync stub) {}", &row.book_id[..4.min(row.book_id.len())]);
            let stub_res = repo.connection.execute(
                "INSERT OR IGNORE INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, ?2, ?3, ?4, ?5, 'synced', 0, 0, ?6, ?6, 1)",
                params![row.book_id, stub_title, "Unknown", "", stub_format, now],
            );
            match stub_res {
                Ok(changed) if changed > 0 => {
                    eprintln!(
                        "FK guard: created stub book {} format {} for highlight {}",
                        row.book_id, stub_format, row.id
                    );
                }
                Ok(_) => {
                    // 0 rows changed means another thread already inserted the stub (race) — proceed
                }
                Err(e) => {
                    eprintln!("FK guard: failed to create stub book {}: {}", row.book_id, e);
                    skipped_unknown_book += 1;
                    continue;
                }
            }
            // Verify stub now exists before proceeding to highlight insert
            let still_missing: bool = !repo.connection.query_row(
                "SELECT EXISTS(SELECT 1 FROM books WHERE id = ?1)",
                params![&row.book_id],
                |r| r.get(0),
            )?;
            if still_missing {
                eprintln!(
                    "FK guard: still missing book {} after stub attempt — skipping highlight {}",
                    row.book_id, row.id
                );
                skipped_unknown_book += 1;
                continue;
            }
        }

        // ── 3) LWW + tombstone branches (DQ2) ──
        let remote_is_deleted = row.deleted_at_epoch_millis.is_some();
        let local_opt: Option<(String, Option<String>)> = repo
            .connection
            .query_row(
                "SELECT updated_at, deleted_at FROM highlights WHERE id = ?1",
                params![&row.id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;

        // Determine whether to apply and what deleted_at to store
        let target_deleted_at: Option<String>;
        match local_opt {
            None => {
                if remote_is_deleted {
                    // Remote tombstone with no local counterpart → converged no-op
                    // (mirrors desktop realtime deleteHighlight affecting 0 rows
                    // and Android applyRemoteHighlight null-guard).
                    applied += 1;
                    continue;
                }
                // Remote live, no local → insert as live
                target_deleted_at = None;
            }
            Some((local_updated_at, local_deleted_at)) => {
                let local_is_deleted = local_deleted_at.is_some();
                if !remote_is_deleted && local_is_deleted {
                    // Remote live vs local tombstoned → never resurrect (DQ2)
                    continue;
                } else if remote_is_deleted && !local_is_deleted {
                    // Remote tombstone vs local live → apply tombstone unconditionally
                    target_deleted_at =
                        Some(epoch_millis_to_rfc3339(row.deleted_at_epoch_millis.unwrap()));
                } else if remote_is_deleted && local_is_deleted {
                    // Both deleted → later deletedAt wins, tie → recordId lex (same PK ⇒ skip)
                    let remote_deleted_epoch = row.deleted_at_epoch_millis.unwrap();
                    let local_deleted_epoch =
                        local_deleted_at.as_deref().map(rfc3339_to_epoch_millis).unwrap_or(0);
                    if remote_deleted_epoch > local_deleted_epoch {
                        target_deleted_at = Some(epoch_millis_to_rfc3339(remote_deleted_epoch));
                    } else if remote_deleted_epoch < local_deleted_epoch {
                        continue;
                    } else {
                        // tie — same PK always skips (idempotent)
                        continue;
                    }
                } else {
                    // Both live → LWW on updatedAt, tie → recordId lex (same PK ⇒ skip)
                    let remote_epoch = row.updated_at_epoch_millis;
                    let local_epoch = rfc3339_to_epoch_millis(&local_updated_at);
                    if remote_epoch > local_epoch {
                        target_deleted_at = None;
                    } else {
                        // older or tie (same id ⇒ no-op, idempotent)
                        continue;
                    }
                }
            }
        }

        let final_deleted_at: Option<String> = target_deleted_at;

        // ── 4) INSERT..ON CONFLICT (remote clocks preserved) ──
        let updated_at_rfc3339 = epoch_millis_to_rfc3339(row.updated_at_epoch_millis);
        // created_at for new rows = remote clock (or now fallback already in epoch conversion)
        let created_at_rfc3339 = updated_at_rfc3339.clone();
        let page_value: i64 = row.page.unwrap_or(0);
        let cfi_value: Option<String> = row.cfi_range.clone();
        // Rects zero-filled per design (no locator columns on desktop).
        repo.connection.execute(
            "INSERT INTO highlights (id, book_id, color, text, page, rect_left, rect_right, rect_top, rect_bottom, cfi, note, created_at, updated_at, deleted_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, 0, 0, 0, 0, ?6, ?7, ?8, ?9, ?10, 1)
             ON CONFLICT(id) DO UPDATE SET
               book_id = excluded.book_id,
               color = excluded.color,
               text = excluded.text,
               page = excluded.page,
               rect_left = 0,
               rect_right = 0,
               rect_top = 0,
               rect_bottom = 0,
               cfi = excluded.cfi,
               note = excluded.note,
               updated_at = excluded.updated_at,
               deleted_at = excluded.deleted_at,
               version = highlights.version + 1",
            params![
                row.id,
                row.book_id,
                row.color,
                row.text_content,
                page_value,
                cfi_value,
                row.note,
                created_at_rfc3339,
                updated_at_rfc3339,
                final_deleted_at,
            ],
        )?;
        applied += 1;
    }

    Ok(UpsertRemoteSummary { applied, skipped_unknown_book, skipped_invalid })
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
            UpdateHighlightInput {
                id: id.clone(),
                color: Some("#4ADE80".to_string()),
                note: None,
                page: None,
                page_number: None,
            },
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
            UpdateHighlightInput {
                id: id.clone(),
                color: None,
                note: Some("A note".to_string()),
                page: None,
                page_number: None,
            },
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
            UpdateHighlightInput {
                id: id.clone(),
                color: None,
                note: Some("A note".to_string()),
                page: None,
                page_number: None,
            },
        )
        .unwrap();
        let updated = update_highlight(
            &repo,
            UpdateHighlightInput {
                id: id.clone(),
                color: None,
                note: Some("".to_string()),
                page: None,
                page_number: None,
            },
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
                page: None,
                page_number: None,
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
            .query_row("SELECT count(*) FROM highlights WHERE id = 'client-id-1'", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(count, 1);

        // Re-applying the same id (sync pull) MUST upsert, not duplicate.
        let second = save_highlight(&repo, base).unwrap();
        assert_eq!(second.id, "client-id-1");
        let count: i64 = repo
            .connection
            .query_row("SELECT count(*) FROM highlights WHERE id = 'client-id-1'", [], |row| {
                row.get(0)
            })
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
                cfi: Some("epubcfi(/6/2!)".to_string()),
                note: None,
            },
        )
        .unwrap();
        assert!(!saved.id.is_empty());
    }

    #[test]
    fn save_highlight_rejects_epub_without_cfi() {
        let repo = new_repository();
        let now = Utc::now().to_rfc3339();
        repo.connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', 'C:/book.epub', 'epub', 'local', 0, 100, ?2, ?2, 1)",
                params!["book-cfi-req", now],
            )
            .unwrap();
        let err = save_highlight(
            &repo,
            SaveHighlightInput {
                id: None,
                book_id: "book-cfi-req".to_string(),
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
        .unwrap_err();
        assert!(matches!(err, AppError::InvalidInput(_)));
    }

    // ─── upsert_remote_highlights (DHR-1..5) ──────────────────────────

    fn insert_book_with_format(repo: &LibraryRepository, id: &str, format: &str) {
        let now = Utc::now().to_rfc3339();
        repo.connection
            .execute(
                "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
                 VALUES (?1, 'Book', 'Author', 'C:/book.epub', ?2, 'local', 0, 100, ?3, ?3, 1)",
                params![id, format, now],
            )
            .unwrap();
    }

    fn remote_highlight(
        id: &str,
        book_id: &str,
        cfi: Option<&str>,
        updated_epoch: i64,
        deleted_epoch: Option<i64>,
    ) -> crate::models::RemoteHighlightRow {
        crate::models::RemoteHighlightRow {
            id: id.to_string(),
            user_id: "user-1".to_string(),
            book_id: book_id.to_string(),
            cfi_range: cfi.map(|s| s.to_string()),
            text_content: "sample text".to_string(),
            note: None,
            color: "#FACC15".to_string(),
            page: Some(1),
            updated_at_epoch_millis: updated_epoch,
            deleted_at_epoch_millis: deleted_epoch,
        }
    }

    #[test]
    fn upsert_remote_highlights_lww_newer_applies_older_skips_tie_skips() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-lww", "epub");

        // Seed local via first pull at 1000
        let first = remote_highlight("hl-lww", "book-lww", Some("epubcfi(/6/1)"), 1000, None);
        let summary = upsert_remote_highlights(&repo, &[first]).unwrap();
        assert_eq!(summary.applied, 1);

        // Newer (2000) applies
        let newer = remote_highlight("hl-lww", "book-lww", Some("epubcfi(/6/1)"), 2000, None);
        // Change text to verify update
        let mut newer2 = newer.clone();
        newer2.text_content = "newer text".to_string();
        let summary = upsert_remote_highlights(&repo, &[newer2]).unwrap();
        assert_eq!(summary.applied, 1);
        let text: String = repo
            .connection
            .query_row("SELECT text FROM highlights WHERE id='hl-lww'", [], |r| r.get(0))
            .unwrap();
        assert_eq!(text, "newer text");

        // Older (500) skipped
        let older = remote_highlight("hl-lww", "book-lww", Some("epubcfi(/6/1)"), 500, None);
        let summary = upsert_remote_highlights(&repo, &[older]).unwrap();
        assert_eq!(summary.applied, 0);

        // Tie (2000 == local) → recordId lex: same id ⇒ skip (idempotent)
        let tie = remote_highlight("hl-lww", "book-lww", Some("epubcfi(/6/1)"), 2000, None);
        let summary = upsert_remote_highlights(&repo, &[tie]).unwrap();
        assert_eq!(summary.applied, 0);
    }

    #[test]
    fn upsert_remote_highlights_clock_preserved_not_now() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-clock", "epub");
        let remote_epoch: i64 = 1_700_000_000_000;
        let row =
            remote_highlight("hl-clock", "book-clock", Some("epubcfi(/6/2)"), remote_epoch, None);
        let before = Utc::now().timestamp_millis();
        let summary = upsert_remote_highlights(&repo, &[row]).unwrap();
        assert_eq!(summary.applied, 1);
        let after = Utc::now().timestamp_millis();

        let updated_at: String = repo
            .connection
            .query_row("SELECT updated_at FROM highlights WHERE id='hl-clock'", [], |r| r.get(0))
            .unwrap();
        let stored_epoch = rfc3339_to_epoch_millis(&updated_at);
        assert_eq!(stored_epoch, remote_epoch);
        assert!(stored_epoch < before || stored_epoch <= after);
        // Must NOT be now() window — remote epoch is fixed far from before/after if we used now it would be in window
        // Since remote_epoch is 2023, it is not inside the before/after window (2026)
        assert!(stored_epoch < before);
    }

    #[test]
    fn upsert_remote_highlights_skips_unknown_book() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-known", "epub");
        let known = remote_highlight("hl-known", "book-known", Some("epubcfi(/6/1)"), 1000, None);
        let unknown =
            remote_highlight("hl-unknown", "book-absent", Some("epubcfi(/6/1)"), 1000, None);
        let summary = upsert_remote_highlights(&repo, &[known, unknown]).unwrap();
        // FK guard now creates a stub book instead of dropping the highlight (fixes PC Odisea 0 highlights)
        assert_eq!(summary.applied, 2);
        assert_eq!(summary.skipped_unknown_book, 0);
        assert_eq!(summary.skipped_invalid, 0);
        let count: i64 =
            repo.connection.query_row("SELECT COUNT(*) FROM highlights", [], |r| r.get(0)).unwrap();
        assert_eq!(count, 2);
        let stub_exists: bool = repo
            .connection
            .query_row("SELECT EXISTS(SELECT 1 FROM books WHERE id='book-absent')", [], |r| {
                r.get(0)
            })
            .unwrap();
        assert!(stub_exists);
    }

    #[test]
    fn upsert_remote_highlights_epub_empty_cfi_invalid_while_siblings_apply() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-epub", "epub");
        let valid = remote_highlight("hl-valid", "book-epub", Some("epubcfi(/6/1)"), 1000, None);
        let invalid = remote_highlight("hl-invalid", "book-epub", Some(""), 1000, None);
        // cfi None also invalid for epub
        let mut invalid_none = remote_highlight("hl-invalid2", "book-epub", None, 1000, None);
        invalid_none.cfi_range = None;
        let summary = upsert_remote_highlights(&repo, &[valid, invalid, invalid_none]).unwrap();
        assert_eq!(summary.applied, 1);
        assert_eq!(summary.skipped_invalid, 2);
        assert_eq!(summary.skipped_unknown_book, 0);
        let count: i64 =
            repo.connection.query_row("SELECT COUNT(*) FROM highlights", [], |r| r.get(0)).unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn upsert_remote_highlights_pdf_empty_cfi_applies() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-pdf", "pdf");
        // Empty CFI for PDF should NOT be invalid
        let row_empty = remote_highlight("hl-pdf-empty", "book-pdf", Some(""), 1000, None);
        let mut row_none = remote_highlight("hl-pdf-none", "book-pdf", None, 1000, None);
        row_none.cfi_range = None;
        let summary = upsert_remote_highlights(&repo, &[row_empty, row_none]).unwrap();
        assert_eq!(summary.applied, 2);
        assert_eq!(summary.skipped_invalid, 0);
        let count: i64 = repo
            .connection
            .query_row("SELECT COUNT(*) FROM highlights WHERE book_id='book-pdf'", [], |r| r.get(0))
            .unwrap();
        assert_eq!(count, 2);
    }

    #[test]
    fn upsert_remote_highlights_tombstone_branches() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-tomb", "epub");

        // 1) remote-live / local-tombstoned ⇒ skip (never resurrect)
        let live = remote_highlight("hl-t1", "book-tomb", Some("epubcfi(/6/1)"), 1000, None);
        upsert_remote_highlights(&repo, &[live]).unwrap();
        // Tombstone it via newer remote tombstone
        let tomb = remote_highlight("hl-t1", "book-tomb", Some("epubcfi(/6/1)"), 2000, Some(2000));
        let s = upsert_remote_highlights(&repo, &[tomb]).unwrap();
        assert_eq!(s.applied, 1);
        // Now try to resurrect with remote live older/newer → must skip
        let resurrect = remote_highlight("hl-t1", "book-tomb", Some("epubcfi(/6/1)"), 3000, None);
        let s = upsert_remote_highlights(&repo, &[resurrect]).unwrap();
        assert_eq!(s.applied, 0);
        let deleted_at: Option<String> = repo
            .connection
            .query_row("SELECT deleted_at FROM highlights WHERE id='hl-t1'", [], |r| r.get(0))
            .unwrap();
        assert!(deleted_at.is_some());

        // 2) remote-tombstone / local-live ⇒ apply tombstone
        let live2 = remote_highlight("hl-t2", "book-tomb", Some("epubcfi(/6/2)"), 1000, None);
        upsert_remote_highlights(&repo, &[live2]).unwrap();
        let tomb2 = remote_highlight("hl-t2", "book-tomb", Some("epubcfi(/6/2)"), 2000, Some(2500));
        let s = upsert_remote_highlights(&repo, &[tomb2]).unwrap();
        assert_eq!(s.applied, 1);
        let del: Option<String> = repo
            .connection
            .query_row("SELECT deleted_at FROM highlights WHERE id='hl-t2'", [], |r| r.get(0))
            .unwrap();
        assert!(del.is_some());

        // 3) both-deleted ⇒ later deletedAt wins
        // hl-t2 is now deleted at 2500. Try remote deleted at 2400 (older) ⇒ skip
        let older_del =
            remote_highlight("hl-t2", "book-tomb", Some("epubcfi(/6/2)"), 3000, Some(2400));
        let s = upsert_remote_highlights(&repo, &[older_del]).unwrap();
        assert_eq!(s.applied, 0);
        // Remote deleted at 2600 (newer) ⇒ apply
        let newer_del =
            remote_highlight("hl-t2", "book-tomb", Some("epubcfi(/6/2)"), 3000, Some(2600));
        let s = upsert_remote_highlights(&repo, &[newer_del]).unwrap();
        assert_eq!(s.applied, 1);

        // 4) tie on deletedAt ⇒ recordId lex → same id ⇒ skip
        let tie_del =
            remote_highlight("hl-t2", "book-tomb", Some("epubcfi(/6/2)"), 3000, Some(2600));
        let s = upsert_remote_highlights(&repo, &[tie_del]).unwrap();
        assert_eq!(s.applied, 0);
    }

    #[test]
    fn upsert_remote_highlights_remote_tombstone_without_local_counts_applied() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-tomb2", "epub");
        // Remote tombstone with no local row → converged no-op but counted as applied per DQ2
        let tomb =
            remote_highlight("hl-no-local", "book-tomb2", Some("epubcfi(/6/1)"), 1000, Some(1000));
        let s = upsert_remote_highlights(&repo, &[tomb]).unwrap();
        assert_eq!(s.applied, 1);
        assert_eq!(s.skipped_invalid, 0);
        assert_eq!(s.skipped_unknown_book, 0);
        let count: i64 = repo
            .connection
            .query_row("SELECT COUNT(*) FROM highlights WHERE id='hl-no-local'", [], |r| r.get(0))
            .unwrap();
        assert_eq!(count, 0);
    }

    #[test]
    fn upsert_remote_highlights_idempotent_rerun() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-idem", "epub");
        let row = remote_highlight("hl-idem", "book-idem", Some("epubcfi(/6/1)"), 1000, None);
        let s1 = upsert_remote_highlights(&repo, &[row.clone()]).unwrap();
        assert_eq!(s1.applied, 1);
        let s2 = upsert_remote_highlights(&repo, &[row]).unwrap();
        assert_eq!(s2.applied, 0);
        assert_eq!(s2.skipped_invalid, 0);
        assert_eq!(s2.skipped_unknown_book, 0);
    }

    #[test]
    fn upsert_remote_highlights_validation_blank_and_negative() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-valid", "epub");
        let mut blank_id = remote_highlight("", "book-valid", Some("epubcfi(/6/1)"), 1000, None);
        blank_id.id = "   ".to_string();
        let mut blank_text =
            remote_highlight("hl-blank-text", "book-valid", Some("epubcfi(/6/1)"), 1000, None);
        blank_text.text_content = "   ".to_string();
        let mut negative_page =
            remote_highlight("hl-neg", "book-valid", Some("epubcfi(/6/1)"), 1000, None);
        negative_page.page = Some(-1);
        let summary =
            upsert_remote_highlights(&repo, &[blank_id, blank_text, negative_page]).unwrap();
        assert_eq!(summary.skipped_invalid, 3);
        assert_eq!(summary.applied, 0);
    }

    #[test]
    fn upsert_remote_highlights_version_bump_and_created_at_preserved() {
        let repo = new_repository();
        insert_book_with_format(&repo, "book-ver", "epub");
        let row = remote_highlight("hl-ver", "book-ver", Some("epubcfi(/6/1)"), 1000, None);
        upsert_remote_highlights(&repo, &[row]).unwrap();
        let (created_before, version_before): (String, i64) = repo
            .connection
            .query_row("SELECT created_at, version FROM highlights WHERE id='hl-ver'", [], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .unwrap();
        // Apply newer
        let mut newer = remote_highlight("hl-ver", "book-ver", Some("epubcfi(/6/1)"), 2000, None);
        newer.text_content = "updated".to_string();
        upsert_remote_highlights(&repo, &[newer]).unwrap();
        let (created_after, version_after): (String, i64) = repo
            .connection
            .query_row("SELECT created_at, version FROM highlights WHERE id='hl-ver'", [], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .unwrap();
        assert_eq!(created_before, created_after);
        assert_eq!(version_after, version_before + 1);
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

    if input.page.is_some() || input.page_number.is_some() {
        let page = match (input.page_number, input.page) {
            (Some(pn), Some(p)) if pn != p => {
                return Err(AppError::InvalidInput(format!(
                    "Highlight payload has conflicting page fields: pageNumber={} page={}",
                    pn, p
                )))
            }
            (Some(pn), _) => pn,
            (None, Some(p)) => p,
            (None, None) => unreachable!(),
        };
        if page < 0 {
            return Err(AppError::InvalidInput(
                "Highlight pageNumber must be non-negative".to_string(),
            ));
        }
        highlight.page = page;
        updates.push(("page = ?", &highlight.page as &dyn rusqlite::ToSql));
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
