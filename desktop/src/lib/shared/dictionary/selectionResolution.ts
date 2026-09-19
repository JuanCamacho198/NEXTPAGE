/**
 * Selection resolution (REQ-DRE-004): decide create-vs-attach before any write.
 *
 * Pure and dependency-free so the heuristic is testable without the reader.
 * The window that calls this is the single-word token after tokenization, which
 * also strips surrounding punctuation.
 */
import { normalizeDictionaryKey, tokenizeSelection } from '$lib/shared/dictionary/dictionaryKey';

/** The subset of a dictionary entry this resolver reads. */
export type SelectionEntry = {
  readonly id: string;
  readonly word?: string | null;
  readonly quote?: string | null;
  readonly sourceBookId?: string | null;
  readonly sourceBookTitle?: string | null;
  readonly sourceBookAuthor?: string | null;
  readonly sourceChapter?: string | null;
  readonly sourceLocator?: string | null;
};

export type SelectionResolution =
  | { kind: 'no-op' }
  | { kind: 'create'; token: string }
  | { kind: 'attach'; token: string; entryId: string; hadEvidence: boolean }
  | { kind: 'no-match' }
  | { kind: 'ambiguous'; entryIds: string[] };

/**
 * Evidence is written as a set, so any populated evidence field counts as
 * "already has evidence" - a partially populated row still reports a re-capture
 * as `evidence-updated` instead of `created`.
 */
function hasEvidence(entry: SelectionEntry): boolean {
  return [
    entry.quote,
    entry.sourceBookId,
    entry.sourceBookTitle,
    entry.sourceBookAuthor,
    entry.sourceChapter,
    entry.sourceLocator,
  ].some((value) => typeof value === 'string' && value.length > 0);
}

/** Index local entries by normalized key; the first entry wins a shared key. */
function indexByNormalizedKey(entries: readonly SelectionEntry[]): Map<string, SelectionEntry> {
  const index = new Map<string, SelectionEntry>();

  for (const entry of entries) {
    const key = normalizeDictionaryKey(entry.word ?? '');
    if (key.length === 0 || index.has(key)) continue;
    index.set(key, entry);
  }

  return index;
}

/**
 * Tokenize first, then branch on token count: 0 -> no-op; 1 -> attach if the
 * key matches, otherwise create with the surface-form token; 2+ -> attach only
 * when exactly one distinct entry matches, otherwise no-match or ambiguous.
 *
 * `create` is reachable only from the single-token branch, so a multi-word
 * selection can never create an entry. The created word is the tokenizer's
 * surface form (casing and accents preserved, punctuation stripped), never the
 * normalized key.
 */
export function resolveSelectionTarget(
  selection: string,
  entries: readonly SelectionEntry[],
): SelectionResolution {
  const tokens = tokenizeSelection(selection);
  if (tokens.length === 0) return { kind: 'no-op' };

  const index = indexByNormalizedKey(entries);

  if (tokens.length === 1) {
    const token = tokens[0];
    const match = index.get(normalizeDictionaryKey(token));
    if (!match) return { kind: 'create', token };
    return { kind: 'attach', token, entryId: match.id, hadEvidence: hasEvidence(match) };
  }

  const matched = new Map<string, { entry: SelectionEntry; token: string }>();
  for (const token of tokens) {
    const entry = index.get(normalizeDictionaryKey(token));
    if (entry && !matched.has(entry.id)) matched.set(entry.id, { entry, token });
  }

  if (matched.size === 0) return { kind: 'no-match' };

  const matches = [...matched.values()];
  if (matches.length > 1) {
    return { kind: 'ambiguous', entryIds: matches.map(({ entry }) => entry.id) };
  }

  const [{ entry, token }] = matches;
  return { kind: 'attach', token, entryId: entry.id, hadEvidence: hasEvidence(entry) };
}
