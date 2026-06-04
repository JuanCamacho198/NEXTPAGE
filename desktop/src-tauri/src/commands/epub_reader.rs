use std::path::PathBuf;

use tauri::{AppHandle, Manager, State};

use crate::models::IndexBookTextInput;
use crate::services::epub_extractor::{
    extract_plain_texts, EpubChapterContent, EpubExtractor, EpubMetadataExtract,
};
use crate::state::AppState;

fn get_cache_dir(app: &AppHandle, book_id: &str) -> PathBuf {
    let app_data = app.path().app_data_dir().expect("app data dir");
    app_data.join("epub_cache").join(book_id)
}

/// Parse an EPUB file: extract metadata, chapters, and resources into a cache directory.
/// Returns metadata + TOC. Subsequent calls use the cache.
#[tauri::command(rename_all = "camelCase")]
pub async fn parse_epub(
    app: AppHandle,
    _state: State<'_, AppState>,
    file_path: String,
    book_id: String,
) -> Result<EpubMetadataExtract, String> {
    let cache_dir = get_cache_dir(&app, &book_id);

    // Check if already cached
    let metadata_path = cache_dir.join("metadata.json");
    if metadata_path.exists() {
        let data = std::fs::read_to_string(&metadata_path)
            .map_err(|e| format!("Failed to read cached metadata: {}", e))?;
        let meta: EpubMetadataExtract = serde_json::from_str(&data)
            .map_err(|e| format!("Failed to parse cached metadata: {}", e))?;
        return Ok(meta);
    }

    // Extract and cache
    let meta = EpubExtractor::extract(
        std::path::Path::new(&file_path),
        &cache_dir,
    )?;

    // Cache metadata for future calls
    std::fs::create_dir_all(&cache_dir).map_err(|e| format!("Failed to create cache dir: {}", e))?;
    let data = serde_json::to_string(&meta)
        .map_err(|e| format!("Failed to serialize metadata: {}", e))?;
    std::fs::write(&metadata_path, &data)
        .map_err(|e| format!("Failed to write metadata cache: {}", e))?;

    Ok(meta)
}

/// Get a specific chapter's content from the EPUB cache.
#[tauri::command(rename_all = "camelCase")]
pub async fn get_epub_chapter(
    app: AppHandle,
    book_id: String,
    chapter_index: usize,
) -> Result<EpubChapterContent, String> {
    let cache_dir = get_cache_dir(&app, &book_id);
    EpubExtractor::get_chapter(&cache_dir, chapter_index)
}

/// Get a resource file from the EPUB cache.
#[tauri::command(rename_all = "camelCase")]
pub async fn get_epub_resource(
    app: AppHandle,
    book_id: String,
    resource_path: String,
) -> Result<Vec<u8>, String> {
    let cache_dir = get_cache_dir(&app, &book_id);
    EpubExtractor::get_resource(&cache_dir, &resource_path)
}

/// Check if an EPUB has been parsed and cached.
#[tauri::command(rename_all = "camelCase")]
pub async fn is_epub_cached(
    app: AppHandle,
    book_id: String,
) -> Result<bool, String> {
    let cache_dir = get_cache_dir(&app, &book_id);
    let metadata_path = cache_dir.join("metadata.json");
    Ok(metadata_path.exists())
}

/// Delete the EPUB cache for a book.
#[tauri::command(rename_all = "camelCase")]
pub async fn clear_epub_cache(
    app: AppHandle,
    book_id: String,
) -> Result<(), String> {
    let cache_dir = get_cache_dir(&app, &book_id);
    if cache_dir.exists() {
        std::fs::remove_dir_all(&cache_dir)
            .map_err(|e| format!("Failed to clear EPUB cache: {}", e))?;
    }
    Ok(())
}

/// Index EPUB text for FTS5 search.
/// Reads cached chapter files, strips HTML, and indexes into FTS5.
#[tauri::command(rename_all = "camelCase")]
pub async fn index_epub_text(
    app: AppHandle,
    state: State<'_, AppState>,
    book_id: String,
) -> Result<(), String> {
    let cache_dir = get_cache_dir(&app, &book_id);
    if !cache_dir.exists() {
        return Err("EPUB not cached — run parse_epub first".to_string());
    }

    let chunks = extract_plain_texts(&cache_dir)?;
    if chunks.is_empty() {
        return Ok(()); // Nothing to index
    }

    let input = IndexBookTextInput {
        book_id: book_id.clone(),
        chunks: chunks
            .into_iter()
            .map(|(locator, text_content, chunk_index)| crate::models::IndexBookTextChunkInput {
                locator,
                chunk_index,
                text_content,
            })
            .collect(),
    };

    let mut repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    repository.index_book_text(input).map_err(|e| format!("{}", e))
}
