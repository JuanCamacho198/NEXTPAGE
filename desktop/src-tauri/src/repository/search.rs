use super::LibraryRepository;
use chrono::Utc;
use rusqlite::params;
use crate::error::{AppError, AppResult};
use crate::models::{IndexBookTextInput, SearchBookTextInput, SearchBookTextResponse, SearchResultDto};
use super::{DEFAULT_SEARCH_PAGE_SIZE, MAX_SEARCH_PAGE_SIZE};


    pub fn index_book_text(repo: &mut LibraryRepository, payload: IndexBookTextInput) -> AppResult<()> {
        let book_id = payload.book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let tx = repo.connection.transaction()?;

        tx.execute(
            "DELETE FROM book_text_fts WHERE book_id = ?1",
            params![book_id],
        )?;
        tx.execute(
            "DELETE FROM book_text_chunks WHERE book_id = ?1",
            params![book_id],
        )?;

        let now = Utc::now().to_rfc3339();
        for chunk in payload.chunks {
            if chunk.locator.trim().is_empty() || chunk.text_content.trim().is_empty() {
                continue;
            }

            let chunk_id = Uuid::new_v4().to_string();
            tx.execute(
                "INSERT INTO book_text_chunks (id, book_id, locator, chunk_index, text_content, created_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                params![
                    chunk_id,
                    book_id,
                    chunk.locator,
                    chunk.chunk_index,
                    chunk.text_content,
                    now,
                ],
            )?;

            tx.execute(
                "INSERT INTO book_text_fts (chunk_id, book_id, locator, text_content)
                 VALUES (?1, ?2, ?3, ?4)",
                params![chunk_id, book_id, chunk.locator, chunk.text_content],
            )?;
        }

        tx.commit()?;
        Ok(())
    }

    pub fn search_book_text(repo: &LibraryRepository,
        &self,
        payload: SearchBookTextInput,
    ) -> AppResult<SearchBookTextResponse> {
        let book_id = payload.book_id.trim();
        if book_id.is_empty() {
            return Err(AppError::MissingBookId);
        }

        let query = LibraryRepository::build_fts_match_query(&payload.query)?;
        let page = payload.page.max(1);
        let page_size = if payload.page_size <= 0 {
            DEFAULT_SEARCH_PAGE_SIZE
        } else {
            payload.page_size.min(MAX_SEARCH_PAGE_SIZE)
        };

        let total: i64 = repo.connection.query_row(
            "SELECT COUNT(*)
             FROM book_text_fts
             WHERE book_id = ?1
               AND book_text_fts MATCH ?2",
            params![book_id, &query],
            |row| row.get(0),
        )?;

        let offset = (page - 1) * page_size;
        if offset >= total {
            return Ok(SearchBookTextResponse {
                items: Vec::new(),
                total,
                page,
                page_size,
            });
        }

        let mut statement = repo.connection.prepare(
            "SELECT fts.chunk_id,
                    fts.book_id,
                    fts.locator,
                    snippet(book_text_fts, 3, '[', ']', '...', 18) AS snippet,
                    bm25(book_text_fts) AS rank
             FROM book_text_fts fts
             JOIN book_text_chunks chunks
               ON chunks.id = fts.chunk_id
             WHERE fts.book_id = ?1
               AND book_text_fts MATCH ?2
             ORDER BY rank ASC, chunks.chunk_index ASC, fts.chunk_id ASC
             LIMIT ?3 OFFSET ?4",
        )?;

        let rows = statement.query_map(params![book_id, &query, page_size, offset], |row| {
            Ok(SearchResultDto {
                chunk_id: row.get(0)?,
                book_id: row.get(1)?,
                locator: row.get(2)?,
                snippet: row.get(3)?,
                rank: row.get(4)?,
            })
        })?;

        let items = rows.collect::<Result<Vec<_>, _>>()?;
        Ok(SearchBookTextResponse {
            items,
            total,
            page,
            page_size,
        })
    }
