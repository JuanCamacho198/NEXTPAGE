use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    AddDictionaryWordInput, DictionaryWordDto, ImportDictionaryError, ImportDictionaryResult,
    UpdateDictionaryEvidenceInput, UpdateDictionaryWordInput,
};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use unicode_normalization::UnicodeNormalization;
use uuid::Uuid;

/// Single source of truth for the selected column list. Its order MUST match
/// the field order read by [`read_full_row`].
const SELECT_COLUMNS: &str = "id, word, created_at, normalized_word, user_id, tags_json, srs_stage, updated_at, deleted_at, synced_at, definition, part_of_speech, phonetic, example, quote, source_book_id, source_book_title, source_book_author, source_chapter, source_locator";

/// Shared normalization contract (REQ-DSI-004): trim -> lowercase -> NFD
/// decompose -> strip combining marks U+0300-U+036F. TS (`normalizeDictionaryKey`)
/// and Kotlin (`DictionaryNormalizer.normalize`) implement the same rule, so the
/// natural key `(user_id, normalized_word)` is identical on every platform.
fn normalize_word(word: &str) -> String {
    word.trim().to_lowercase().nfd().filter(|c| !('\u{0300}'..='\u{036f}').contains(c)).collect()
}

fn has_column(conn: &rusqlite::Connection, table: &str, col: &str) -> bool {
    let mut stmt = match conn.prepare(&format!("PRAGMA table_info({})", table)) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let rows = stmt.query_map([], |row| row.get::<_, String>(1));
    if let Ok(rows) = rows {
        for name in rows.flatten() {
            if name == col {
                return true;
            }
        }
    }
    false
}

fn read_full_row(row: &rusqlite::Row) -> rusqlite::Result<DictionaryWordDto> {
    let id: String = row.get(0)?;
    let word: String = row.get(1)?;
    let created_at: String = row.get(2)?;
    let normalized_word: Option<String> = row.get(3).ok();
    let user_id: Option<String> = row.get(4).ok();
    let tags_json: Option<String> = row.get(5).ok();
    let srs_stage: Option<i64> = row.get(6).ok();
    let updated_at: Option<String> = row.get(7).ok();
    let deleted_at: Option<String> = row.get(8).ok();
    let synced_at: Option<String> = row.get(9).ok();
    let definition: Option<String> = row.get(10).ok();
    let part_of_speech: Option<String> = row.get(11).ok();
    let phonetic: Option<String> = row.get(12).ok();
    let example: Option<String> = row.get(13).ok();
    let quote: Option<String> = row.get(14).ok();
    let source_book_id: Option<String> = row.get(15).ok();
    let source_book_title: Option<String> = row.get(16).ok();
    let source_book_author: Option<String> = row.get(17).ok();
    let source_chapter: Option<String> = row.get(18).ok();
    let source_locator: Option<String> = row.get(19).ok();
    let tags: Option<Vec<String>> = tags_json.as_deref().and_then(|s| serde_json::from_str(s).ok());
    Ok(DictionaryWordDto {
        id: id.clone(),
        word,
        created_at: created_at.clone(),
        normalized_word,
        user_id,
        tags,
        srs_stage: srs_stage.map(|v| v as i32),
        updated_at: updated_at.or(Some(created_at)),
        deleted_at,
        synced_at,
        definition,
        part_of_speech,
        phonetic,
        example,
        quote,
        source_book_id,
        source_book_title,
        source_book_author,
        source_chapter,
        source_locator,
    })
}

fn col_exists(conn: &rusqlite::Connection, col: &str) -> bool {
    has_column(conn, "dictionary_words", col)
}

