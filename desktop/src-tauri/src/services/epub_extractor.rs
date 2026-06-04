use std::path::{Path, PathBuf};
use std::fs;

use epub::doc::EpubDoc;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EpubChapterMeta {
    pub index: usize,
    pub id: String,
    pub label: String,
    pub href: String,
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
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EpubChapterContent {
    pub index: usize,
    pub html: String,
    pub mime: String,
}

#[derive(Debug)]
pub struct EpubExtractor;

impl EpubExtractor {
    /// Parse EPUB metadata and extract chapters + resources to cache dir.
    /// Returns metadata and builds a cache structure under `cache_dir`.
    pub fn extract(
        epub_path: &Path,
        cache_dir: &Path,
    ) -> Result<EpubMetadataExtract, String> {
        let mut doc = EpubDoc::new(epub_path)
            .map_err(|e| format!("Failed to open EPUB: {}", e))?;

        // --- Metadata ---
        let title = doc.get_title().unwrap_or_else(|| "Unknown".to_string());
        let author = doc
            .mdata("creator")
            .map(|m| m.value.clone())
            .unwrap_or_default();
        let language = doc.mdata("language").map(|m| m.value.clone());
        let publisher = doc.mdata("publisher").map(|m| m.value.clone());

        // --- Build TOC from NavPoints ---
        let chapters = Self::build_toc(&doc.toc);
        let total_chapters = doc.spine.len();
        let num_chapters = doc.get_num_chapters();

        // --- Extract all spine chapters as files ---
        let chapters_dir = cache_dir.join("chapters");
        fs::create_dir_all(&chapters_dir)
            .map_err(|e| format!("Failed to create chapters dir: {}", e))?;

        for idx in 0..num_chapters {
            if doc.set_current_chapter(idx) {
                if let Some((html, _mime)) = doc.get_current_str() {
                    let file_name = format!("{}.xhtml", idx);
                    let chapter_path = chapters_dir.join(&file_name);
                    // Write only if not already cached
                    if !chapter_path.exists() {
                        if let Err(e) = fs::write(&chapter_path, &html) {
                            eprintln!("Warning: failed to write chapter {}: {}", idx, e);
                        }
                    }
                }
            }
        }

        // --- Extract all resources (CSS, images, fonts) ---
        let resources_dir = cache_dir.join("resources");
        fs::create_dir_all(&resources_dir)
            .map_err(|e| format!("Failed to create resources dir: {}", e))?;

        // Collect resource paths first to avoid borrow conflict
        let resource_paths: Vec<PathBuf> = doc.resources.values().map(|item| item.path.clone()).collect();

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

        Ok(EpubMetadataExtract {
            title,
            author,
            language,
            publisher,
            chapters,
            total_chapters,
        })
    }

    /// Read a cached chapter from the cache directory.
    /// Returns the HTML content with resource URIs rewritten to use `epub://` protocol
    /// (the frontend can resolve these via `get_epub_resource`).
    pub fn get_chapter(cache_dir: &Path, chapter_index: usize) -> Result<EpubChapterContent, String> {
        // Try reading from the cache
        let chapter_path = cache_dir
            .join("chapters")
            .join(format!("{}.xhtml", chapter_index));

        if !chapter_path.exists() {
            return Err(format!(
                "Chapter {} not found in cache",
                chapter_index
            ));
        }

        let html = fs::read_to_string(&chapter_path)
            .map_err(|e| format!("Failed to read chapter {}: {}", chapter_index, e))?;

        // Rewrite resource URLs to use `epub-resource://` scheme that the frontend can resolve
        let processed = Self::rewrite_resource_urls(&html, cache_dir, chapter_index);

        Ok(EpubChapterContent {
            index: chapter_index,
            html: processed,
            mime: "application/xhtml+xml".to_string(),
        })
    }

    /// Get a resource file's bytes from the cache directory.
    pub fn get_resource(cache_dir: &Path, resource_path: &str) -> Result<Vec<u8>, String> {
        let full_path = cache_dir.join("resources").join(resource_path);
        if !full_path.exists() {
            return Err(format!("Resource not found: {}", resource_path));
        }
        fs::read(&full_path)
            .map_err(|e| format!("Failed to read resource: {}", e))
    }

    fn build_toc(
        nav_points: &[epub::doc::NavPoint],
    ) -> Vec<EpubChapterMeta> {
        let mut chapters = Vec::new();

        for (i, nav) in nav_points.iter().enumerate() {
            // Find which spine index this nav point corresponds to
            let content_str = nav.content.to_string_lossy();
            // Try to find matching spine index
            let index = i; // use i as default since toc mirrors spine order often

            chapters.push(EpubChapterMeta {
                index,
                id: index.to_string(),
                label: nav.label.clone(),
                href: content_str.to_string(),
            });

            // Add children recursively
            Self::add_child_toc(&nav.children, &mut chapters, index + 1);
        }
        chapters
    }

    fn add_child_toc(
        children: &[epub::doc::NavPoint],
        chapters: &mut Vec<EpubChapterMeta>,
        start_index: usize,
    ) {
        for (i, child) in children.iter().enumerate() {
            let content_str = child.content.to_string_lossy();
            chapters.push(EpubChapterMeta {
                index: start_index + i,
                id: format!("child-{}", start_index + i),
                label: child.label.clone(),
                href: content_str.to_string(),
            });
            Self::add_child_toc(
                &child.children,
                chapters,
                start_index + i + 1,
            );
        }
    }

    /// Rewrite relative resource URLs in the chapter HTML to `file-path` scheme
    /// that the frontend can resolve using `readFile` (for resources dir).
    /// 
    /// Relative paths like `../css/style.css` or `image/photo.jpg` are resolved
    /// against the chapter's original location and replaced with absolute resource paths.
    fn rewrite_resource_urls(html: &str, cache_dir: &Path, chapter_index: usize) -> String {
        // The cache dir has resources/ with the original EPUB structure.
        // Chapter content references resources relative to chapter location.
        // We resolve these to absolute paths within resources/.
        let resources_base = cache_dir.join("resources");
        // Replace src="..." and href="..." and xlink:href="..."
        // Captures the attribute name and the URL
        let re = regex::Regex::new(
            r#"(src|href|xlink:href)="([^"]+)""#
        ).ok();

        match re {
            Some(re) => re.replace_all(html, |caps: &regex::Captures| {
                let attr = &caps[1];
                let url = &caps[2];

                // Skip absolute URLs
                if url.starts_with("http://") || url.starts_with("https://") || url.starts_with("data:") || url.starts_with("#") {
                    return format!("{}=\"{}\"", attr, url);
                }

                // Resolve relative path against resources directory
                // The chapter is at chapters/{index}.xhtml
                // Resources are at resources/ with original structure
                // Chapter was originally at some path in the EPUB like OEBPS/Text/chapter.xhtml
                // We store everything flat-ish under resources/
                let resolved = resources_base.join(url);

                if resolved.exists() {
                    let abs = resolved.to_string_lossy();
                    // Use absolute file path that the frontend can read
                    format!("{}=\"{}\"", attr, abs)
                } else {
                    // As fallback, try to find the resource anywhere in resources/
                    let file_name = Path::new(url).file_name().and_then(|n| n.to_str()).unwrap_or(url);
                    // Search for the file in resources dir
                    let found = Self::find_file(&resources_base, file_name);
                    match found {
                        Some(path) => format!("{}=\"{}\"", attr, path.to_string_lossy()),
                        None => {
                            eprintln!("Warning: resource not found: {} (from chapter {})", url, chapter_index);
                            format!("{}=\"{}\"", attr, url)
                        }
                    }
                }
            }).to_string(),
            None => html.to_string(),
        }
    }

    fn find_file(dir: &Path, file_name: &str) -> Option<PathBuf> {
        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    if let Some(found) = Self::find_file(&path, file_name) {
                        return Some(found);
                    }
                } else if path.file_name().and_then(|n| n.to_str()) == Some(file_name) {
                    return Some(path);
                }
            }
        }
        None
    }
}
