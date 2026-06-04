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
    /// Absolute path to the resources cache directory
    pub resources_path: String,
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

        // --- Extract all resources (CSS, images, fonts) AND chapters ---
        // All files saved with their original paths inside resources/
        // This preserves relative URL structure: chapters reference ../css/foo.css etc.
        let resources_dir = cache_dir.join("resources");
        fs::create_dir_all(&resources_dir)
            .map_err(|e| format!("Failed to create resources dir: {}", e))?;

        // First extract all resources from manifest
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
    pub fn get_chapter(cache_dir: &Path, chapter_index: usize) -> Result<EpubChapterContent, String> {
        // Read spine paths to find the chapter's original location
        let spine_path = cache_dir.join("spine.json");
        if !spine_path.exists() {
            return Err("EPUB not cached / no spine data".to_string());
        }
        let spine_data = fs::read_to_string(&spine_path)
            .map_err(|e| format!("Failed to read spine cache: {}", e))?;
        let spine_paths: Vec<String> = serde_json::from_str(&spine_data)
            .map_err(|e| format!("Failed to parse spine cache: {}", e))?;

        let chapter_rel_path = spine_paths.get(chapter_index)
            .ok_or_else(|| format!("Chapter index {} out of range", chapter_index))?;

        let chapter_abs_path = cache_dir.join("resources").join(chapter_rel_path);
        if !chapter_abs_path.exists() {
            return Err(format!(
                "Chapter {} not found at {:?}",
                chapter_index, chapter_rel_path
            ));
        }

        let html = fs::read_to_string(&chapter_abs_path)
            .map_err(|e| format!("Failed to read chapter {}: {}", chapter_index, e))?;

        Ok(EpubChapterContent {
            index: chapter_index,
            html,
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
}