pub fn list_dictionary_words(repo: &LibraryRepository) -> AppResult<Vec<DictionaryWordDto>> {
    if col_exists(&repo.connection, "user_id") {
        let sql = format!(
            "SELECT {} FROM dictionary_words WHERE deleted_at IS NULL ORDER BY normalized_word ASC",
            SELECT_COLUMNS
        );
        let mut stmt = repo.connection.prepare(&sql)?;
        let rows = stmt.query_map([], read_full_row)?;
        Ok(rows.collect::<Result<Vec<_>, _>>()?)
    } else {
        let mut statement = repo.connection.prepare(
            "SELECT id, word, created_at FROM dictionary_words ORDER BY normalized_word ASC",
        )?;
        let rows = statement.query_map([], |row| {
            Ok(DictionaryWordDto {
                id: row.get(0)?,
                word: row.get(1)?,
                created_at: row.get(2)?,
                ..Default::default()
            })
        })?;
        Ok(rows.collect::<Result<Vec<_>, _>>()?)
    }
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
    let user_id = input.user_id.clone().unwrap_or_default();
    let tags_json = serde_json::to_string(&input.tags.unwrap_or_default()).unwrap();
    let srs_stage = input.srs_stage.unwrap_or(0).clamp(0, 5);
    let definition = input.definition.clone();
    let part_of_speech = input.part_of_speech.clone();
    let phonetic = input.phonetic.clone();
    let example = input.example.clone();
    let quote = input.quote.clone();
    let source_book_id = input.source_book_id.clone();
    let source_book_title = input.source_book_title.clone();
    let source_book_author = input.source_book_author.clone();
    let source_chapter = input.source_chapter.clone();
    let source_locator = input.source_locator.clone();
    if col_exists(&repo.connection, "user_id") {
        let sql = format!(
            "SELECT {} FROM dictionary_words WHERE user_id = ?1 AND normalized_word = ?2 AND deleted_at IS NULL LIMIT 1",
            SELECT_COLUMNS
        );
        let mut stmt = repo.connection.prepare(&sql)?;
        let existing = stmt.query_row(params![user_id, normalized], read_full_row).optional()?;
        if let Some(existing) = existing {
            return Err(AppError::DbConstraint(format!("dictionary.duplicate:{}", existing.word)));
        }
        let id = Uuid::new_v4().to_string();
        repo.connection.execute(
            "INSERT INTO dictionary_words (id, word, normalized_word, user_id, tags_json, srs_stage, created_at, updated_at, deleted_at, synced_at, definition, part_of_speech, phonetic, example, quote, source_book_id, source_book_title, source_book_author, source_chapter, source_locator) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, NULL, NULL, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)",
            params![id, word, normalized, user_id, tags_json, srs_stage, now, now, definition, part_of_speech, phonetic, example, quote, source_book_id, source_book_title, source_book_author, source_chapter, source_locator],
        )?;
        Ok(DictionaryWordDto {
            id,
            word: word.to_string(),
            created_at: now.clone(),
            normalized_word: Some(normalized),
            user_id: Some(user_id),
            tags: serde_json::from_str(&tags_json).ok(),
            srs_stage: Some(srs_stage),
            updated_at: Some(now.clone()),
            deleted_at: None,
            synced_at: None,
            definition,
            part_of_speech,
            phonetic,
            example,
            quote,
            source_book_id,
            source_book_title,
            source_book_author,
            source_chapter,
            source_locator,
        })
    } else {
        if let Some(existing) = find_word_by_normalized(repo, &normalized)? {
            return Ok(existing);
        }
        let id = Uuid::new_v4().to_string();
        repo.connection.execute(
            "INSERT INTO dictionary_words (id, word, normalized_word, created_at) VALUES (?1, ?2, ?3, ?4)",
            params![id, word, normalized, now],
        )?;
        Ok(DictionaryWordDto {
            id,
            word: word.to_string(),
            created_at: now.clone(),
            normalized_word: Some(normalized),
            user_id: Some(user_id),
            updated_at: Some(now),
            ..Default::default()
        })
    }
}

fn find_word_by_normalized(
    repo: &LibraryRepository,
    normalized: &str,
) -> AppResult<Option<DictionaryWordDto>> {
    if col_exists(&repo.connection, "user_id") {
        let sql = format!(
            "SELECT {} FROM dictionary_words WHERE normalized_word = ?1 AND deleted_at IS NULL LIMIT 1",
            SELECT_COLUMNS
        );
        let mut stmt = repo.connection.prepare(&sql)?;
        let result = stmt.query_row(params![normalized], read_full_row).optional()?;
        Ok(result)
    } else {
        let mut statement = repo.connection.prepare(
            "SELECT id, word, created_at FROM dictionary_words WHERE normalized_word = ?1",
        )?;
        let result = statement
            .query_row(params![normalized], |row| {
                Ok(DictionaryWordDto {
                    id: row.get(0)?,
                    word: row.get(1)?,
                    created_at: row.get(2)?,
                    ..Default::default()
                })
            })
            .optional()?;
        Ok(result)
    }
}

