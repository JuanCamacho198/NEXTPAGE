use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{CreateTagInput, SaveHighlightTagsInput, TagDto};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use uuid::Uuid;

fn normalize_tag_name(name: &str) -> String {
    name.trim().to_lowercase()
}

pub fn list_tags(repo: &LibraryRepository) -> AppResult<Vec<TagDto>> {
    let mut statement = repo
        .connection
        .prepare("SELECT id, name, color, created_at FROM tags ORDER BY normalized_name ASC")?;

    let rows = statement.query_map([], |row| {
        Ok(TagDto {
            id: row.get(0)?,
            name: row.get(1)?,
            color: row.get(2)?,
            created_at: row.get(3)?,
        })
    })?;

    let tags = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(tags)
}

pub fn create_tag(repo: &LibraryRepository, input: CreateTagInput) -> AppResult<TagDto> {
    let name = input.name.trim();
    if name.is_empty() {
        return Err(AppError::InvalidInput("Tag name is required".to_string()));
    }
    if name.len() > 50 {
        return Err(AppError::InvalidInput("Tag name must be 50 characters or less".to_string()));
    }

    let normalized = normalize_tag_name(name);
    let now = Utc::now().to_rfc3339();

    if let Some(existing) = find_tag_by_normalized_name(repo, &normalized)? {
        return Ok(existing);
    }

    let id = Uuid::new_v4().to_string();
    repo.connection.execute(
        "INSERT INTO tags (id, name, normalized_name, color, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5)",
        params![id, name, normalized, input.color, now],
    )?;

    Ok(TagDto { id, name: name.to_string(), color: input.color, created_at: now })
}

fn find_tag_by_normalized_name(
    repo: &LibraryRepository,
    normalized: &str,
) -> AppResult<Option<TagDto>> {
    let mut statement = repo
        .connection
        .prepare("SELECT id, name, color, created_at FROM tags WHERE normalized_name = ?1")?;

    let result = statement
        .query_row(params![normalized], |row| {
            Ok(TagDto {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
                created_at: row.get(3)?,
            })
        })
        .optional()?;

    Ok(result)
}

pub fn list_tags_for_highlight(
    repo: &LibraryRepository,
    highlight_id: &str,
) -> AppResult<Vec<TagDto>> {
    let mut statement = repo.connection.prepare(
        "SELECT t.id, t.name, t.color, t.created_at
         FROM tags t
         INNER JOIN highlight_tags ht ON ht.tag_id = t.id
         WHERE ht.highlight_id = ?1
         ORDER BY t.normalized_name ASC",
    )?;

    let rows = statement.query_map(params![highlight_id], |row| {
        Ok(TagDto {
            id: row.get(0)?,
            name: row.get(1)?,
            color: row.get(2)?,
            created_at: row.get(3)?,
        })
    })?;

    let tags = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(tags)
}

