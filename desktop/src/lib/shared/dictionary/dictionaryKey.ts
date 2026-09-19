/**
 * Dictionary natural-key contract (REQ-DSI-004).
 *
 * One rule on every platform: Rust `normalize_word`
 * (`desktop/src-tauri/src/repository/dictionary.rs`) and Kotlin
 * `DictionaryNormalizer.normalize` must produce byte-identical keys, because
 * `(user_id, normalized_word)` is the table's unique constraint and the sync
 * conflict target. A divergence creates duplicate rows and makes cross-device
 * deletes miss silently.
 */

/** Quote cap applied by the EPUB extractor; an oversized paragraph truncates, never blocks. */
export const MAX_QUOTE_LENGTH = 2000;

const COMBINING_MARKS = /[\u0300-\u036f]/g;
const LEADING_PUNCTUATION = /^[\p{P}]+/u;
const TRAILING_PUNCTUATION = /[\p{P}]+$/u;

/**
 * trim -> lowercase -> NFD decompose -> strip U+0300-U+036F.
 *
 * Punctuation is deliberately preserved (`Abyss,` -> `abyss,`). Stripping
 * surrounding punctuation belongs to `tokenizeSelection`, which runs first.
 * Lowercasing is the locale-independent `toLowerCase`, so `İstanbul` -> the
 * invariant `istanbul` and not a Turkish-locale variant.
 */
export function normalizeDictionaryKey(value: string): string {
  return value.trim().toLowerCase().normalize('NFD').replace(COMBINING_MARKS, '');
}

/**
 * Whitespace split, strip surrounding punctuation, collapse duplicate tokens.
 *
 * Surface form is preserved (original casing, accents, internal punctuation);
 * the normalized key is used only to decide whether a token is a duplicate.
 */
export function tokenizeSelection(selection: string): string[] {
  const tokens: string[] = [];
  const seenKeys = new Set<string>();

  for (const raw of selection.split(/\s+/)) {
    const token = raw.replace(LEADING_PUNCTUATION, '').replace(TRAILING_PUNCTUATION, '');
    if (token.length === 0) continue;

    const key = normalizeDictionaryKey(token);
    if (key.length === 0 || seenKeys.has(key)) continue;

    seenKeys.add(key);
    tokens.push(token);
  }

  return tokens;
}