pub fn update_dictionary_word(
    repo: &LibraryRepository,
    input: UpdateDictionaryWordInput,
) -> AppResult<DictionaryWordDto> {
    let existing = if col_exists(&repo.connection, "user_id") {
        let sql = format!("SELECT {} FROM dictionary_words WHERE id = ?1 LIMIT 1", SELECT_COLUMNS);
        let mut stmt = repo.connection.prepare(&sql)?;
        stmt.query_row(params![input.id], read_full_row).optional()?
    } else {
        let mut stmt = repo
            .connection
            .prepare("SELECT id, word, created_at FROM dictionary_words WHERE id = ?1")?;
        stmt.query_row(params![input.id], |row| {
            Ok(DictionaryWordDto {
                id: row.get(0)?,
                word: row.get(1)?,
                created_at: row.get(2)?,
                ..Default::default()
            })
        })
        .optional()?
    };
    let existing = existing
        .ok_or_else(|| AppError::NotFound(format!("Dictionary word {} not found", input.id)))?;
    if !col_exists(&repo.connection, "user_id") {
        if let Some(new_word) = input.word.as_deref() {
            let trimmed = new_word.trim();
            if trimmed.is_empty() {
                return Err(AppError::InvalidInput("Word is required".to_string()));
            }
            if trimmed.len() > 200 {
                return Err(AppError::InvalidInput(
                    "Word must be 200 characters or less".to_string(),
                ));
            }
            let normalized = normalize_word(trimmed);
            repo.connection.execute(
                "UPDATE dictionary_words SET word = ?1, normalized_word = ?2 WHERE id = ?3",
                params![trimmed, normalized, input.id],
            )?;
            return Ok(DictionaryWordDto {
                id: existing.id,
                word: trimmed.to_string(),
                created_at: existing.created_at,
                normalized_word: Some(normalized),
                ..Default::default()
            });
        }
        return Ok(existing);
    }
    let new_word = input.word.as_deref().unwrap_or(&existing.word);
    let trimmed = new_word.trim();
    if trimmed.is_empty() {
        return Err(AppError::InvalidInput("Word is required".to_string()));
    }
    if trimmed.len() > 200 {
        return Err(AppError::InvalidInput("Word must be 200 characters or less".to_string()));
    }
    let normalized = normalize_word(trimmed);
    let user_id = existing.user_id.clone().unwrap_or_default();
    let mut dup = repo.connection.prepare(
        "SELECT id FROM dictionary_words WHERE user_id = ?1 AND normalized_word = ?2 AND id != ?3 AND deleted_at IS NULL LIMIT 1",
    )?;
    if dup
        .query_row(params![user_id, normalized, input.id], |r| r.get::<_, String>(0))
        .optional()?
        .is_some()
    {
        return Err(AppError::DbConstraint("dictionary.duplicate".to_string()));
    }
    let tags = input.tags.clone().or(existing.tags.clone()).unwrap_or_default();
    let tags_json = serde_json::to_string(&tags).unwrap();
    let srs_stage = input.srs_stage.or(existing.srs_stage).unwrap_or(0).clamp(0, 5);
    let definition = input.definition.clone().or(existing.definition.clone());
    let part_of_speech = input.part_of_speech.clone().or(existing.part_of_speech.clone());
    let phonetic = input.phonetic.clone().or(existing.phonetic.clone());
    let example = input.example.clone().or(existing.example.clone());
    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "UPDATE dictionary_words SET word = ?1, normalized_word = ?2, tags_json = ?3, srs_stage = ?4, updated_at = ?5, definition = ?6, part_of_speech = ?7, phonetic = ?8, example = ?9 WHERE id = ?10",
        params![trimmed, normalized, tags_json, srs_stage, now, definition, part_of_speech, phonetic, example, input.id],
    )?;
    Ok(DictionaryWordDto {
        id: existing.id.clone(),
        word: trimmed.to_string(),
        created_at: existing.created_at,
        normalized_word: Some(normalized),
        user_id: Some(user_id),
        tags: Some(tags),
        srs_stage: Some(srs_stage),
        updated_at: Some(now),
        deleted_at: None,
        synced_at: None,
        definition,
        part_of_speech,
        phonetic,
        example,
        quote: existing.quote.clone(),
        source_book_id: existing.source_book_id.clone(),
        source_book_title: existing.source_book_title.clone(),
        source_book_author: existing.source_book_author.clone(),
        source_chapter: existing.source_chapter.clone(),
        source_locator: existing.source_locator.clone(),
    })
}

pub fn update_dictionary_evidence(
    repo: &LibraryRepository,
    input: UpdateDictionaryEvidenceInput,
) -> AppResult<DictionaryWordDto> {
    repo.connection.execute(
        "UPDATE dictionary_words SET quote = ?1, source_book_id = ?2, source_book_title = ?3, source_book_author = ?4, source_chapter = ?5, source_locator = ?6 WHERE id = ?7",
        params![
            input.quote,
            input.source_book_id,
            input.source_book_title,
            input.source_book_author,
            input.source_chapter,
            input.source_locator,
            input.id
        ],
    )?;
    let sql = format!("SELECT {} FROM dictionary_words WHERE id = ?1 LIMIT 1", SELECT_COLUMNS);
    let mut stmt = repo.connection.prepare(&sql)?;
    let updated = stmt.query_row(params![input.id], read_full_row).optional()?;
    updated.ok_or_else(|| AppError::NotFound(format!("Dictionary word {} not found", input.id)))
}

pub fn remove_dictionary_word(repo: &LibraryRepository, id: &str) -> AppResult<()> {
    if col_exists(&repo.connection, "deleted_at") {
        let now = Utc::now().to_rfc3339();
        let rows = repo.connection.execute(
            "UPDATE dictionary_words SET deleted_at = ?1, updated_at = ?1 WHERE id = ?2 AND deleted_at IS NULL",
            params![now, id],
        )?;
        if rows == 0 {
            let rows2 = repo
                .connection
                .execute("DELETE FROM dictionary_words WHERE id = ?1", params![id])?;
            if rows2 == 0 {
                return Err(AppError::NotFound(format!("Dictionary word {} not found", id)));
            }
        }
        Ok(())
    } else {
        let rows =
            repo.connection.execute("DELETE FROM dictionary_words WHERE id = ?1", params![id])?;
        if rows == 0 {
            return Err(AppError::NotFound(format!("Dictionary word {} not found", id)));
        }
        Ok(())
    }
}