pub fn save_highlight_tags(
    repo: &mut LibraryRepository,
    input: SaveHighlightTagsInput,
) -> AppResult<Vec<TagDto>> {
    let highlight_exists: bool = repo
        .connection
        .query_row(
            "SELECT 1 FROM highlights WHERE id = ?1 AND deleted_at IS NULL",
            params![input.highlight_id],
            |_row| Ok(true),
        )
        .optional()?
        .unwrap_or(false);

    if !highlight_exists {
        return Err(AppError::NotFound(format!("Highlight {} not found", input.highlight_id)));
    }

    let tx = repo.connection.transaction()?;
    tx.execute("DELETE FROM highlight_tags WHERE highlight_id = ?1", params![input.highlight_id])?;

    let now = Utc::now().to_rfc3339();
    for tag_id in &input.tag_ids {
        tx.execute(
            "INSERT INTO highlight_tags (highlight_id, tag_id, created_at)
             VALUES (?1, ?2, ?3)
             ON CONFLICT(highlight_id, tag_id) DO UPDATE SET created_at = excluded.created_at",
            params![input.highlight_id, tag_id, now],
        )?;
    }
    tx.commit()?;

    list_tags_for_highlight(repo, &input.highlight_id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::new_repository;
    use chrono::Utc;
    use rusqlite::params;
    use uuid::Uuid;

    #[test]
    fn create_tag_persists_and_returns_dto() {
        let repo = new_repository();
        let tag = create_tag(
            &repo,
            CreateTagInput { name: "Important".to_string(), color: Some("#ff0000".to_string()) },
        )
        .unwrap();

        assert_eq!(tag.name, "Important");
        assert_eq!(tag.color, Some("#ff0000".to_string()));

        let tags = list_tags(&repo).unwrap();
        assert_eq!(tags.len(), 1);
        assert_eq!(tags[0].name, "Important");
    }

    #[test]
    fn create_tag_returns_existing_on_normalized_duplicate() {
        let repo = new_repository();
        let first =
            create_tag(&repo, CreateTagInput { name: "Review".to_string(), color: None }).unwrap();

        let second = create_tag(
            &repo,
            CreateTagInput { name: "  review ".to_string(), color: Some("#00ff00".to_string()) },
        )
        .unwrap();

        assert_eq!(first.id, second.id);
        assert_eq!(second.color, None);
    }

    #[test]
    fn create_tag_rejects_empty_and_long_names() {
        let repo = new_repository();
        let empty = create_tag(&repo, CreateTagInput { name: "   ".to_string(), color: None });
        assert!(matches!(empty, Err(AppError::InvalidInput(_))));

        let long = create_tag(&repo, CreateTagInput { name: "a".repeat(51), color: None });
        assert!(matches!(long, Err(AppError::InvalidInput(_))));
    }

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
    fn save_highlight_tags_links_and_returns_assigned_tags() {
        let mut repo = new_repository();
        let highlight_id = insert_book_and_highlight(&repo);
        let tag =
            create_tag(&repo, CreateTagInput { name: "Review".to_string(), color: None }).unwrap();

        let assigned = save_highlight_tags(
            &mut repo,
            SaveHighlightTagsInput {
                highlight_id: highlight_id.clone(),
                tag_ids: vec![tag.id.clone()],
            },
        )
        .unwrap();

        assert_eq!(assigned.len(), 1);
        assert_eq!(assigned[0].id, tag.id);

        let for_highlight = list_tags_for_highlight(&repo, &highlight_id).unwrap();
        assert_eq!(for_highlight.len(), 1);
        assert_eq!(for_highlight[0].name, "Review");
    }

    #[test]
    fn save_highlight_tags_removes_unlisted_tags() {
        let mut repo = new_repository();
        let highlight_id = insert_book_and_highlight(&repo);
        let first =
            create_tag(&repo, CreateTagInput { name: "Review".to_string(), color: None }).unwrap();
        let second =
            create_tag(&repo, CreateTagInput { name: "Idea".to_string(), color: None }).unwrap();

        save_highlight_tags(
            &mut repo,
            SaveHighlightTagsInput {
                highlight_id: highlight_id.clone(),
                tag_ids: vec![first.id.clone(), second.id.clone()],
            },
        )
        .unwrap();

        let reassigned = save_highlight_tags(
            &mut repo,
            SaveHighlightTagsInput {
                highlight_id: highlight_id.clone(),
                tag_ids: vec![second.id.clone()],
            },
        )
        .unwrap();

        assert_eq!(reassigned.len(), 1);
        assert_eq!(reassigned[0].name, "Idea");
    }

    #[test]
    fn save_highlight_tags_returns_not_found_for_missing_highlight() {
        let mut repo = new_repository();
        let tag =
            create_tag(&repo, CreateTagInput { name: "Review".to_string(), color: None }).unwrap();

        let result = save_highlight_tags(
            &mut repo,
            SaveHighlightTagsInput {
                highlight_id: "missing-id".to_string(),
                tag_ids: vec![tag.id],
            },
        );

        assert!(matches!(result, Err(AppError::NotFound(_))));
    }
}
