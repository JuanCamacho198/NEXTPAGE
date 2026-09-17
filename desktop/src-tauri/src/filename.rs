//! Windows-safe filename sanitizing shared by the download temp path and every
//! on-disk artifact derived from a catalog book id (`books/` and `covers/`).
//!
//! A catalog id such as `gutendex:2701` is not a valid Windows path segment:
//! `:` starts an NTFS alternate data stream, so `books/gutendex:2701.epub`
//! writes a stream and the following rename fails with `ERROR_INVALID_PARAMETER`
//! (os error 87). Every filename derived from a book id therefore goes through
//! [`sanitize_file_stem`].

/// Windows device names are rejected even when an extension follows the stem
/// (`CON.epub` is not a legal path), so a stem matching one must be changed.
const RESERVED_DEVICE_NAMES: [&str; 22] = [
    "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
    "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
];

const MAX_EXT_CHARS: usize = 5;
const DEFAULT_EXT: &str = "epub";

/// Catalog ids are bounded so the composed Windows filename stays short.
pub(crate) const MAX_BOOK_ID_CHARS: usize = 120;
pub(crate) const FALLBACK_BOOK_ID: &str = "book";

/// Keeps only `[A-Za-z0-9_-]`, bounded in length. A fully filtered-out or empty
/// input falls back, so a segment can never be empty or contain a separator.
pub(crate) fn sanitize_segment(raw: &str, max_chars: usize, fallback: &str) -> String {
    let filtered: String = raw
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
        .take(max_chars)
        .collect();
    if filtered.is_empty() {
        fallback.to_string()
    } else {
        filtered
    }
}

/// Deterministic, Windows-safe filename stem for a book id. Character filtering
/// already drops `<>:"/\|?*`, control characters and any trailing dot or space;
/// a reserved device name is suffixed so `CON` never becomes the illegal
/// `CON.epub`. The same id always yields the same stem, so a retry rewrites one
/// path instead of accumulating files.
pub(crate) fn sanitize_file_stem(raw: &str, max_chars: usize, fallback: &str) -> String {
    let stem = sanitize_segment(raw, max_chars, fallback);
    if is_reserved_device_name(&stem) {
        format!("{stem}_")
    } else {
        stem
    }
}

fn is_reserved_device_name(stem: &str) -> bool {
    let upper = stem.to_ascii_uppercase();
    RESERVED_DEVICE_NAMES.iter().any(|reserved| upper == *reserved)
}

/// `format` sanitized to `[a-z0-9]{1,5}`, defaulting to `epub`. Anything longer
/// than five characters (or empty) is not a plausible extension and is replaced.
pub(crate) fn sanitize_extension(format: Option<&str>) -> String {
    let filtered: String = format
        .unwrap_or(DEFAULT_EXT)
        .to_ascii_lowercase()
        .chars()
        .filter(|c| c.is_ascii_lowercase() || c.is_ascii_digit())
        .collect();
    if filtered.is_empty() || filtered.len() > MAX_EXT_CHARS {
        DEFAULT_EXT.to_string()
    } else {
        filtered
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn file_stem_drops_every_windows_illegal_character() {
        // The real catalog id that broke the download: the colon is dropped.
        assert_eq!(sanitize_file_stem("gutendex:2701", 120, "book"), "gutendex2701");
        assert_eq!(sanitize_file_stem("a<b>c:d\"e/f\\g|h?i*j", 120, "book"), "abcdefghij");
        // Control characters (including tab/newline) are filtered out too.
        assert_eq!(sanitize_file_stem("line\nbreak\ttab", 120, "book"), "linebreaktab");
        assert_eq!(
            sanitize_file_stem("openlibrary:/works/OL45804W", 120, "book"),
            "openlibraryworksOL45804W"
        );
    }

    #[test]
    fn file_stem_has_no_trailing_dot_or_space() {
        assert_eq!(sanitize_file_stem("Moby Dick. ", 120, "book"), "MobyDick");
        assert_eq!(sanitize_file_stem("trailing... ", 120, "book"), "trailing");
        assert_eq!(sanitize_file_stem("   ", 120, "book"), "book");
    }

    #[test]
    fn file_stem_guards_reserved_device_names_even_with_an_extension() {
        for name in ["CON", "con", "PrN", "aux", "nul", "COM1", "com9", "LPT1", "lpt9"] {
            let stem = sanitize_file_stem(name, 120, "book");
            assert_eq!(stem, format!("{name}_"), "reserved name {name}");
            // The composed filename with an extension is legal as well.
            assert_eq!(format!("{stem}.epub"), format!("{name}_.epub"));
        }
    }

    #[test]
    fn file_stem_falls_back_and_is_deterministic() {
        // Empty result guard: a fully filtered id cannot produce an empty stem.
        assert_eq!(sanitize_file_stem(":::", 120, "book"), "book");
        assert_eq!(sanitize_file_stem("", 120, "book"), "book");

        // Determinism: the same id always maps to the same stem, so a retry
        // targets one path instead of accumulating files.
        let first = sanitize_file_stem("gutendex:2701", 120, "book");
        let second = sanitize_file_stem("gutendex:2701", 120, "book");
        assert_eq!(first, second);

        // Idempotence: re-sanitizing the output is a no-op (reserved guard included).
        assert_eq!(sanitize_file_stem(&first, 120, "book"), first);
        assert_eq!(sanitize_file_stem("CON", 120, "book"), "CON_");
        assert_eq!(sanitize_file_stem("CON_", 120, "book"), "CON_");
    }
}