pub fn search_dictionary_words(
    repo: &LibraryRepository,
    query: &str,
    limit: i64,
    fuzzy: bool,
    user_id: Option<&str>,
) -> AppResult<Vec<DictionaryWordDto>> {
    let q = normalize_word(query);
    if q.is_empty() {
        return Ok(vec![]);
    }
    let limit = limit.clamp(1, 100);
    if !col_exists(&repo.connection, "user_id") {
        let mut stmt = repo.connection.prepare(
            "SELECT id, word, created_at FROM dictionary_words WHERE normalized_word LIKE ?1 ORDER BY normalized_word ASC LIMIT ?2",
        )?;
        let pattern = format!("{}%", q);
        let rows = stmt.query_map(params![pattern, limit], |row| {
            Ok(DictionaryWordDto {
                id: row.get(0)?,
                word: row.get(1)?,
                created_at: row.get(2)?,
                ..Default::default()
            })
        })?;
        return Ok(rows.collect::<Result<Vec<_>, _>>()?);
    }
    let uid = user_id.unwrap_or("");
    let pattern_prefix = format!("{}%", q);
    let pattern_sub = format!("%{}%", q);
    let sql = format!(
        "SELECT {} FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') AND (normalized_word LIKE ?2 OR normalized_word LIKE ?3) ORDER BY updated_at DESC LIMIT 200",
        SELECT_COLUMNS
    );
    let mut stmt = repo.connection.prepare(&sql)?;
    let mut candidates: Vec<DictionaryWordDto> = stmt
        .query_map(params![uid, pattern_prefix, pattern_sub], read_full_row)?
        .collect::<Result<Vec<_>, _>>()?;
    if candidates.is_empty() && fuzzy {
        let sql = format!(
            "SELECT {} FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') LIMIT 500",
            SELECT_COLUMNS
        );
        let mut stmt2 = repo.connection.prepare(&sql)?;
        let all: Vec<DictionaryWordDto> =
            stmt2.query_map(params![uid], read_full_row)?.collect::<Result<Vec<_>, _>>()?;
        candidates = all
            .into_iter()
            .filter(|d| {
                if let Some(n) = d.normalized_word.as_deref() {
                    levenshtein(n, &q) <= 2
                } else {
                    false
                }
            })
            .collect();
    } else if fuzzy {
        let sql = format!(
            "SELECT {} FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') LIMIT 500",
            SELECT_COLUMNS
        );
        let mut stmt2 = repo.connection.prepare(&sql)?;
        let all: Vec<DictionaryWordDto> =
            stmt2.query_map(params![uid], read_full_row)?.collect::<Result<Vec<_>, _>>()?;
        for d in all {
            if candidates.iter().any(|c| c.id == d.id) {
                continue;
            }
            if let Some(n) = d.normalized_word.as_deref() {
                if levenshtein(n, &q) <= 2 {
                    candidates.push(d);
                }
            }
        }
    }
    candidates.sort_by(|a, b| {
        let ra = rank_for(a, &q);
        let rb = rank_for(b, &q);
        ra.cmp(&rb).then_with(|| b.updated_at.cmp(&a.updated_at))
    });
    candidates.truncate(limit as usize);
    Ok(candidates)
}

fn rank_for(dto: &DictionaryWordDto, q: &str) -> u8 {
    if let Some(n) = dto.normalized_word.as_deref() {
        if n == q {
            return 0;
        }
        if n.starts_with(q) {
            return 1;
        }
        if n.contains(q) {
            return 2;
        }
        if levenshtein(n, q) <= 2 {
            return 3;
        }
    }
    4
}

fn levenshtein(a: &str, b: &str) -> usize {
    let a = a.as_bytes();
    let b = b.as_bytes();
    if a.is_empty() {
        return b.len();
    }
    if b.is_empty() {
        return a.len();
    }
    let mut prev: Vec<usize> = (0..=b.len()).collect();
    let mut cur = vec![0; b.len() + 1];
    for (i, ca) in a.iter().enumerate() {
        cur[0] = i + 1;
        for (j, cb) in b.iter().enumerate() {
            let cost = if ca == cb { 0 } else { 1 };
            cur[j + 1] = (prev[j + 1] + 1).min(cur[j] + 1).min(prev[j] + cost);
        }
        std::mem::swap(&mut prev, &mut cur);
    }
    prev[b.len()]
}

pub fn export_dictionary(repo: &LibraryRepository, format: &str) -> AppResult<String> {
    let words = list_dictionary_words(repo)?;
    if format == "csv" {
        let mut out = String::from("word,tags,srs_stage,updated_at\n");
        for w in words {
            let tags = w.tags.unwrap_or_default().join("|");
            let srs = w.srs_stage.unwrap_or(0).to_string();
            let updated = w.updated_at.unwrap_or(w.created_at);
            let word_esc = w.word.replace('"', "\"\"");
            out.push_str(&format!("\"{}\",\"{}\",{},{}\n", word_esc, tags, srs, updated));
        }
        Ok(out)
    } else {
        let json_words: Vec<serde_json::Value> = words
            .into_iter()
            .map(|w| {
                serde_json::json!({
                    "word": w.word,
                    "tags": w.tags.unwrap_or_default(),
                    "srs_stage": w.srs_stage.unwrap_or(0),
                    "updated_at": w.updated_at.unwrap_or(w.created_at)
                })
            })
            .collect();
        Ok(serde_json::to_string(&serde_json::json!({ "words": json_words })).unwrap())
    }
}

