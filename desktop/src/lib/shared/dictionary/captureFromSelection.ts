/**
 * Capture orchestration (REQ-DRE-004, REQ-DRE-006, REQ-DRE-007, REQ-DRE-008).
 *
 * Pure orchestration over injected actions: this module decides *whether* a
 * write happens and which one, and returns the feedback the toolbar shows; the
 * store owns the writes. `ReaderWorkspace` supplies `dictionaryActions.words`
 * as `entries`, assembles the evidence with `buildEvidence` and calls
 * `captureFromSelection`, so the reader stays glue and the branch rules stay
 * testable without rendering a viewer.
 */
import type { DictionaryWordDto } from '$lib/shared/types/book';
import {
  resolveSelectionTarget,
  type SelectionEntry,
} from '$lib/shared/dictionary/selectionResolution';

/** The six reader-captured evidence fields, camelCase end to end. */
export type DictionaryEvidence = {
  quote: string | null;
  sourceBookId: string | null;
  sourceBookTitle: string | null;
  sourceBookAuthor: string | null;
  sourceChapter: string | null;
  sourceLocator: string | null;
};

/**
 * `created` and `evidence-updated` mean a write happened. Every other value is
 * feedback only: `already-in-dictionary` is the resolve-only branch (Decision
 * 13), `no-match`/`ambiguous` are REQ-DRE-004's two multi-word outcomes plus the
 * empty selection, and `error` reports a rejected action.
 */
export type DictionaryCaptureFeedback =
  'created' | 'evidence-updated' | 'already-in-dictionary' | 'no-match' | 'ambiguous' | 'error';

/** The reader selection fields this module reads; `SelectionData` is a superset. */
export type DictionaryCaptureData = {
  readonly quote?: string | null;
  readonly chapterTitle?: string | null;
  readonly cfi?: string | null;
};

/** The book fields snapshotted at click time; the reader's `ActiveBook` is a superset. */
export type DictionaryCaptureBook = {
  readonly id: string;
  readonly title?: string | null;
  readonly author?: string | null;
};

/** The write surface the dictionary store exposes to the reader (Decision 5). */
export type DictionaryActions = {
  readonly words: readonly DictionaryWordDto[];
  add(word: string, opts?: { evidence?: DictionaryEvidence | null }): Promise<DictionaryWordDto>;
  capture(entryId: string, evidence: DictionaryEvidence): Promise<DictionaryWordDto>;
};

export type DictionaryCaptureDeps = {
  readonly actions: DictionaryActions;
  readonly entries: readonly SelectionEntry[];
  readonly evidence?: DictionaryEvidence | null;
};

/**
 * Evidence is EPUB-only (REQ-DRE-006): a non-EPUB viewer, a null/absent quote
 * (no block ancestor, REQ-DRE-005) or a missing active book yields null, so the
 * caller creates or resolves an entry without a reference and writes nothing.
 *
 * Title and author are copied, never referenced, so a later metadata edit
 * cannot rewrite a captured snapshot (REQ-DRE-003). `chapterTitle` and the
 * selection CFI are optional and land as null when absent.
 */
export function buildEvidence(
  data: DictionaryCaptureData,
  book: DictionaryCaptureBook | null | undefined,
  viewerKind: string | null | undefined,
): DictionaryEvidence | null {
  if (viewerKind !== 'epub') return null;
  if (data.quote === null || data.quote === undefined) return null;
  if (!book) return null;

  return {
    quote: data.quote,
    sourceBookId: book.id,
    sourceBookTitle: book.title ?? null,
    sourceBookAuthor: book.author ?? null,
    sourceChapter: data.chapterTitle ?? null,
    sourceLocator: data.cfi ?? null,
  };
}

/**
 * Turn one selection into at most one write and return the feedback value.
 *
 * - `create` calls `add` with the surface-form token (Decision 14). Reachable
 *   only for a single token, so a multi-word selection can never create.
 * - `attach` with evidence calls `capture`, reporting `evidence-updated` when
 *   the entry already had evidence and `created` otherwise (REQ-DRE-008).
 * - `attach` with null evidence is resolve-only: the entry already exists, so
 *   nothing is written and the feedback is `already-in-dictionary` (Decision
 *   13). `add` is not idempotent, so writing there would surface a duplicate
 *   error instead of resolving.
 * - `no-op`, `no-match` and `ambiguous` never write.
 *
 * A rejected action maps to `error`; the store keeps its own state consistent
 * on failure, and this module touches no state itself.
 */
export async function captureFromSelection(
  selection: string,
  deps: DictionaryCaptureDeps,
): Promise<DictionaryCaptureFeedback> {
  const resolution = resolveSelectionTarget(selection, deps.entries);
  const evidence = deps.evidence ?? null;

  try {
    switch (resolution.kind) {
      case 'create':
        await deps.actions.add(resolution.token, { evidence });
        return 'created';
      case 'attach':
        if (evidence === null) return 'already-in-dictionary';
        await deps.actions.capture(resolution.entryId, evidence);
        return resolution.hadEvidence ? 'evidence-updated' : 'created';
      case 'ambiguous':
        return 'ambiguous';
      case 'no-op':
      case 'no-match':
        return 'no-match';
    }
  } catch {
    return 'error';
  }
}
