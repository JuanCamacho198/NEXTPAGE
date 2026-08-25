use super::LibraryRepository;
use crate::error::{AppError, AppResult};
use crate::models::{
    AddDictionaryWordInput, DictionaryWordDto, ImportDictionaryError, ImportDictionaryResult,
    UpdateDictionaryWordInput,
};
use chrono::Utc;
use rusqlite::{params, OptionalExtension};
use uuid::Uuid;

fn normalize_word(word: &str) -> String {
    let trimmed = word.trim().to_lowercase();
    strip_accents(&trimmed)
}

fn strip_accents(input: &str) -> String {
    input
        .chars()
        .map(|c| match c {
            'á' | 'à' | 'ä' | 'â' => 'a',
            'é' | 'è' | 'ë' | 'ê' => 'e',
            'í' | 'ì' | 'ï' | 'î' => 'i',
            'ó' | 'ò' | 'ö' | 'ô' => 'o',
            'ú' | 'ù' | 'ü' | 'û' => 'u',
            'ñ' => 'n',
            'ç' => 'c',
            _ => c,
        })
        .collect()
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
    let is_favorite: Option<i64> = row.get(6).ok();
    let srs_stage: Option<i64> = row.get(7).ok();
    let updated_at: Option<String> = row.get(8).ok();
    let deleted_at: Option<String> = row.get(9).ok();
    let synced_at: Option<String> = row.get(10).ok();
    let tags: Option<Vec<String>> = tags_json.as_deref().and_then(|s| serde_json::from_str(s).ok());
    Ok(DictionaryWordDto {
        id: id.clone(),
        word,
        created_at: created_at.clone(),
        normalized_word,
        user_id,
        tags,
        is_favorite: is_favorite.map(|v| v != 0),
        srs_stage: srs_stage.map(|v| v as i32),
        updated_at: updated_at.or(Some(created_at)),
        deleted_at,
        synced_at,
    })
}

fn col_exists(conn: &rusqlite::Connection, col: &str) -> bool {
    has_column(conn, "dictionary_words", col)
}

pub fn list_dictionary_words(repo: &LibraryRepository) -> AppResult<Vec<DictionaryWordDto>> {
    if col_exists(&repo.connection, "user_id") {
        let mut stmt = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE deleted_at IS NULL ORDER BY normalized_word ASC",
        )?;
        let rows = stmt.query_map([], |row| read_full_row(row))?;
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
                normalized_word: None,
                user_id: None,
                tags: None,
                is_favorite: None,
                srs_stage: None,
                updated_at: None,
                deleted_at: None,
                synced_at: None,
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
    let user_id = input.user_id.unwrap_or_default();
    let tags_json = serde_json::to_string(&input.tags.unwrap_or_default()).unwrap();
    let is_favorite = if input.is_favorite.unwrap_or(false) { 1 } else { 0 };
    let srs_stage = input.srs_stage.unwrap_or(0).clamp(0, 5);
    if col_exists(&repo.connection, "user_id") {
        let mut stmt = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE user_id = ?1 AND normalized_word = ?2 AND deleted_at IS NULL LIMIT 1",
        )?;
        let existing = stmt
            .query_row(params![user_id, normalized], |row| read_full_row(row))
            .optional()?;
        if let Some(existing) = existing {
            return Err(AppError::DbConstraint(format!(
                "dictionary.duplicate:{}",
                existing.word
            )));
        }
        let id = Uuid::new_v4().to_string();
        repo.connection.execute(
            "INSERT INTO dictionary_words (id, word, normalized_word, user_id, tags_json, is_favorite, srs_stage, created_at, updated_at, deleted_at, synced_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, NULL, NULL)",
            params![id, word, normalized, user_id, tags_json, is_favorite, srs_stage, now, now],
        )?;
        Ok(DictionaryWordDto {
            id,
            word: word.to_string(),
            created_at: now.clone(),
            normalized_word: Some(normalized),
            user_id: Some(user_id),
            tags: serde_json::from_str(&tags_json).ok(),
            is_favorite: Some(is_favorite != 0),
            srs_stage: Some(srs_stage),
            updated_at: Some(now.clone()),
            deleted_at: None,
            synced_at: None,
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
            tags: None,
            is_favorite: None,
            srs_stage: None,
            updated_at: Some(now),
            deleted_at: None,
            synced_at: None,
        })
    }
}