pub fn import_dictionary(
    repo: &LibraryRepository,
    payload: &str,
    format: &str,
    user_id: Option<&str>,
) -> AppResult<ImportDictionaryResult> {
    let uid = user_id.unwrap_or("");
    let mut imported: i64 = 0;
    let mut errors: Vec<ImportDictionaryError> = vec![];
    let entries: Vec<(String, Vec<String>, i32, String)> = if format == "csv" {
        let mut v = vec![];
        // Resolve column positions from the header rather than assuming an index.
        // A file exported before `is_favorite` was dropped carries it between
        // `tags` and `srs_stage`; reading by name keeps `srs_stage`/`updated_at`
        // in the right slots for both layouts.
        let header: Vec<String> = payload
            .lines()
            .next()
            .map(|line| {
                split_csv_line(line)
                    .iter()
                    .map(|cell| cell.trim().trim_matches('"').to_string())
                    .collect()
            })
            .unwrap_or_default();
        let named_header = header.first().map(|cell| cell == "word").unwrap_or(false);
        let column = |name: &str, fallback: usize| -> usize {
            if named_header {
                header.iter().position(|cell| cell == name).unwrap_or(fallback)
            } else {
                fallback
            }
        };
        let tags_col = column("tags", 1);
        let srs_col = column("srs_stage", 2);
        let updated_col = column("updated_at", 3);
        for (idx, line) in payload.lines().enumerate() {
            if idx == 0 && named_header {
                continue;
            }
            if line.trim().is_empty() {
                continue;
            }
            let parts: Vec<String> = split_csv_line(line);
            if parts.is_empty() {
                errors.push(ImportDictionaryError {
                    row: idx as i64 + 1,
                    reason: "empty row".to_string(),
                });
                continue;
            }
            let word =
                parts.first().cloned().unwrap_or_default().trim().trim_matches('"').to_string();
            let tags_str = parts
                .get(tags_col)
                .cloned()
                .unwrap_or_default()
                .trim()
                .trim_matches('"')
                .to_string();
            let tags = if tags_str.is_empty() {
                vec![]
            } else {
                tags_str.split('|').map(|s| s.to_string()).collect()
            };
            let srs = parts
                .get(srs_col)
                .and_then(|s| s.trim().trim_matches('"').parse::<i32>().ok())
                .unwrap_or(0);
            let updated = parts
                .get(updated_col)
                .cloned()
                .unwrap_or_else(|| Utc::now().to_rfc3339())
                .trim()
                .trim_matches('"')
                .to_string();
            v.push((word, tags, srs, updated));
        }
        v
    } else {
        let val: serde_json::Value =
            serde_json::from_str(payload).map_err(|e| AppError::InvalidInput(e.to_string()))?;
        let arr = val.get("words").and_then(|v| v.as_array()).cloned().unwrap_or_default();
        let mut v = vec![];
        for item in arr {
            let word = item.get("word").and_then(|x| x.as_str()).unwrap_or("").to_string();
            let tags = item
                .get("tags")
                .and_then(|x| x.as_array())
                .map(|a| a.iter().filter_map(|e| e.as_str().map(|s| s.to_string())).collect())
                .unwrap_or_default();
            let srs = item.get("srs_stage").and_then(|x| x.as_i64()).unwrap_or(0) as i32;
            let updated = item
                .get("updated_at")
                .and_then(|x| x.as_str())
                .unwrap_or(&Utc::now().to_rfc3339())
                .to_string();
            v.push((word, tags, srs, updated));
        }
        v
    };
    for (idx, (word, tags, srs, updated_at)) in entries.into_iter().enumerate() {
        let trimmed = word.trim();
        if trimmed.is_empty() || trimmed.len() > 200 {
            errors.push(ImportDictionaryError {
                row: idx as i64 + 1,
                reason: "invalid word len".to_string(),
            });
            continue;
        }
        let normalized = normalize_word(trimmed);
        let existing: Option<DictionaryWordDto> = if col_exists(&repo.connection, "user_id") {
            let sql = format!(
                "SELECT {} FROM dictionary_words WHERE user_id = ?1 AND normalized_word = ?2 LIMIT 1",
                SELECT_COLUMNS
            );
            let mut stmt = repo.connection.prepare(&sql).unwrap();
            stmt.query_row(params![uid, normalized], read_full_row).optional().unwrap()
        } else {
            None
        };
        if let Some(ex) = existing {
            let ex_updated = ex.updated_at.unwrap_or(ex.created_at);
            if ex_updated > updated_at {
                continue;
            }
            if ex_updated == updated_at {
                continue;
            }
            let tags_json = serde_json::to_string(&tags).unwrap();
            let _ = repo.connection.execute(
                "UPDATE dictionary_words SET word = ?1, tags_json = ?2, srs_stage = ?3, updated_at = ?4 WHERE id = ?5",
                params![trimmed, tags_json, srs.clamp(0, 5), updated_at, ex.id],
            );
            imported += 1;
        } else {
            let id = Uuid::new_v4().to_string();
            let tags_json = serde_json::to_string(&tags).unwrap();
            let now_created = updated_at.clone();
            if col_exists(&repo.connection, "user_id") {
                let _ = repo.connection.execute(
                    "INSERT INTO dictionary_words (id, word, normalized_word, user_id, tags_json, srs_stage, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
                    params![id, trimmed, normalized, uid, tags_json, srs.clamp(0, 5), now_created, updated_at],
                );
            } else {
                let _ = repo.connection.execute(
                    "INSERT INTO dictionary_words (id, word, normalized_word, created_at) VALUES (?1, ?2, ?3, ?4)",
                    params![id, trimmed, normalized, now_created],
                );
            }
            imported += 1;
        }
    }
    Ok(ImportDictionaryResult { imported, errors })
}

