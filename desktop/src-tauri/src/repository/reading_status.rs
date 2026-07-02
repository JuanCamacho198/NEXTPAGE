use rusqlite::params;
use rusqlite::OptionalExtension;

use crate::error::AppResult;

use super::LibraryRepository;

pub fn set_reading_status(
    repo: &LibraryRepository,
    book_id: &str,
    status: Option<&str>,
) -> AppResult<()> {
    if let Some(s) = status {
        repo.connection.execute(
            "INSERT INTO book_reading_status (book_id, status, updated_at)
             VALUES (?1, ?2, datetime('now'))
             ON CONFLICT(book_id) DO UPDATE SET
               status = excluded.status,
               updated_at = datetime('now')",
            params![book_id, s],
        )?;
    } else {
        repo.connection
            .execute("DELETE FROM book_reading_status WHERE book_id = ?1", params![book_id])?;
    }
    Ok(())
}

pub fn get_reading_status(repo: &LibraryRepository, book_id: &str) -> AppResult<Option<String>> {
    let result = repo
        .connection
        .query_row(
            "SELECT status FROM book_reading_status WHERE book_id = ?1",
            params![book_id],
            |row| row.get(0),
        )
        .optional()?;
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::new_repository;

    #[test]
    fn test_set_and_get_reading_status() {
        let repo = new_repository();
        crate::repository::tests::insert_book(&repo, "book-1", "/path/book-1.epub");

        // Initially no status
        assert_eq!(get_reading_status(&repo, "book-1").unwrap(), None);

        // Set to 'reading'
        set_reading_status(&repo, "book-1", Some("reading")).unwrap();
        assert_eq!(get_reading_status(&repo, "book-1").unwrap(), Some("reading".to_string()));

        // Upsert to 'completed'
        set_reading_status(&repo, "book-1", Some("completed")).unwrap();
        assert_eq!(get_reading_status(&repo, "book-1").unwrap(), Some("completed".to_string()));

        // Clear status
        set_reading_status(&repo, "book-1", None).unwrap();
        assert_eq!(get_reading_status(&repo, "book-1").unwrap(), None);
    }

    #[test]
    fn test_set_reading_status_with_invalid_status_fails() {
        let repo = new_repository();
        crate::repository::tests::insert_book(&repo, "book-1", "/path/book-1.epub");

        let result = set_reading_status(&repo, "book-1", Some("invalid"));
        assert!(result.is_err());
    }
}