fn find_word_by_normalized(
    repo: &LibraryRepository,
    normalized: &str,
) -> AppResult<Option<DictionaryWordDto>> {
    if col_exists(&repo.connection, "user_id") {
        let mut stmt = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE normalized_word = ?1 AND deleted_at IS NULL LIMIT 1",
        )?;
        let result = stmt
            .query_row(params![normalized], |row| read_full_row(row))
            .optional()?;
        Ok(result)
    } else {
        let mut statement = repo
            .connection
            .prepare("SELECT id, word, created_at FROM dictionary_words WHERE normalized_word = ?1")?;
        let result = statement
            .query_row(params![normalized], |row| {
                Ok(DictionaryWordDto {
                    id: row.get(0)?,
                    word: row.get(1)?,
                    created_at: row.get(2)?,
                    normalized_word: None,
                    user_id: None,
                    tags: None,
                    is_favorite: None,
                    srs_stage: None,
                    updated_at: None,
                    deleted_at: None,
                    synced_at: None,
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
        let mut stmt = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE id = ?1 LIMIT 1",
        )?;
        stmt.query_row(params![input.id], |row| read_full_row(row))
            .optional()?
    } else {
        let mut stmt = repo
            .connection
            .prepare("SELECT id, word, created_at FROM dictionary_words WHERE id = ?1")?;
        stmt.query_row(params![input.id], |row| {
            Ok(DictionaryWordDto {
                id: row.get(0)?,
                word: row.get(1)?,
                created_at: row.get(2)?,
                normalized_word: None,
                user_id: None,
                tags: None,
                is_favorite: None,
                srs_stage: None,
                updated_at: None,
                deleted_at: None,
                synced_at: None,
            })
        })
        .optional()?
    };
    let existing = existing.ok_or_else(|| AppError::NotFound(format!("Dictionary word {} not found", input.id)))?;
    if !col_exists(&repo.connection, "user_id") {
        if let Some(new_word) = input.word.as_deref() {
            let trimmed = new_word.trim();
            if trimmed.is_empty() {
                return Err(AppError::InvalidInput("Word is required".to_string()));
            }
            if trimmed.len() > 200 {
                return Err(AppError::InvalidInput("Word must be 200 characters or less".to_string()));
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
                user_id: None,
                tags: None,
                is_favorite: None,
                srs_stage: None,
                updated_at: None,
                deleted_at: None,
                synced_at: None,
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
    let is_favorite = input.is_favorite.or(existing.is_favorite).unwrap_or(false);
    let srs_stage = input.srs_stage.or(existing.srs_stage).unwrap_or(0).clamp(0, 5);
    let now = Utc::now().to_rfc3339();
    repo.connection.execute(
        "UPDATE dictionary_words SET word = ?1, normalized_word = ?2, tags_json = ?3, is_favorite = ?4, srs_stage = ?5, updated_at = ?6 WHERE id = ?7",
        params![trimmed, normalized, tags_json, if is_favorite { 1 } else { 0 }, srs_stage, now, input.id],
    )?;
    Ok(DictionaryWordDto {
        id: existing.id.clone(),
        word: trimmed.to_string(),
        created_at: existing.created_at,
        normalized_word: Some(normalized),
        user_id: Some(user_id),
        tags: Some(tags),
        is_favorite: Some(is_favorite),
        srs_stage: Some(srs_stage),
        updated_at: Some(now),
        deleted_at: None,
        synced_at: None,
    })
}

pub fn remove_dictionary_word(repo: &LibraryRepository, id: &str) -> AppResult<()> {
    if col_exists(&repo.connection, "deleted_at") {
        let now = Utc::now().to_rfc3339();
        let rows = repo.connection.execute(
            "UPDATE dictionary_words SET deleted_at = ?1, updated_at = ?1 WHERE id = ?2 AND deleted_at IS NULL",
            params![now, id],
        )?;
        if rows == 0 {
            let rows2 = repo.connection.execute("DELETE FROM dictionary_words WHERE id = ?1", params![id])?;
            if rows2 == 0 {
                return Err(AppError::NotFound(format!("Dictionary word {} not found", id)));
            }
        }
        Ok(())
    } else {
        let rows = repo.connection.execute("DELETE FROM dictionary_words WHERE id = ?1", params![id])?;
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
                normalized_word: None,
                user_id: None,
                tags: None,
                is_favorite: None,
                srs_stage: None,
                updated_at: None,
                deleted_at: None,
                synced_at: None,
            })
        })?;
        return Ok(rows.collect::<Result<Vec<_>, _>>()?);
    }
    let uid = user_id.unwrap_or("");
    let pattern_prefix = format!("{}%", q);
    let pattern_sub = format!("%{}%", q);
    let mut stmt = repo.connection.prepare(
        "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') AND (normalized_word LIKE ?2 OR normalized_word LIKE ?3) ORDER BY updated_at DESC LIMIT 200",
    )?;
    let mut candidates: Vec<DictionaryWordDto> = stmt
        .query_map(params![uid, pattern_prefix, pattern_sub], |row| read_full_row(row))?
        .collect::<Result<Vec<_>, _>>()?;
    if candidates.is_empty() && fuzzy {
        let mut stmt2 = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') LIMIT 500",
        )?;
        let all: Vec<DictionaryWordDto> = stmt2
            .query_map(params![uid], |row| read_full_row(row))?
            .collect::<Result<Vec<_>, _>>()?;
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
        let mut stmt2 = repo.connection.prepare(
            "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE deleted_at IS NULL AND (user_id = ?1 OR ?1 = '') LIMIT 500",
        )?;
        let all: Vec<DictionaryWordDto> = stmt2
            .query_map(params![uid], |row| read_full_row(row))?
            .collect::<Result<Vec<_>, _>>()?;
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
        let mut out = String::from("word,tags,is_favorite,srs_stage,updated_at\n");
        for w in words {
            let tags = w.tags.unwrap_or_default().join("|");
            let fav = if w.is_favorite.unwrap_or(false) { "true" } else { "false" };
            let srs = w.srs_stage.unwrap_or(0).to_string();
            let updated = w.updated_at.unwrap_or(w.created_at);
            let word_esc = w.word.replace('"', "\"\"");
            out.push_str(&format!("\"{}\",\"{}\",{},{},{}\n", word_esc, tags, fav, srs, updated));
        }
        Ok(out)
    } else {
        let json_words: Vec<serde_json::Value> = words
            .into_iter()
            .map(|w| {
                serde_json::json!({
                    "word": w.word,
                    "tags": w.tags.unwrap_or_default(),
                    "is_favorite": w.is_favorite.unwrap_or(false),
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
    let entries: Vec<(String, Vec<String>, bool, i32, String)> = if format == "csv" {
        let mut v = vec![];
        for (idx, line) in payload.lines().enumerate() {
            if idx == 0 && line.starts_with("word") {
                continue;
            }
            if line.trim().is_empty() {
                continue;
            }
            let parts: Vec<String> = split_csv_line(line);
            if parts.is_empty() {
                errors.push(ImportDictionaryError { row: idx as i64 + 1, reason: "empty row".to_string() });
                continue;
            }
            let word = parts.get(0).cloned().unwrap_or_default().trim().trim_matches('"').to_string();
            let tags_str = parts.get(1).cloned().unwrap_or_default().trim().trim_matches('"').to_string();
            let tags = if tags_str.is_empty() { vec![] } else { tags_str.split('|').map(|s| s.to_string()).collect() };
            let fav = parts.get(2).map(|s| s.trim().trim_matches('"') == "true").unwrap_or(false);
            let srs = parts.get(3).and_then(|s| s.trim().trim_matches('"').parse::<i32>().ok()).unwrap_or(0);
            let updated = parts.get(4).cloned().unwrap_or_else(|| Utc::now().to_rfc3339()).trim().trim_matches('"').to_string();
            v.push((word, tags, fav, srs, updated));
        }
        v
    } else {
        let val: serde_json::Value = serde_json::from_str(payload).map_err(|e| AppError::InvalidInput(e.to_string()))?;
        let arr = val.get("words").and_then(|v| v.as_array()).cloned().unwrap_or_default();
        let mut v = vec![];
        for item in arr {
            let word = item.get("word").and_then(|x| x.as_str()).unwrap_or("").to_string();
            let tags = item.get("tags").and_then(|x| x.as_array()).map(|a| a.iter().filter_map(|e| e.as_str().map(|s| s.to_string())).collect()).unwrap_or_default();
            let fav = item.get("is_favorite").and_then(|x| x.as_bool()).unwrap_or(false);
            let srs = item.get("srs_stage").and_then(|x| x.as_i64()).unwrap_or(0) as i32;
            let updated = item.get("updated_at").and_then(|x| x.as_str()).unwrap_or(&Utc::now().to_rfc3339()).to_string();
            v.push((word, tags, fav, srs, updated));
        }
        v
    };
    for (idx, (word, tags, fav, srs, updated_at)) in entries.into_iter().enumerate() {
        let trimmed = word.trim();
        if trimmed.is_empty() || trimmed.len() > 200 {
            errors.push(ImportDictionaryError { row: idx as i64 + 1, reason: "invalid word len".to_string() });
            continue;
        }
        let normalized = normalize_word(trimmed);
        let existing: Option<DictionaryWordDto> = if col_exists(&repo.connection, "user_id") {
            let mut stmt = repo.connection.prepare(
                "SELECT id, word, created_at, normalized_word, user_id, tags_json, is_favorite, srs_stage, updated_at, deleted_at, synced_at FROM dictionary_words WHERE user_id = ?1 AND normalized_word = ?2 LIMIT 1",
            ).unwrap();
            stmt.query_row(params![uid, normalized], |row| read_full_row(row)).optional().unwrap()
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
                "UPDATE dictionary_words SET word = ?1, tags_json = ?2, is_favorite = ?3, srs_stage = ?4, updated_at = ?5 WHERE id = ?6",
                params![trimmed, tags_json, if fav { 1 } else { 0 }, srs.clamp(0, 5), updated_at, ex.id],
            );
            imported += 1;
        } else {
            let id = Uuid::new_v4().to_string();
            let tags_json = serde_json::to_string(&tags).unwrap();
            let now_created = updated_at.clone();
            if col_exists(&repo.connection, "user_id") {
                let _ = repo.connection.execute(
                    "INSERT INTO dictionary_words (id, word, normalized_word, user_id, tags_json, is_favorite, srs_stage, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
                    params![id, trimmed, normalized, uid, tags_json, if fav { 1 } else { 0 }, srs.clamp(0, 5), now_created, updated_at],
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
        let first =
            add_dictionary_word(&repo, AddDictionaryWordInput { word: "Serendipity".to_string(), user_id: None, tags: None, is_favorite: None, srs_stage: None })
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
        let word =
            add_dictionary_word(&repo, AddDictionaryWordInput { word: "Ephemeral".to_string(), user_id: None, tags: None, is_favorite: None, srs_stage: None })
                .unwrap();
        remove_dictionary_word(&repo, &word.id).unwrap();
        let words = list_dictionary_words(&repo).unwrap();
        assert!(words.is_empty());
    }

    #[test]
    fn duplicate_per_user_returns_error() {
        let repo = new_repository();
        add_dictionary_word(&repo, AddDictionaryWordInput { word: "Hola".to_string(), user_id: Some("u1".to_string()), tags: None, is_favorite: None, srs_stage: None }).unwrap();
        let dup = add_dictionary_word(&repo, AddDictionaryWordInput { word: "hola".to_string(), user_id: Some("u1".to_string()), tags: None, is_favorite: None, srs_stage: None });
        assert!(dup.is_err());
        let ok = add_dictionary_word(&repo, AddDictionaryWordInput { word: "hola".to_string(), user_id: Some("u2".to_string()), tags: None, is_favorite: None, srs_stage: None }).unwrap();
        assert_eq!(ok.word, "hola");
    }

    #[test]
    fn update_preserves_id_and_bumps_updated_at() {
        let repo = new_repository();
        let w = add_dictionary_word(&repo, AddDictionaryWordInput { word: "hola".to_string(), user_id: None, tags: None, is_favorite: None, srs_stage: None }).unwrap();
        let updated = update_dictionary_word(&repo, UpdateDictionaryWordInput { id: w.id.clone(), word: Some("Hola!".to_string()), tags: None, is_favorite: None, srs_stage: None }).unwrap();
        assert_eq!(updated.id, w.id);
        assert_eq!(updated.word, "Hola!");
        assert!(updated.updated_at.unwrap() >= w.updated_at.unwrap());
    }

    #[test]
    fn search_prefix_and_fuzzy() {
        let repo = new_repository();
        add_dictionary_word(&repo, AddDictionaryWordInput { word: "biblioteca".to_string(), user_id: None, tags: None, is_favorite: None, srs_stage: None }).unwrap();
        let res = search_dictionary_words(&repo, "bibli", 10, false, None).unwrap();
        assert_eq!(res.len(), 1);
        let fuzzy = search_dictionary_words(&repo, "bibilioteca", 10, true, None).unwrap();
        assert_eq!(fuzzy.len(), 1);
    }

    #[test]
    fn import_csv_partial_success() {
        let repo = new_repository();
        let csv = "word,tags,is_favorite,srs_stage,updated_at\n\"hola\",\"\",false,0,2026-08-25T10:00:00Z\n\"\",\"\",false,0,2026-08-25T10:00:00Z\n";
        let r = import_dictionary(&repo, csv, "csv", None).unwrap();
        assert_eq!(r.imported, 1);
        assert_eq!(r.errors.len(), 1);
    }
}