fn split_csv_line(line: &str) -> Vec<String> {
    let mut out = vec![];
    let mut cur = String::new();
    let mut in_quotes = false;
    let mut chars = line.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '"' {
            if in_quotes && chars.peek() == Some(&'"') {
                cur.push('"');
                chars.next();
            } else {
                in_quotes = !in_quotes;
            }
        } else if c == ',' && !in_quotes {
            out.push(cur.clone());
            cur.clear();
        } else {
            cur.push(c);
        }
    }
    out.push(cur);
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::tests::new_repository;

    #[test]
    fn add_and_list_dictionary_words() {
        let repo = new_repository();
        let first = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Serendipity".to_string(),
                user_id: None,
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(first.word, "Serendipity");
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
        let word = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Ephemeral".to_string(),
                user_id: None,
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        remove_dictionary_word(&repo, &word.id).unwrap();
        let words = list_dictionary_words(&repo).unwrap();
        assert!(words.is_empty());
    }

    #[test]
    fn duplicate_per_user_returns_error() {
        let repo = new_repository();
        add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Hola".to_string(),
                user_id: Some("u1".to_string()),
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        let dup = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "hola".to_string(),
                user_id: Some("u1".to_string()),
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        );
        assert!(dup.is_err());
        let ok = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "hola".to_string(),
                user_id: Some("u2".to_string()),
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(ok.word, "hola");
    }

    #[test]
    fn update_preserves_id_and_bumps_updated_at() {
        let repo = new_repository();
        let w = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "hola".to_string(),
                user_id: None,
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        let updated = update_dictionary_word(
            &repo,
            UpdateDictionaryWordInput {
                id: w.id.clone(),
                word: Some("Hola!".to_string()),
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(updated.id, w.id);
        assert_eq!(updated.word, "Hola!");
        assert!(updated.updated_at.unwrap() >= w.updated_at.unwrap());
    }

    #[test]
    fn search_prefix_and_fuzzy() {
        let repo = new_repository();
        add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "biblioteca".to_string(),
                user_id: None,
                tags: None,
                srs_stage: None,
                ..Default::default()
            },
        )
        .unwrap();
        let res = search_dictionary_words(&repo, "bibli", 10, false, None).unwrap();
        assert_eq!(res.len(), 1);
        let fuzzy = search_dictionary_words(&repo, "bibilioteca", 10, true, None).unwrap();
        assert_eq!(fuzzy.len(), 1);
    }

    #[test]
    fn import_csv_partial_success() {
        let repo = new_repository();
        let csv = "word,tags,srs_stage,updated_at\n\"hola\",\"\",0,2026-08-25T10:00:00Z\n\"\",\"\",0,2026-08-25T10:00:00Z\n";
        let r = import_dictionary(&repo, csv, "csv", None).unwrap();
        assert_eq!(r.imported, 1);
        assert_eq!(r.errors.len(), 1);
    }

    /// A file exported before `is_favorite` was dropped still imports. The
    /// legacy column sits between `tags` and `srs_stage`, so a positional
    /// reader would land `srs_stage` in the timestamp slot; the header lookup
    /// keeps both fields where they belong.
    #[test]
    fn import_legacy_csv_with_favorite_column_keeps_srs_and_updated_at() {
        let repo = new_repository();
        let csv = "word,tags,is_favorite,srs_stage,updated_at\n\"hola\",\"greeting\",true,4,2026-08-25T10:00:00Z\n";
        let r = import_dictionary(&repo, csv, "csv", Some("u1")).unwrap();
        assert_eq!(r.imported, 1);
        let words = list_dictionary_words(&repo).unwrap();
        assert_eq!(words.len(), 1);
        assert_eq!(words[0].srs_stage, Some(4));
        assert_eq!(words[0].updated_at.as_deref(), Some("2026-08-25T10:00:00Z"));
        assert_eq!(words[0].tags, Some(vec!["greeting".to_string()]));
    }

    #[test]
    fn export_omits_the_favorite_column_from_csv_and_json() {
        let repo = new_repository();
        add_dictionary_word(
            &repo,
            AddDictionaryWordInput { word: "hola".to_string(), ..Default::default() },
        )
        .unwrap();
        let csv = export_dictionary(&repo, "csv").unwrap();
        assert!(csv.starts_with("word,tags,srs_stage,updated_at\n"), "csv: {csv}");
        assert!(!csv.contains("is_favorite"), "csv: {csv}");
        let json = export_dictionary(&repo, "json").unwrap();
        assert!(!json.contains("is_favorite"), "json: {json}");
    }

    #[test]
    fn create_with_only_word_leaves_new_fields_null() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput { word: "Solo".to_string(), ..Default::default() },
        )
        .unwrap();
        assert!(created.definition.is_none());
        assert!(created.part_of_speech.is_none());
        assert!(created.phonetic.is_none());
        assert!(created.example.is_none());
        assert!(created.quote.is_none());
        assert!(created.source_book_id.is_none());
        assert!(created.source_book_title.is_none());
        assert!(created.source_book_author.is_none());
        assert!(created.source_chapter.is_none());
        assert!(created.source_locator.is_none());
        let listed = list_dictionary_words(&repo).unwrap();
        assert_eq!(listed.len(), 1);
        let read = &listed[0];
        assert!(read.definition.is_none() && read.part_of_speech.is_none());
        assert!(read.phonetic.is_none() && read.example.is_none());
        assert!(read.quote.is_none() && read.source_book_id.is_none());
        assert!(read.source_book_title.is_none() && read.source_book_author.is_none());
        assert!(read.source_chapter.is_none() && read.source_locator.is_none());
    }

    #[test]
    fn user_field_round_trip_persists_fields() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Efímero".to_string(),
                definition: Some("Que dura poco tiempo".to_string()),
                part_of_speech: Some("adjetivo".to_string()),
                phonetic: Some("/eˈfimeɾo/".to_string()),
                example: Some("Un amor efímero".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(created.definition.as_deref(), Some("Que dura poco tiempo"));
        assert_eq!(created.part_of_speech.as_deref(), Some("adjetivo"));
        assert_eq!(created.phonetic.as_deref(), Some("/eˈfimeɾo/"));
        assert_eq!(created.example.as_deref(), Some("Un amor efímero"));
        let listed = list_dictionary_words(&repo).unwrap();
        let read = listed.iter().find(|w| w.id == created.id).unwrap();
        assert_eq!(read.definition.as_deref(), Some("Que dura poco tiempo"));
        assert_eq!(read.part_of_speech.as_deref(), Some("adjetivo"));
        assert_eq!(read.phonetic.as_deref(), Some("/eˈfimeɾo/"));
        assert_eq!(read.example.as_deref(), Some("Un amor efímero"));
    }

    #[test]
    fn update_user_fields_cannot_alter_evidence() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Evidencia".to_string(),
                definition: Some("original".to_string()),
                quote: Some("cita original".to_string()),
                source_book_id: Some("book-1".to_string()),
                source_book_title: Some("Titulo".to_string()),
                source_book_author: Some("Autor".to_string()),
                source_chapter: Some("Cap 1".to_string()),
                source_locator: Some("epubcfi(/6/4!/4/2)".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        let updated = update_dictionary_word(
            &repo,
            UpdateDictionaryWordInput {
                id: created.id.clone(),
                definition: Some("nueva".to_string()),
                part_of_speech: Some("sustantivo".to_string()),
                phonetic: Some("/e/".to_string()),
                example: Some("ejemplo".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(updated.definition.as_deref(), Some("nueva"));
        assert_eq!(updated.part_of_speech.as_deref(), Some("sustantivo"));
        assert_eq!(updated.phonetic.as_deref(), Some("/e/"));
        assert_eq!(updated.example.as_deref(), Some("ejemplo"));
        assert_eq!(updated.quote.as_deref(), Some("cita original"));
        assert_eq!(updated.source_book_id.as_deref(), Some("book-1"));
        assert_eq!(updated.source_book_title.as_deref(), Some("Titulo"));
        assert_eq!(updated.source_book_author.as_deref(), Some("Autor"));
        assert_eq!(updated.source_chapter.as_deref(), Some("Cap 1"));
        assert_eq!(updated.source_locator.as_deref(), Some("epubcfi(/6/4!/4/2)"));
        let listed = list_dictionary_words(&repo).unwrap();
        let read = listed.iter().find(|w| w.id == created.id).unwrap();
        assert_eq!(read.quote.as_deref(), Some("cita original"));
        assert_eq!(read.source_book_id.as_deref(), Some("book-1"));
        assert_eq!(read.source_book_title.as_deref(), Some("Titulo"));
        assert_eq!(read.source_book_author.as_deref(), Some("Autor"));
        assert_eq!(read.source_chapter.as_deref(), Some("Cap 1"));
        assert_eq!(read.source_locator.as_deref(), Some("epubcfi(/6/4!/4/2)"));
    }

    #[test]
    fn recapture_overwrites_all_evidence_columns() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Recaptura".to_string(),
                quote: Some("cita A".to_string()),
                source_book_id: Some("book-a".to_string()),
                source_book_title: Some("Libro A".to_string()),
                source_book_author: Some("Autor A".to_string()),
                source_chapter: Some("Cap 1".to_string()),
                source_locator: Some("epubcfi(/6/4!/4/2)".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        let recaptured = update_dictionary_evidence(
            &repo,
            UpdateDictionaryEvidenceInput {
                id: created.id.clone(),
                quote: Some("cita B".to_string()),
                source_book_id: Some("book-b".to_string()),
                source_book_title: Some("Libro B".to_string()),
                source_book_author: Some("Autor B".to_string()),
                source_chapter: Some("Cap 9".to_string()),
                source_locator: Some("epubcfi(/6/8!/4/6)".to_string()),
            },
        )
        .unwrap();
        assert_eq!(recaptured.quote.as_deref(), Some("cita B"));
        assert_eq!(recaptured.source_book_id.as_deref(), Some("book-b"));
        assert_eq!(recaptured.source_book_title.as_deref(), Some("Libro B"));
        assert_eq!(recaptured.source_book_author.as_deref(), Some("Autor B"));
        assert_eq!(recaptured.source_chapter.as_deref(), Some("Cap 9"));
        assert_eq!(recaptured.source_locator.as_deref(), Some("epubcfi(/6/8!/4/6)"));
        let listed = list_dictionary_words(&repo).unwrap();
        let read = listed.iter().find(|w| w.id == created.id).unwrap();
        assert_eq!(read.quote.as_deref(), Some("cita B"));
        assert_eq!(read.source_book_id.as_deref(), Some("book-b"));
        assert_eq!(read.source_book_title.as_deref(), Some("Libro B"));
        assert_eq!(read.source_book_author.as_deref(), Some("Autor B"));
        assert_eq!(read.source_chapter.as_deref(), Some("Cap 9"));
        assert_eq!(read.source_locator.as_deref(), Some("epubcfi(/6/8!/4/6)"));
    }

    #[test]
    fn recapture_leaves_user_fields_unchanged() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Efímero".to_string(),
                definition: Some("Que dura poco tiempo".to_string()),
                part_of_speech: Some("adjetivo".to_string()),
                phonetic: Some("/eˈfimeɾo/".to_string()),
                example: Some("Un amor efímero".to_string()),
                quote: Some("cita A".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        let recaptured = update_dictionary_evidence(
            &repo,
            UpdateDictionaryEvidenceInput {
                id: created.id.clone(),
                quote: Some("cita B".to_string()),
                source_book_id: Some("book-b".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(recaptured.word, created.word);
        assert_eq!(recaptured.definition.as_deref(), created.definition.as_deref());
        assert_eq!(recaptured.part_of_speech.as_deref(), created.part_of_speech.as_deref());
        assert_eq!(recaptured.phonetic.as_deref(), created.phonetic.as_deref());
        assert_eq!(recaptured.example.as_deref(), created.example.as_deref());
        let listed = list_dictionary_words(&repo).unwrap();
        let read = listed.iter().find(|w| w.id == created.id).unwrap();
        assert_eq!(read.definition.as_deref(), Some("Que dura poco tiempo"));
        assert_eq!(read.part_of_speech.as_deref(), Some("adjetivo"));
        assert_eq!(read.phonetic.as_deref(), Some("/eˈfimeɾo/"));
        assert_eq!(read.example.as_deref(), Some("Un amor efímero"));
    }

    #[test]
    fn recapture_accepts_null_quote() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "SinCita".to_string(),
                quote: Some("cita A".to_string()),
                source_book_id: Some("book-a".to_string()),
                source_book_title: Some("Libro A".to_string()),
                source_book_author: Some("Autor A".to_string()),
                source_chapter: Some("Cap 1".to_string()),
                source_locator: Some("epubcfi(/6/4!/4/2)".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        let cleared = update_dictionary_evidence(
            &repo,
            UpdateDictionaryEvidenceInput {
                id: created.id.clone(),
                quote: None,
                source_book_id: None,
                source_book_title: None,
                source_book_author: None,
                source_chapter: None,
                source_locator: None,
            },
        )
        .unwrap();
        assert!(cleared.quote.is_none());
        assert!(cleared.source_book_id.is_none());
        assert!(cleared.source_book_title.is_none());
        assert!(cleared.source_book_author.is_none());
        assert!(cleared.source_chapter.is_none());
        assert!(cleared.source_locator.is_none());
        let listed = list_dictionary_words(&repo).unwrap();
        let read = listed.iter().find(|w| w.id == created.id).unwrap();
        assert!(read.quote.is_none() && read.source_book_id.is_none());
        assert!(read.source_book_title.is_none() && read.source_book_author.is_none());
        assert!(read.source_chapter.is_none() && read.source_locator.is_none());
    }

    #[test]
    fn recapture_requires_existing_id() {
        let repo = new_repository();
        let result = update_dictionary_evidence(
            &repo,
            UpdateDictionaryEvidenceInput {
                id: "missing-id".to_string(),
                quote: Some("cita".to_string()),
                ..Default::default()
            },
        );
        assert!(matches!(result, Err(AppError::NotFound(_))));
    }

    /// Conformance vectors shared verbatim with the TypeScript test (task 2A.3) and
    /// the Kotlin test (task 5B.1): same inputs, same expected keys (REQ-DSI-004).
    ///
    /// Punctuation stays in the key: the contract strips only combining marks, so a
    /// surrounding comma survives normalization. Stripping punctuation belongs to
    /// `tokenizeSelection` (slice 2A), not to the normalizer.
    const NORMALIZATION_VECTORS: &[(&str, &str)] = &[
        ("", ""),
        ("   ", ""),
        ("  Serendipity  ", "serendipity"),
        ("café", "cafe"),
        ("CAFÉ", "cafe"),
        ("  Café  ", "cafe"),
        ("Ñandú", "nandu"),
        ("Ōkami", "okami"),
        ("Řeka", "reka"),
        ("Île", "ile"),
        ("Āris", "aris"),
        ("Ștefan", "stefan"),
        ("İstanbul", "istanbul"),
        ("Abyss,", "abyss,"),
        ("(Ephemeral)", "(ephemeral)"),
        ("¡Hola!", "¡hola!"),
        ("Ephemeral's", "ephemeral's"),
    ];

    #[test]
    fn normalization_vectors_match_shared_contract() {
        for (input, expected) in NORMALIZATION_VECTORS {
            assert_eq!(normalize_word(input), *expected, "input {:?}", input);
        }
    }

    #[test]
    fn normalization_backs_the_per_user_natural_key() {
        let repo = new_repository();
        let created = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "Tōkyō".to_string(),
                user_id: Some("u1".to_string()),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(created.normalized_word.as_deref(), Some("tokyo"));
        assert_eq!(created.word, "Tōkyō");
        let dup = add_dictionary_word(
            &repo,
            AddDictionaryWordInput {
                word: "TOKYO".to_string(),
                user_id: Some("u1".to_string()),
                ..Default::default()
            },
        );
        assert!(matches!(dup, Err(AppError::DbConstraint(_))));
        let listed = list_dictionary_words(&repo).unwrap();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].word, "Tōkyō");
    }
}
