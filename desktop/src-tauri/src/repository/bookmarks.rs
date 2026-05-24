use super::LibraryRepository;
use chrono::Utc;
use rusqlite::params;
use crate::error::{AppError, AppResult};
use crate::models::{BookmarkDto, SaveBookmarkInput};


    pub fn list_bookmarks(repo: &LibraryRepository, book_id: Option<&str>) -> AppResult<Vec<BookmarkDto>> {
        let mut statement = repo.connection.prepare(
            "SELECT id, book_id, page, position, title, created_at
             FROM bookmarks
             WHERE (?1 IS NULL OR book_id = ?1) AND deleted_at IS NULL
             ORDER BY page ASC, position ASC",
        )?;

        let rows = statement.query_map(params![book_id], |row| {
            Ok(BookmarkDto {
                id: row.get(0)?,
                book_id: row.get(1)?,
                page: row.get(2)?,
                position: row.get(3)?,
                title: row.get(4)?,
                created_at: row.get(5)?,
            })
        })?;

        let bookmarks = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(bookmarks)
    }

    pub fn save_bookmark(repo: &LibraryRepository, payload: SaveBookmarkInput) -> AppResult<BookmarkDto> {
        if payload.book_id.trim().is_empty() {
            return Err(AppError::MissingBookId);
        }

        let now = Utc::now().to_rfc3339();
        let id = Uuid::new_v4().to_string();

        repo.connection.execute(
            "INSERT INTO bookmarks (id, book_id, page, position, title, created_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, 1)",
            params![
                id,
                payload.book_id,
                payload.page,
                payload.position,
                payload.title,
                now
            ],
        )?;

        Ok(BookmarkDto {
            id,
            book_id: payload.book_id,
            page: payload.page,
            position: payload.position,
            title: payload.title,
            created_at: now,
        })
    }

    pub fn delete_bookmark(repo: &LibraryRepository, id: &str) -> AppResult<()> {
        let now = Utc::now().to_rfc3339();
        repo.connection.execute(
            "UPDATE bookmarks SET deleted_at = ?1, version = version + 1 WHERE id = ?2",
            params![now, id],
        )?;
        Ok(())
    }
