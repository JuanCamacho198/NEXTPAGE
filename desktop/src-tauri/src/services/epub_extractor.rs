use std::fs;
use std::path::{Path, PathBuf};

use epub::doc::EpubDoc;
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
    pub chapters: Vec<EpubChapterMeta>,
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

        let total_chapters = doc.spine.len();
        let num_chapters = doc.get_num_chapters();

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
        let mut spine_paths: Vec<String> = Vec::new();
        for idx in 0..num_chapters {
            if doc.set_current_chapter(idx) {
                if let Some((html, _mime)) = doc.get_current_str() {
                    // Get the original path from spine
                    if let Some(path) = doc.get_current_path() {
                        let target_path = resources_dir.join(&path);
                        if let Some(parent) = target_path.parent() {
                            let _ = fs::create_dir_all(parent);
                        }
                        if !target_path.exists() {
                            if let Err(e) = fs::write(&target_path, &html) {
                                eprintln!("Warning: failed to write chapter {:?}: {}", path, e);
                            }
                        }
                        spine_paths.push(path.to_string_lossy().to_string());
                    }
                }
            }
        }

        // Cache spine paths for quick chapter lookup
        let spine_path = cache_dir.join("spine.json");
        let spine_data = serde_json::to_string(&spine_paths)
            .map_err(|e| format!("Failed to serialize spine: {}", e))?;
        let _ = std::fs::write(&spine_path, &spine_data);

        // --- Build spine map: filename -> spine index ---
        let mut spine_map: std::collections::HashMap<String, usize> =
            std::collections::HashMap::new();
        for (idx, spine_path_entry) in spine_paths.iter().enumerate() {
            if let Some(name) = std::path::Path::new(spine_path_entry).file_name() {
                spine_map.insert(name.to_string_lossy().to_string(), idx);
            }
        }

        // --- Build TOC from NavPoints using spine map ---
        let chapters = Self::build_toc(&doc.toc, &spine_map);

        let resources_path = resources_dir.to_string_lossy().to_string();

        Ok(EpubMetadataExtract {
            title,
            author,
            language,
            publisher,
            chapters,
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

        let chapter_abs_path = cache_dir.join("resources").join(chapter_rel_path);
        if !chapter_abs_path.exists() {
            return Err(format!("Chapter {} not found at {:?}", chapter_index, chapter_rel_path));
        }

        let html = fs::read_to_string(&chapter_abs_path)
            .map_err(|e| format!("Failed to read chapter {}: {}", chapter_index, e))?;

        // Compute the chapter's base path: the directory of the chapter's relative path
        // within the resources cache. Used by the frontend to set `<base href>` so that
        // relative URLs (../images/foo.jpg) resolve correctly.
        let chapter_base_path = std::path::Path::new(chapter_rel_path)
            .parent()
            .map(|p| p.to_string_lossy().to_string())
            .unwrap_or_default();

        let chapter_path = chapter_rel_path.replace('\\', "/");

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
            // Extract filename from href for spine lookup
            let filename = std::path::Path::new(&content_str.as_ref())
                .file_name()
                .map(|f| f.to_string_lossy().to_string())
                .unwrap_or_default();
            // Look up actual spine index, fall back to sequential index if not found
            let index = spine_map.get(&filename).copied().unwrap_or_else(|| {
                eprintln!("Warning: NavPoint href {:?} not found in spine", content_str);
                i
            });

            chapters.push(EpubChapterMeta {
                index,
                id: index.to_string(),
                label: nav.label.clone(),
                href: content_str.to_string(),
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
            let filename = std::path::Path::new(&content_str.as_ref())
                .file_name()
                .map(|f| f.to_string_lossy().to_string())
                .unwrap_or_default();
            let index = spine_map.get(&filename).copied().unwrap_or_else(|| {
                eprintln!("Warning: NavPoint href {:?} not found in spine", content_str);
                start_index + i
            });
            chapters.push(EpubChapterMeta {
                index,
                id: index.to_string(),
                label: child.label.clone(),
                href: content_str.to_string(),
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
        let text = strip_html(&html);
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
}
