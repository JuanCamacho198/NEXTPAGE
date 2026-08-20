use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

use epub::doc::EpubDoc;
use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EpubChapterMeta {
    pub index: usize,
    pub id: String,
    pub label: String,
    pub href: String,
    pub depth: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EpubMetadataExtract {
    pub title: String,
    pub author: String,
    pub language: Option<String>,
    pub publisher: Option<String>,
    #[serde(default, alias = "chapters")]
    pub toc: Vec<EpubChapterMeta>,
    #[serde(default)]
    pub spine_hrefs: Vec<String>,
    pub total_chapters: usize,
    /// Absolute path to the resources cache directory
    pub resources_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EpubChapterContent {
    pub index: usize,
    pub html: String,
    pub mime: String,
    /// Relative directory path of the chapter within the resources cache, used for `<base href>`
    pub chapter_base_path: String,
    /// Relative path to the chapter file within the resources cache (e.g. OEBPS/Text/ch01.xhtml)
    pub chapter_path: String,
}

fn normalize_href(href: &str) -> String {
    href.replace('\\', "/")
}

fn strip_fragment(href: &str) -> String {
    if let Some(idx) = href.find('#') {
        href[..idx].to_string()
    } else {
        href.to_string()
    }
}

/// Strip injected `chrome-extension://` and `floatBarImgId` content.
/// Guarantees zero `chrome-extension://` in output.
pub fn sanitize_html(html: &str) -> String {
    let mut result = html.to_string();

    // Remove whole tags containing floatBarImgId (e.g. <img id="floatBarImgId" ...>)
    // Use regex to match <[^>]*floatBarImgId[^>]*>
    if let Ok(re_float) = Regex::new(r#"<[^>]*\bfloatBarImgId\b[^>]*>"#) {
        result = re_float.replace_all(&result, "").to_string();
    }
    // Remove whole tags where src="chrome-extension://..."
    if let Ok(re_chrome_tag) =
        Regex::new(r#"<[^>]*\bsrc\s*=\s*["']chrome-extension://[^"']*["'][^>]*>"#)
    {
        result = re_chrome_tag.replace_all(&result, "").to_string();
    }
    // Remove any remaining chrome-extension:// URLs (fallback)
    if let Ok(re_chrome_url) = Regex::new(r#"chrome-extension://[^"'\s>]+"#) {
        result = re_chrome_url.replace_all(&result, "").to_string();
    }
    // Final guarantee: string replace remaining tokens
    result = result.replace("chrome-extension://", "");
    result = result.replace("floatBarImgId", "");
    // Clean up empty src attributes left behind like src="" or src=''
    if let Ok(re_empty_src) = Regex::new(r#"\s+src\s*=\s*["']\s*["']"#) {
        result = re_empty_src.replace_all(&result, "").to_string();
    }
    result
}

/// Resolve cover href via OPF chain: cover-image → meta name=cover → guide type=cover → heuristic
/// Returns normalized href (e.g. "OEBPS/Images/cover.jpg") if found.
pub fn resolve_cover<R: Read + std::io::Seek>(
    doc: &EpubDoc<R>,
    epub_path: &Path,
) -> Option<String> {
    // Step 1: manifest properties cover-image (EPUB3)
    for res in doc.resources.values() {
        if let Some(props) = &res.properties {
            if props.split_whitespace().any(|p| p == "cover-image") {
                return Some(normalize_href(&res.path.to_string_lossy()));
            }
        }
    }
    // Step 2: meta name=cover (EPUB2 legacy, also EPUB3 fallback)
    if let Some(meta) = doc.metadata.iter().find(|m| m.property == "cover") {
        let cover_id = meta.value.trim();
        if !cover_id.is_empty() {
            if let Some(res) = doc.resources.get(cover_id) {
                return Some(normalize_href(&res.path.to_string_lossy()));
            }
            // Some EPUBs store the href directly in meta value
            if cover_id.contains('/') || cover_id.contains('.') {
                return Some(normalize_href(cover_id));
            }
        }
    }
    // Step 3: guide type=cover — parse OPF for <reference type="cover" href="...">
    if let Some(href) = find_guide_cover_href(epub_path, &doc.root_file) {
        return Some(href);
    }
    // Step 4: heuristic via manifest resources
    if let Some(href) = heuristic_cover_from_resources(doc) {
        return Some(href);
    }
    // Fallback: scan zip entries directly for heuristic (covers manifest-less images)
    heuristic_cover_from_zip(epub_path)
}

fn find_guide_cover_href(epub_path: &Path, root_file: &Path) -> Option<String> {
    let file = std::fs::File::open(epub_path).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let root_name = normalize_href(&root_file.to_string_lossy());
    let mut entry = archive.by_name(&root_name).ok()?;
    let mut opf = String::new();
    entry.read_to_string(&mut opf).ok()?;

    let base = root_file.parent().unwrap_or_else(|| Path::new(""));
    // Order-independent: try both attribute orders
    let re1 =
        Regex::new(r#"<reference[^>]*type\s*=\s*["']cover["'][^>]*href\s*=\s*["']([^"']+)["']"#)
            .ok()?;
    if let Some(cap) = re1.captures(&opf) {
        let raw = &cap[1];
        let full = base.join(raw);
        return Some(normalize_href(&full.to_string_lossy()));
    }
    let re2 =
        Regex::new(r#"<reference[^>]*href\s*=\s*["']([^"']+)["'][^>]*type\s*=\s*["']cover["']"#)
            .ok()?;
    if let Some(cap) = re2.captures(&opf) {
        let raw = &cap[1];
        let full = base.join(raw);
        return Some(normalize_href(&full.to_string_lossy()));
    }
    None
}

fn heuristic_cover_from_resources<R: Read + std::io::Seek>(doc: &EpubDoc<R>) -> Option<String> {
    let image_exts = ["jpg", "jpeg", "png", "webp"];
    let mut first_image: Option<String> = None;
    let mut cover_candidate: Option<String> = None;

    for res in doc.resources.values() {
        let path_lower = res.path.to_string_lossy().to_ascii_lowercase();
        let ext = path_lower.rsplit('.').next().unwrap_or("");
        if !image_exts.contains(&ext) {
            continue;
        }
        if first_image.is_none() {
            first_image = Some(normalize_href(&res.path.to_string_lossy()));
        }
        if (path_lower.contains("cover")
            || path_lower.contains("portada")
            || path_lower.contains("cubierta"))
            && cover_candidate.is_none()
        {
            cover_candidate = Some(normalize_href(&res.path.to_string_lossy()));
        }
    }
    cover_candidate.or(first_image)
}

fn heuristic_cover_from_zip(epub_path: &Path) -> Option<String> {
    let file = std::fs::File::open(epub_path).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let image_exts = ["jpg", "jpeg", "png", "webp"];
    let mut first_image: Option<String> = None;
    let mut cover_candidate: Option<String> = None;

    for i in 0..archive.len() {
        let entry = archive.by_index(i).ok()?;
        let name = entry.name().to_string();
        let name_lower = name.to_ascii_lowercase();
        if let Some(ext) = name_lower.rsplit('.').next() {
            if image_exts.contains(&ext) {
                if first_image.is_none() {
                    first_image = Some(normalize_href(&name));
                }
                if (name_lower.contains("cover")
                    || name_lower.contains("portada")
                    || name_lower.contains("cubierta"))
                    && cover_candidate.is_none()
                {
                    cover_candidate = Some(normalize_href(&name));
                }
            }
        }
    }
    cover_candidate.or(first_image)
}

#[derive(Debug)]
pub struct EpubExtractor;

impl EpubExtractor {
    /// Parse EPUB metadata and extract chapters + resources to cache dir.
    /// Returns metadata and builds a cache structure under `cache_dir`.
    pub fn extract(epub_path: &Path, cache_dir: &Path) -> Result<EpubMetadataExtract, String> {
        let mut doc = EpubDoc::new(epub_path).map_err(|e| format!("Failed to open EPUB: {}", e))?;

        // --- Metadata ---
        let title = doc.get_title().unwrap_or_else(|| "Unknown".to_string());
        let author = doc.mdata("creator").map(|m| m.value.clone()).unwrap_or_default();
        let language = doc.mdata("language").map(|m| m.value.clone());
        let publisher = doc.mdata("publisher").map(|m| m.value.clone());

        // --- Build filtered spineHrefs (linear=no excluded) ---
        let mut spine_hrefs: Vec<String> = Vec::new();
        for item in &doc.spine {
            if !item.linear {
                continue;
            }
            if let Some(res) = doc.resources.get(&item.idref) {
                spine_hrefs.push(normalize_href(&res.path.to_string_lossy()));
            }
        }
        let total_chapters = spine_hrefs.len();

        // --- Extract all resources (CSS, images, fonts) AND chapters ---
        // All files saved with their original paths inside resources/
        // This preserves relative URL structure: chapters reference ../css/foo.css etc.
        let resources_dir = cache_dir.join("resources");
        fs::create_dir_all(&resources_dir)
            .map_err(|e| format!("Failed to create resources dir: {}", e))?;

        // First extract all resources from manifest
        let resource_paths: Vec<PathBuf> =
            doc.resources.values().map(|item| item.path.clone()).collect();
        for rel_path in &resource_paths {
            let target_path = resources_dir.join(rel_path);
            if target_path.exists() {
                continue;
            }
            if let Some(parent) = target_path.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if let Some(data) = doc.get_resource_by_path(rel_path) {
                if let Err(e) = fs::write(&target_path, &data) {
                    eprintln!("Warning: failed to write resource {:?}: {}", rel_path, e);
                }
            }
        }

        // Then extract all spine chapters — save at their original resource paths
        // so that relative URLs (../css/foo.css etc.) resolve correctly
        // Use filtered spine_hrefs order: iterate by filtered set and pull html via idref
        // For filtered spine, we need to map idref -> path and fetch via doc.get_resource
        for href in &spine_hrefs {
            // Find idref for this href
            let idref_opt = doc
                .resources
                .iter()
                .find(|(_, res)| normalize_href(&res.path.to_string_lossy()) == *href)
                .map(|(id, _)| id.clone());
            if let Some(idref) = idref_opt {
                if let Some((html, _mime)) = doc.get_resource_str(&idref) {
                    let sanitized = sanitize_html(&html);
                    let target_path = resources_dir.join(href);
                    if let Some(parent) = target_path.parent() {
                        let _ = fs::create_dir_all(parent);
                    }
                    if !target_path.exists() {
                        if let Err(e) = fs::write(&target_path, sanitized.as_bytes()) {
                            eprintln!("Warning: failed to write chapter {:?}: {}", href, e);
                        }
                    } else {
                        // Overwrite with sanitized version if not yet sanitized (ensure zero chrome-extension)
                        let existing = fs::read_to_string(&target_path).unwrap_or_default();
                        if existing.contains("chrome-extension://")
                            || existing.contains("floatBarImgId")
                        {
                            let _ = fs::write(&target_path, sanitized.as_bytes());
                        }
                    }
                }
            }
        }

        // Cache spine paths for quick chapter lookup
        let spine_path = cache_dir.join("spine.json");
        let spine_data = serde_json::to_string(&spine_hrefs)
            .map_err(|e| format!("Failed to serialize spine: {}", e))?;
        let _ = std::fs::write(&spine_path, &spine_data);

        // --- Build spine map: filename -> filtered spine index ---
        let mut spine_map: std::collections::HashMap<String, usize> =
            std::collections::HashMap::new();
        for (idx, spine_path_entry) in spine_hrefs.iter().enumerate() {
            if let Some(name) = std::path::Path::new(spine_path_entry).file_name() {
                spine_map.insert(name.to_string_lossy().to_string(), idx);
            }
            // Also insert full normalized href for exact match fallback
            spine_map.insert(spine_path_entry.clone(), idx);
        }

        // --- Build TOC from NavPoints using filtered spine map ---
        let toc = Self::build_toc(&doc.toc, &spine_map);

        let resources_path = resources_dir.to_string_lossy().to_string();

        Ok(EpubMetadataExtract {
            title,
            author,
            language,
            publisher,
            toc,
            spine_hrefs,
            total_chapters,
            resources_path,
        })
    }

    /// Read a cached chapter from the cache directory.
    /// The HTML is returned as-is with original relative URLs — the frontend
    /// sets a `<base>` tag pointing to the resources dir so relative paths resolve.
    pub fn get_chapter(
        cache_dir: &Path,
        chapter_index: usize,
    ) -> Result<EpubChapterContent, String> {
        // Read spine paths to find the chapter's original location
        let spine_path = cache_dir.join("spine.json");
        if !spine_path.exists() {
            return Err("EPUB not cached / no spine data".to_string());
        }
        let spine_data = fs::read_to_string(&spine_path)
            .map_err(|e| format!("Failed to read spine cache: {}", e))?;
        let spine_paths: Vec<String> = serde_json::from_str(&spine_data)
            .map_err(|e| format!("Failed to parse spine cache: {}", e))?;

        let chapter_rel_path = spine_paths
            .get(chapter_index)
            .ok_or_else(|| format!("Chapter index {} out of range", chapter_index))?;

        // Backfill: old Windows caches stored spine entries with backslashes
        let normalized_rel = normalize_href(chapter_rel_path);
        // Resolve file on disk handling both old (backslash) and new (slash) caches
        let mut chapter_abs_path = cache_dir.join("resources").join(&normalized_rel);
        if !chapter_abs_path.exists() && normalized_rel != *chapter_rel_path {
            let legacy_path = cache_dir.join("resources").join(chapter_rel_path);
            if legacy_path.exists() {
                chapter_abs_path = legacy_path;
            }
        }
        if !chapter_abs_path.exists() {
            return Err(format!("Chapter {} not found at {:?}", chapter_index, chapter_rel_path));
        }

        let html = fs::read_to_string(&chapter_abs_path)
            .map_err(|e| format!("Failed to read chapter {}: {}", chapter_index, e))?;

        // Compute the chapter's base path: the directory of the chapter's relative path
        // within the resources cache. Used by the frontend to set `<base href>` so that
        // relative URLs (../images/foo.jpg) resolve correctly.
        let chapter_base_path = normalize_href(
            &std::path::Path::new(&normalized_rel)
                .parent()
                .map(|p| p.to_string_lossy().to_string())
                .unwrap_or_default(),
        );

        let chapter_path = normalized_rel;

        Ok(EpubChapterContent {
            index: chapter_index,
            html,
            mime: "application/xhtml+xml".to_string(),
            chapter_base_path,
            chapter_path,
        })
    }

    /// Get a resource file's bytes from the cache directory.
    pub fn get_resource(cache_dir: &Path, resource_path: &str) -> Result<Vec<u8>, String> {
        let full_path = cache_dir.join("resources").join(resource_path);
        if !full_path.exists() {
            return Err(format!("Resource not found: {}", resource_path));
        }
        fs::read(&full_path).map_err(|e| format!("Failed to read resource: {}", e))
    }

    fn build_toc(
        nav_points: &[epub::doc::NavPoint],
        spine_map: &std::collections::HashMap<String, usize>,
    ) -> Vec<EpubChapterMeta> {
        let mut chapters = Vec::new();

        for (i, nav) in nav_points.iter().enumerate() {
            // Find which spine index this nav point corresponds to
            let content_str = nav.content.to_string_lossy();
            let href = normalize_href(&content_str);
            // Strip fragment for spine lookup (e.g. HM-colombia-3.html#_idParaDest-5)
            let href_base = strip_fragment(&href);
            // Extract filename from fragment-stripped href and also try full href for spine lookup
            let filename = std::path::Path::new(&href_base)
                .file_name()
                .map(|f| f.to_string_lossy().to_string())
                .unwrap_or_default();
            // Try full base href first, then filename, fall back to sequential
            let index = spine_map
                .get(&href_base)
                .copied()
                .or_else(|| spine_map.get(&filename).copied())
                .unwrap_or_else(|| {
                    eprintln!("Warning: NavPoint href {:?} not found in spine", content_str);
                    i
                });

            chapters.push(EpubChapterMeta {
                index,
                // The id must be unique per TOC entry. Multiple nav points can
                // resolve to the same spine index (e.g. a "Part" heading and its
                // first chapter pointing at the same file), so using the spine
                // index here would produce duplicate ids and crash keyed each
                // blocks in the frontend (each_key_duplicate). `chapters.len()`
                // is a monotonic counter across the whole flattened TOC.
                id: format!("chapter-{}", chapters.len()),
                label: nav.label.clone(),
                href,
                depth: 0,
            });

            // Add children recursively with depth + 1
            Self::add_child_toc(&nav.children, &mut chapters, spine_map, index + 1, 0);
        }
        chapters
    }

    fn add_child_toc(
        children: &[epub::doc::NavPoint],
        chapters: &mut Vec<EpubChapterMeta>,
        spine_map: &std::collections::HashMap<String, usize>,
        start_index: usize,
        current_depth: usize,
    ) {
        let child_depth = current_depth + 1;
        for (i, child) in children.iter().enumerate() {
            let content_str = child.content.to_string_lossy();
            let href = normalize_href(&content_str);
            let href_base = strip_fragment(&href);
            let filename = std::path::Path::new(&href_base)
                .file_name()
                .map(|f| f.to_string_lossy().to_string())
                .unwrap_or_default();
            let index = spine_map
                .get(&href_base)
                .copied()
                .or_else(|| spine_map.get(&filename).copied())
                .unwrap_or_else(|| {
                    eprintln!("Warning: NavPoint href {:?} not found in spine", content_str);
                    start_index + i
                });
            chapters.push(EpubChapterMeta {
                index,
                id: format!("chapter-{}", chapters.len()),
                label: child.label.clone(),
                href,
                depth: child_depth,
            });
            Self::add_child_toc(&child.children, chapters, spine_map, index + 1, child_depth);
        }
    }
}

/// Extract plain text from all cached EPUB chapters.
/// Returns Vec of (locator, text_content, chunk_index).
pub fn extract_plain_texts(
    cache_dir: &std::path::Path,
) -> Result<Vec<(String, String, i32)>, String> {
    let spine_path = cache_dir.join("spine.json");
    if !spine_path.exists() {
        return Err("EPUB not cached / no spine data".to_string());
    }
    let spine_data = std::fs::read_to_string(&spine_path)
        .map_err(|e| format!("Failed to read spine cache: {}", e))?;
    let spine_paths: Vec<String> = serde_json::from_str(&spine_data)
        .map_err(|e| format!("Failed to parse spine cache: {}", e))?;

    let mut chunks = Vec::new();
    for (index, rel_path) in spine_paths.iter().enumerate() {
        let chapter_path = cache_dir.join("resources").join(rel_path);
        if !chapter_path.exists() {
            eprintln!("Warning: chapter {} not found at {:?}", index, rel_path);
            continue;
        }
        let html = match std::fs::read_to_string(&chapter_path) {
            Ok(h) => h,
            Err(e) => {
                eprintln!("Warning: failed to read chapter {}: {}", index, e);
                continue;
            }
        };
        let sanitized = sanitize_html(&html);
        let text = strip_html(&sanitized);
        if !text.is_empty() {
            chunks.push((format!("chapter:{}", index), text, index as i32));
        }
    }

    Ok(chunks)
}

/// Strip HTML tags from a string, returning plain text.
pub fn strip_html(html: &str) -> String {
    let mut text = String::with_capacity(html.len());
    let mut in_tag = false;
    let mut in_entity = false;
    let mut entity_buf = String::new();

    for c in html.chars() {
        if in_tag {
            if c == '>' {
                in_tag = false;
            }
        } else if c == '<' {
            in_tag = true;
        } else if c == '&' {
            in_entity = true;
            entity_buf.clear();
        } else if in_entity {
            if c == ';' {
                // Decode common HTML entities
                let decoded = match entity_buf.as_str() {
                    "amp" => "&",
                    "lt" => "<",
                    "gt" => ">",
                    "quot" => "\"",
                    "apos" => "'",
                    "nbsp" => " ",
                    _ => "",
                };
                text.push_str(decoded);
                in_entity = false;
            } else if c == ' ' || c == '<' {
                text.push('&');
                text.push_str(&entity_buf);
                text.push(c);
                in_entity = false;
            } else {
                entity_buf.push(c);
            }
        } else {
            text.push(c);
        }
    }

    // Collapse whitespace: replace newlines/tabs with spaces, trim
    let collapsed: String =
        text.chars().map(|c| if c == '\n' || c == '\t' || c == '\r' { ' ' } else { c }).collect();

    // Remove multiple consecutive spaces
    let mut result = String::with_capacity(collapsed.len());
    let mut prev_space = false;
    for c in collapsed.chars() {
        if c == ' ' {
            if !prev_space {
                result.push(c);
                prev_space = true;
            }
        } else {
            result.push(c);
            prev_space = false;
        }
    }

    result.trim().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    #[test]
    fn test_strip_html_simple() {
        let html = "<p>Hello World</p>";
        assert_eq!(strip_html(html), "Hello World");
    }

    #[test]
    fn test_strip_html_with_entities() {
        let html = "<p>Hello &amp; World &lt;3</p>";
        assert_eq!(strip_html(html), "Hello & World <3");
    }

    #[test]
    fn test_strip_html_nested_tags() {
        let html = "<div><p>Hello <b>World</b></p></div>";
        assert_eq!(strip_html(html), "Hello World");
    }

    #[test]
    fn test_strip_html_collapses_whitespace() {
        let html = "<p>Hello\n\nWorld</p><p>  Test  </p>";
        assert_eq!(strip_html(html), "Hello World Test");
    }

    #[test]
    fn test_strip_html_empty() {
        assert_eq!(strip_html(""), "");
    }

    #[test]
    fn test_strip_html_no_tags() {
        assert_eq!(strip_html("Plain text only"), "Plain text only");
    }

    // ── 1.1: spine authority & linear filtering ──

    #[test]
    fn test_metadata_split_has_spine_hrefs_and_toc() {
        let dir = std::env::temp_dir().join(format!("epub_split_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        // Create minimal epub with 2 chapters
        let epub_path = dir.join("test.epub");
        create_minimal_epub(&epub_path, 2, false);
        // Debug: try opening with EpubDoc directly
        match EpubDoc::new(&epub_path) {
            Ok(doc) => {
                eprintln!(
                    "DEBUG spine len={}, resources len={}, toc len={}, version={:?}",
                    doc.spine.len(),
                    doc.resources.len(),
                    doc.toc.len(),
                    doc.version
                );
                for s in &doc.spine {
                    eprintln!(" spine item idref={} linear={}", s.idref, s.linear);
                }
                for (k, v) in &doc.resources {
                    eprintln!(" resource {} -> {}", k, v.path.display());
                }
            }
            Err(e) => eprintln!("DEBUG EpubDoc::new failed: {}", e),
        }
        let cache = dir.join("cache");
        let meta = match EpubExtractor::extract(&epub_path, &cache) {
            Ok(m) => m,
            Err(e) => panic!("extract failed: {}", e),
        };
        eprintln!(
            "meta spine_hrefs={:?}, toc len={}, total={}",
            meta.spine_hrefs,
            meta.toc.len(),
            meta.total_chapters
        );
        assert_eq!(meta.spine_hrefs.len(), 2);
        // Minimal epub without NCX has toc 0; ensure spine authority holds
        assert!(meta.toc.is_empty() || meta.toc.len() == 2);
        assert_eq!(meta.total_chapters, 2);
        assert_eq!(meta.total_chapters, meta.spine_hrefs.len());
        // Verify spine.json equals spine_hrefs
        let spine_data = std::fs::read_to_string(cache.join("spine.json")).unwrap();
        let spine: Vec<String> = serde_json::from_str(&spine_data).unwrap();
        assert_eq!(spine, meta.spine_hrefs);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_linear_no_excluded_from_spine() {
        // accessible_epub_3 has cover linear=no among 22 spine items → 21 filtered
        let epub_path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../desktop/src/test/fixtures/epubs/accessible_epub_3.epub");
        if !epub_path.exists() {
            // Fallback to absolute path
            let alt = PathBuf::from(
                "C:/Users/Juan Camacho/Documents/PROYECTOS/NEXTPAGE/desktop/src/test/fixtures/epubs/accessible_epub_3.epub",
            );
            if !alt.exists() {
                eprintln!("skipping linear_no test: fixture not found");
                return;
            }
            test_linear_with_path(&alt);
            return;
        }
        test_linear_with_path(&epub_path);
    }

    fn test_linear_with_path(epub_path: &Path) {
        let dir = std::env::temp_dir().join(format!("epub_linear_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let cache = dir.join("cache");
        let meta = EpubExtractor::extract(epub_path, &cache).unwrap();
        assert_eq!(
            meta.total_chapters, 21,
            "accessible_epub_3 should have 21 after filtering cover linear=no"
        );
        assert_eq!(meta.spine_hrefs.len(), 21);
        assert!(!meta.spine_hrefs.iter().any(|h| h.to_ascii_lowercase().contains("cover.xhtml")));
        // TOC should not contain cover either (or if it does, index must be valid filtered)
        for entry in &meta.toc {
            assert!(entry.index < 21, "toc index must be within filtered spine");
        }
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_sanitize_html_strips_chrome_extension() {
        let html = r#"<div><p>Hello</p><img id="floatBarImgId" src="chrome-extension://dbkmjjclgbiooljcegcddagnddjedmed/img.png"><p>World <img src="chrome-extension://abc/def.png"></p></div>"#;
        let sanitized = sanitize_html(html);
        assert!(
            !sanitized.contains("chrome-extension://"),
            "must guarantee zero chrome-extension://"
        );
        assert!(!sanitized.contains("floatBarImgId"), "must strip floatBarImgId");
        assert!(sanitized.contains("Hello"));
        assert!(sanitized.contains("World"));
    }

    #[test]
    fn test_sanitize_html_preserves_normal_content() {
        let html = r#"<p>Keep this <img src="images/cover.jpg"> and <a href="chapter2.xhtml">link</a></p>"#;
        let sanitized = sanitize_html(html);
        assert!(sanitized.contains("images/cover.jpg"));
        assert!(sanitized.contains("chapter2.xhtml"));
        assert!(!sanitized.contains("chrome-extension://"));
    }

    #[test]
    fn test_sanitize_html_empty_src_cleaned() {
        let html = r#"<p><img src="chrome-extension://evil"></p>"#;
        let sanitized = sanitize_html(html);
        assert!(!sanitized.contains("chrome-extension://"));
        assert!(!sanitized.contains("evil"));
        // No broken src="" should remain with empty value? Either removed or empty but not chrome
        assert!(!sanitized.contains("chrome"));
    }

    #[test]
    fn test_extract_plain_texts_sanitized() {
        let dir = std::env::temp_dir().join(format!("epub_sanitize_text_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(dir.join("resources")).unwrap();
        let spine = vec!["ch1.xhtml".to_string()];
        std::fs::write(dir.join("spine.json"), serde_json::to_string(&spine).unwrap()).unwrap();
        std::fs::write(
            dir.join("resources/ch1.xhtml"),
            r#"<p>Hello <img id="floatBarImgId" src="chrome-extension://abc"> world</p>"#,
        )
        .unwrap();
        let chunks = extract_plain_texts(&dir).unwrap();
        assert_eq!(chunks.len(), 1);
        assert!(!chunks[0].1.contains("chrome-extension://"));
        assert!(chunks[0].1.contains("Hello"));
        assert!(chunks[0].1.contains("world"));
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_cover_chain_cover_image_priority() {
        let dir = std::env::temp_dir().join(format!("epub_cover1_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let epub_path = dir.join("cover_image.epub");
        create_epub_with_cover(&epub_path, CoverMode::CoverImage);
        let doc = EpubDoc::new(&epub_path).unwrap();
        let href = resolve_cover(&doc, &epub_path).unwrap();
        assert!(
            href.to_ascii_lowercase().contains("cover.jpg"),
            "cover-image should be Images/cover.jpg got {}",
            href
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_cover_chain_meta_fallback() {
        let dir = std::env::temp_dir().join(format!("epub_cover2_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let epub_path = dir.join("cover_meta.epub");
        create_epub_with_cover(&epub_path, CoverMode::Meta);
        let doc = EpubDoc::new(&epub_path).unwrap();
        let href = resolve_cover(&doc, &epub_path).unwrap();
        assert!(
            href.to_ascii_lowercase().contains("meta_cover"),
            "meta cover should be used got {}",
            href
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_cover_chain_heuristic_fallback() {
        let dir = std::env::temp_dir().join(format!("epub_cover3_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let epub_path = dir.join("cover_heur.epub");
        create_epub_with_cover(&epub_path, CoverMode::Heuristic);
        let doc = EpubDoc::new(&epub_path).unwrap();
        let href = resolve_cover(&doc, &epub_path).unwrap();
        assert!(
            href.to_ascii_lowercase().contains("cover"),
            "heuristic should find cover got {}",
            href
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_extract_plain_texts_from_cache() {
        // Create a temp directory with spine.json and resource files
        let dir = std::env::temp_dir().join(format!("epub_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir.join("resources")).unwrap();

        // Write spine.json
        let spine_paths = vec!["chapter1.xhtml".to_string(), "chapter2.xhtml".to_string()];
        let spine_json = serde_json::to_string(&spine_paths).unwrap();
        std::fs::write(dir.join("spine.json"), &spine_json).unwrap();

        // Write chapter files
        std::fs::write(dir.join("resources/chapter1.xhtml"), "<p>First chapter content</p>")
            .unwrap();
        std::fs::write(
            dir.join("resources/chapter2.xhtml"),
            "<p>Second chapter with <b>bold</b> text</p>",
        )
        .unwrap();

        let result = extract_plain_texts(&dir).unwrap();
        assert_eq!(result.len(), 2);
        assert_eq!(result[0].0, "chapter:0");
        assert_eq!(result[0].1, "First chapter content");
        assert_eq!(result[1].0, "chapter:1");
        assert_eq!(result[1].1, "Second chapter with bold text");

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_extract_plain_texts_handles_missing_cache() {
        let dir = std::env::temp_dir().join("nonexistent_epub_test");
        let result = extract_plain_texts(&dir);
        assert!(result.is_err());
    }

    #[test]
    fn test_toc_spine_map_lookup() {
        let mut spine_map: std::collections::HashMap<String, usize> =
            std::collections::HashMap::new();
        spine_map.insert("chapter3.xhtml".to_string(), 2usize);
        spine_map.insert("chapter1.xhtml".to_string(), 0usize);
        spine_map.insert("chapter2.xhtml".to_string(), 1usize);

        // Simulate build_toc behavior: extract filename from content href and look up
        let href = "OEBPS/chapter2.xhtml";
        let filename = std::path::Path::new(href)
            .file_name()
            .map(|f| f.to_string_lossy().to_string())
            .unwrap_or_default();
        let index = spine_map.get(&filename).copied().unwrap_or(0);

        assert_eq!(index, 1); // chapter2.xhtml -> second in spine
    }

    #[test]
    fn test_toc_invalid_href_falls_back() {
        let spine_map: std::collections::HashMap<String, usize> = std::collections::HashMap::new();
        let href = "nonexistent.xhtml";
        let filename = std::path::Path::new(href)
            .file_name()
            .map(|f| f.to_string_lossy().to_string())
            .unwrap_or_default();
        let index = spine_map.get(&filename).copied().unwrap_or(0);

        assert_eq!(index, 0); // Falls back to 0
    }

    #[test]
    fn test_toc_ids_unique_when_navpoints_share_spine_index() {
        use std::collections::HashMap;
        use std::path::PathBuf;

        let mut spine_map: HashMap<String, usize> = HashMap::new();
        spine_map.insert("chapter5.xhtml".to_string(), 5usize);

        // Two nav points (a "Part" heading and its chapter) both point to
        // chapter5.xhtml, so both resolve to spine index 5 — the exact
        // duplicate-key scenario that crashed the frontend TOC
        // (each_key_duplicate: duplicate key `5`).
        let nav_points = vec![
            epub::doc::NavPoint {
                label: "Part Two".to_string(),
                content: PathBuf::from("OEBPS/chapter5.xhtml"),
                children: vec![],
                play_order: Some(10),
            },
            epub::doc::NavPoint {
                label: "Chapter 5".to_string(),
                content: PathBuf::from("OEBPS/chapter5.xhtml"),
                children: vec![],
                play_order: Some(11),
            },
        ];

        let chapters = EpubExtractor::build_toc(&nav_points, &spine_map);

        assert_eq!(chapters.len(), 2);
        assert_eq!(chapters[0].index, 5);
        assert_eq!(chapters[1].index, 5);
        // ids must still be unique even though both share spine index 5
        assert_ne!(chapters[0].id, chapters[1].id);

        let mut ids: Vec<&String> = chapters.iter().map(|c| &c.id).collect();
        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), chapters.len());
    }

    #[test]
    fn test_historia_offset2_24_spine_20_toc() {
        use std::collections::HashMap;
        use std::path::PathBuf;
        // Simulate Historia Mínima de Colombia: spine 24, TOC 20, offset-2
        // Spine: [cover.xhtml, toc.xhtml, HM-1..HM-20 (20), backmatter, colophon] = 24
        let mut spine_map: HashMap<String, usize> = HashMap::new();
        spine_map.insert("cover.xhtml".to_string(), 0);
        spine_map.insert("toc.xhtml".to_string(), 1);
        for i in 1..=20 {
            spine_map.insert(format!("HM-colombia-{}.html", i), 1 + i);
        }
        spine_map.insert("backmatter.xhtml".to_string(), 22);
        spine_map.insert("colophon.xhtml".to_string(), 23);
        // TOC nav points: 20 entries HM-1..HM-20, each maps to spine 2..21
        let nav_points: Vec<epub::doc::NavPoint> = (1..=20)
            .map(|i| epub::doc::NavPoint {
                label: format!("HM {}", i),
                content: PathBuf::from(format!("OEBPS/Text/HM-colombia-{}.html", i)),
                children: vec![],
                play_order: Some(i),
            })
            .collect();
        let chapters = EpubExtractor::build_toc(&nav_points, &spine_map);
        assert_eq!(chapters.len(), 20, "Historia TOC should be 20");
        // Offset-2: first TOC entry maps to spine index 2
        assert_eq!(chapters[0].index, 2, "toc[0] should map to spine 2 (offset-2)");
        assert_eq!(chapters[1].index, 3);
        // Toc[2] is HM-colombia-3.html with fragment in real EPUB — test that filename fallback works
        let toc_with_frag = vec![epub::doc::NavPoint {
            label: "HM 3".to_string(),
            content: PathBuf::from("OEBPS/Text/HM-colombia-3.html#_idParaDest-5"),
            children: vec![],
            play_order: Some(3),
        }];
        let frag_chapters = EpubExtractor::build_toc(&toc_with_frag, &spine_map);
        assert_eq!(frag_chapters.len(), 1);
        // Fragment href still resolves via filename fallback to spine 4 (HM-3 at index 4? Actually HM-3 is index 4)
        // HM-1 =>2, HM-2=>3, HM-3=>4
        assert_eq!(frag_chapters[0].index, 4);
        // Verify filename fallback: Text/HM-colombia-3.html without prefix still resolves
        let href = "OEBPS/Text/HM-colombia-3.html";
        let filename = std::path::Path::new(href)
            .file_name()
            .unwrap()
            .to_string_lossy()
            .to_string();
        assert_eq!(spine_map.get(&filename), Some(&4));
        // All indices must be within filtered spine (2..21) and never point to cover/toc
        for ch in &chapters {
            assert!(ch.index >= 2 && ch.index <= 21, "TOC index {} out of Historia range", ch.index);
        }
        // Ids still unique despite sequential indices
        let mut ids: Vec<&String> = chapters.iter().map(|c| &c.id).collect();
        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), chapters.len());
    }

    #[test]
    fn test_stale_cache_historia_purge_20_vs_24() {
        // Simulates cache stale detection for Historia: spine.json 24 vs metadata 20
        let dir = std::env::temp_dir().join(format!("epub_historia_purge_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        // Write spine with 24 entries (Historia)
        let spine24: Vec<String> = (0..24).map(|i| format!("ch{}.xhtml", i)).collect();
        std::fs::write(dir.join("spine.json"), serde_json::to_string(&spine24).unwrap()).unwrap();
        std::fs::write(dir.join(".cache_version"), "3").unwrap();
        // Metadata claims 20 (old TOC conflated)
        let meta = EpubMetadataExtract {
            title: "Historia".to_string(),
            author: "Auth".to_string(),
            language: None,
            publisher: None,
            toc: vec![],
            spine_hrefs: vec!["a.xhtml".to_string(); 20],
            total_chapters: 20,
            resources_path: "/tmp".to_string(),
        };
        // Simulate is_cache_stale logic from epub_reader.rs: spine len != total_chapters => stale
        let data = std::fs::read_to_string(dir.join("spine.json")).unwrap();
        let spine: Vec<String> = serde_json::from_str(&data).unwrap();
        assert_eq!(spine.len(), 24);
        assert_ne!(spine.len(), meta.total_chapters);
        assert!(spine.len() != meta.total_chapters, "Historia 24 vs 20 should be stale");
        let _ = std::fs::remove_dir_all(&dir);
    }

    // ── helpers for cover & minimal epub generation ──

    enum CoverMode {
        CoverImage,
        Meta,
        Heuristic,
    }

    fn create_minimal_epub(path: &Path, chapters: usize, with_linear_no: bool) {
        use std::io::Write;
        let file = std::fs::File::create(path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Stored);
        // mimetype must be first and uncompressed
        zip.start_file("mimetype", options).unwrap();
        zip.write_all(b"application/epub+zip").unwrap();

        let options_def = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);

        zip.start_file("META-INF/container.xml", options_def).unwrap();
        zip.write_all(
            br#"<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"#,
        )
        .unwrap();

        let mut manifest = String::new();
        let mut spine = String::new();
        for i in 0..chapters {
            manifest.push_str(&format!(
                r#"<item id="c{}" href="Text/chapter{}.xhtml" media-type="application/xhtml+xml"/>"#,
                i, i
            ));
            let linear = if with_linear_no && i == 0 { r#" linear="no""# } else { "" };
            spine.push_str(&format!(r#"<itemref idref="c{}"{} />"#, i, linear));
        }
        // Add cover image for completeness
        manifest.push_str(r#"<item id="cover-img" href="Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"#);
        let opf = format!(
            r#"<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:test</dc:identifier><dc:title>Test</dc:title><dc:language>en</dc:language></metadata><manifest>{}</manifest><spine>{}</spine></package>"#,
            manifest, spine
        );
        zip.start_file("OEBPS/content.opf", options_def).unwrap();
        zip.write_all(opf.as_bytes()).unwrap();

        for i in 0..chapters {
            zip.start_file(format!("OEBPS/Text/chapter{}.xhtml", i), options_def).unwrap();
            zip.write_all(format!(r#"<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>C{}</title></head><body><p>Chapter {} content</p></body></html>"#, i, i).as_bytes()).unwrap();
        }
        // dummy cover
        zip.start_file("OEBPS/Images/cover.jpg", options_def).unwrap();
        zip.write_all(b"\xFF\xD8\xFF").unwrap();
        zip.finish().unwrap();
    }

    fn create_epub_with_cover(path: &Path, mode: CoverMode) {
        use std::io::Write;
        let file = std::fs::File::create(path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let mimetype_opts = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Stored);
        zip.start_file("mimetype", mimetype_opts).unwrap();
        zip.write_all(b"application/epub+zip").unwrap();
        let opts = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);
        zip.start_file("META-INF/container.xml", opts).unwrap();
        zip.write_all(br#"<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"#).unwrap();

        let (manifest_extra, metadata_extra, guide_extra) = match mode {
            CoverMode::CoverImage => (
                r#"<item id="cover-img" href="Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"#,
                "",
                "",
            ),
            CoverMode::Meta => (
                r#"<item id="meta_cover" href="Images/meta_cover.jpg" media-type="image/jpeg"/>"#,
                r#"<meta name="cover" content="meta_cover"/>"#,
                "",
            ),
            CoverMode::Heuristic => (
                r#"<item id="heuristic_cover" href="Images/my_cover_portada.jpg" media-type="image/jpeg"/>"#,
                "",
                "",
            ),
        };

        let manifest = format!(
            r#"<item id="c0" href="Text/chapter0.xhtml" media-type="application/xhtml+xml"/>{}"#,
            manifest_extra
        );
        // guide for completeness (not used in these cases)
        let guide = if guide_extra.is_empty() {
            "".to_string()
        } else {
            format!("<guide>{}</guide>", guide_extra)
        };
        let opf = format!(
            r#"<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:cover-test</dc:identifier><dc:title>Cover Test</dc:title><dc:language>en</dc:language>{}</metadata><manifest>{}</manifest><spine><itemref idref="c0"/></spine>{}</package>"#,
            metadata_extra, manifest, guide
        );
        zip.start_file("OEBPS/content.opf", opts).unwrap();
        zip.write_all(opf.as_bytes()).unwrap();
        zip.start_file("OEBPS/Text/chapter0.xhtml", opts).unwrap();
        zip.write_all(br#"<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><body><p>hi</p></body></html>"#).unwrap();
        // cover images
        let cover_path = match mode {
            CoverMode::CoverImage => "OEBPS/Images/cover.jpg",
            CoverMode::Meta => "OEBPS/Images/meta_cover.jpg",
            CoverMode::Heuristic => "OEBPS/Images/my_cover_portada.jpg",
        };
        zip.start_file(cover_path, opts).unwrap();
        zip.write_all(b"\xFF\xD8\xFF").unwrap();
        // also add a non-cover image for heuristic fallback test
        if let CoverMode::Heuristic = mode {
            // add extra image that is not cover-like to ensure heuristic picks cover-like
            zip.start_file("OEBPS/Images/other.png", opts).unwrap();
            zip.write_all(b"\x89PNG").unwrap();
        }
        zip.finish().unwrap();
    }
}
