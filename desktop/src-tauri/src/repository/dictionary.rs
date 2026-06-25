use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{AddDictionaryWordInput, DictionaryWordDto};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use uuid::Uuid;

fn normalize_word(word: &str) -> String {
    word.trim().to_lowercase()
}

pub fn list_dictionary_words(repo: &LibraryRepository) -> AppResult<Vec<DictionaryWordDto>> {
    let mut statement = repo.connection.prepare(
        "SELECT id, word, created_at FROM dictionary_words ORDER BY normalized_word ASC",
    )?;

    let rows = statement.query_map([], |row| {
        Ok(DictionaryWordDto { id: row.get(0)?, word: row.get(1)?, created_at: row.get(2)? })
    })?;

    let words = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(words)
}

pub fn add_dictionary_word(
    repo: &LibraryRepository,
    input: AddDictionaryWordInput,
) -> AppResult<DictionaryWordDto> {
    let word = input.word.trim();
    if word.is_empty() {
        return Err(AppError::InvalidInput("Word is required".to_string()));
    }
    if word.len() > 200 {
        return Err(AppError::InvalidInput("Word must be 200 characters or less".to_string()));
    }

    let normalized = normalize_word(word);
    let now = Utc::now().to_rfc3339();

    if let Some(existing) = find_word_by_normalized(repo, &normalized)? {
        return Ok(existing);
    }

    let id = Uuid::new_v4().to_string();
    repo.connection.execute(
        "INSERT INTO dictionary_words (id, word, normalized_word, created_at)
         VALUES (?1, ?2, ?3, ?4)",
        params![id, word, normalized, now],
    )?;

    Ok(DictionaryWordDto { id, word: word.to_string(), created_at: now })
}

fn find_word_by_normalized(
    repo: &LibraryRepository,
    normalized: &str,
) -> AppResult<Option<DictionaryWordDto>> {
    let mut statement = repo
        .connection
        .prepare("SELECT id, word, created_at FROM dictionary_words WHERE normalized_word = ?1")?;

    let result = statement
        .query_row(params![normalized], |row| {
            Ok(DictionaryWordDto { id: row.get(0)?, word: row.get(1)?, created_at: row.get(2)? })
        })
        .optional()?;

    Ok(result)
}

pub fn remove_dictionary_word(repo: &LibraryRepository, id: &str) -> AppResult<()> {
    let rows =
        repo.connection.execute("DELETE FROM dictionary_words WHERE id = ?1", params![id])?;

    if rows == 0 {
        return Err(AppError::NotFound(format!("Dictionary word {} not found", id)));
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::new_repository;

    #[test]
    fn add_and_list_dictionary_words() {
        let repo = new_repository();
        let first =
            add_dictionary_word(&repo, AddDictionaryWordInput { word: "Serendipity".to_string() })
                .unwrap();

        assert_eq!(first.word, "Serendipity");

        let second = add_dictionary_word(
            &repo,
            AddDictionaryWordInput { word: "  serendipity ".to_string() },
        )
        .unwrap();

        assert_eq!(first.id, second.id);

        let words = list_dictionary_words(&repo).unwrap();
        assert_eq!(words.len(), 1);
    }

    #[test]
    fn remove_dictionary_word_requires_existing_id() {
        let repo = new_repository();
        let result = remove_dictionary_word(&repo, "missing-id");
        assert!(matches!(result, Err(AppError::NotFound(_))));
    }

    #[test]
    fn remove_dictionary_word_deletes_existing_word() {
        let repo = new_repository();
        let word =
            add_dictionary_word(&repo, AddDictionaryWordInput { word: "Ephemeral".to_string() })
                .unwrap();

        remove_dictionary_word(&repo, &word.id).unwrap();

        let words = list_dictionary_words(&repo).unwrap();
        assert!(words.is_empty());
    }
}
