/**
 * `captureFromSelection` unit tests (tasks 2B.1 / 2B.2).
 *
 * Covers `buildEvidence` (EPUB vs PDF, no quote, chapter title, book snapshot)
 * and every `captureFromSelection` branch with its feedback value. The
 * resolve-only branches assert that neither injected action is called: this
 * module owns no write path and no outbox dependency, so "no action called" is
 * exactly "no write and no outbox enqueue attempted".
 */
import { describe, expect, it, vi } from 'vitest';
import {
  buildEvidence,
  captureFromSelection,
  type DictionaryCaptureBook,
  type DictionaryEvidence,
} from '$lib/shared/dictionary/captureFromSelection';
import type { SelectionEntry } from '$lib/shared/dictionary/selectionResolution';
import type { DictionaryWordDto } from '$lib/shared/types/book';

const BOOK: DictionaryCaptureBook = {
  id: 'book-1',
  title: 'The Abyss',
  author: 'Ada Lovelace',
};

function evidence(overrides: Partial<DictionaryEvidence> = {}): DictionaryEvidence {
  return {
    quote: 'the abyss was quiet',
    sourceBookId: 'book-1',
    sourceBookTitle: 'The Abyss',
    sourceBookAuthor: 'Ada Lovelace',
    sourceChapter: 'Chapter 1',
    sourceLocator: 'epubcfi(/6/4!/4/2/2)',
    ...overrides,
  };
}

function entry(id: string, word: string, extra: Partial<SelectionEntry> = {}): SelectionEntry {
  return { id, word, ...extra };
}

function dto(id: string, word: string): DictionaryWordDto {
  return { id, word, createdAt: '2026-09-19T00:00:00.000Z' };
}

function makeActions(entries: readonly SelectionEntry[] = []) {
  return {
    words: entries as readonly DictionaryWordDto[],
    add: vi.fn(async (word: string, _opts?: { evidence?: DictionaryEvidence | null }) =>
      dto('created-id', word),
    ),
    capture: vi.fn(async (entryId: string, _evidence: DictionaryEvidence) =>
      dto(entryId, 'attached'),
    ),
  };
}

function expectNoWrites(actions: ReturnType<typeof makeActions>): void {
  expect(actions.add).not.toHaveBeenCalled();
  expect(actions.capture).not.toHaveBeenCalled();
}

describe('buildEvidence', () => {
  it('assembles all six fields for an EPUB selection', () => {
    expect(
      buildEvidence(
        { quote: 'the abyss was quiet', chapterTitle: 'Chapter 1', cfi: 'epubcfi(/6/4!/4/2/2)' },
        BOOK,
        'epub',
      ),
    ).toEqual({
      quote: 'the abyss was quiet',
      sourceBookId: 'book-1',
      sourceBookTitle: 'The Abyss',
      sourceBookAuthor: 'Ada Lovelace',
      sourceChapter: 'Chapter 1',
      sourceLocator: 'epubcfi(/6/4!/4/2/2)',
    });
  });

  it('leaves chapter and locator null when the selection carries neither', () => {
    expect(buildEvidence({ quote: 'a paragraph' }, BOOK, 'epub')).toEqual({
      quote: 'a paragraph',
      sourceBookId: 'book-1',
      sourceBookTitle: 'The Abyss',
      sourceBookAuthor: 'Ada Lovelace',
      sourceChapter: null,
      sourceLocator: null,
    });
  });

  it('copies title and author as a snapshot that later metadata edits cannot rewrite', () => {
    const book = { id: 'book-1', title: 'First', author: 'One' };
    const captured = buildEvidence({ quote: 'a paragraph' }, book, 'epub');

    book.title = 'Renamed';
    book.author = 'Someone Else';

    expect(captured?.sourceBookTitle).toBe('First');
    expect(captured?.sourceBookAuthor).toBe('One');
  });

  it('returns null for the PDF viewer', () => {
    expect(buildEvidence({ quote: 'a paragraph' }, BOOK, 'pdf')).toBeNull();
  });

  it('returns null for an absent or unknown viewer kind', () => {
    expect(buildEvidence({ quote: 'a paragraph' }, BOOK, null)).toBeNull();
    expect(buildEvidence({ quote: 'a paragraph' }, BOOK, undefined)).toBeNull();
    expect(buildEvidence({ quote: 'a paragraph' }, BOOK, 'mobi')).toBeNull();
  });

  it('returns null when the selection has no quote (no block ancestor)', () => {
    expect(buildEvidence({ quote: null }, BOOK, 'epub')).toBeNull();
    expect(buildEvidence({ quote: undefined }, BOOK, 'epub')).toBeNull();
    expect(buildEvidence({}, BOOK, 'epub')).toBeNull();
  });

  it('returns null when there is no active book to reference', () => {
    expect(buildEvidence({ quote: 'a paragraph' }, null, 'epub')).toBeNull();
    expect(buildEvidence({ quote: 'a paragraph' }, undefined, 'epub')).toBeNull();
  });
});

describe('captureFromSelection - create', () => {
  it('creates the surface-form token with the evidence attached', async () => {
    const actions = makeActions();
    const captured = evidence();

    const feedback = await captureFromSelection('Abyss,', {
      actions,
      entries: [],
      evidence: captured,
    });

    expect(feedback).toBe('created');
    expect(actions.add).toHaveBeenCalledTimes(1);
    expect(actions.add).toHaveBeenCalledWith('Abyss', { evidence: captured });
    expect(actions.capture).not.toHaveBeenCalled();
  });

  it('preserves accents and casing in the created word', async () => {
    const actions = makeActions();

    await captureFromSelection('caf\u00E9', { actions, entries: [], evidence: evidence() });

    expect(actions.add).toHaveBeenCalledWith('caf\u00E9', { evidence: expect.anything() });
  });

  it('creates with null evidence when the capture has none (PDF)', async () => {
    const actions = makeActions();

    const feedback = await captureFromSelection('Abyss', {
      actions,
      entries: [],
      evidence: null,
    });

    expect(feedback).toBe('created');
    expect(actions.add).toHaveBeenCalledWith('Abyss', { evidence: null });
    expect(actions.capture).not.toHaveBeenCalled();
  });
});

describe('captureFromSelection - attach', () => {
  it('captures onto an existing entry and reports evidence-updated', async () => {
    const existing = entry('a', 'abyss', { quote: 'an older quote' });
    const actions = makeActions([existing]);
    const captured = evidence();

    const feedback = await captureFromSelection('abyss', {
      actions,
      entries: [existing],
      evidence: captured,
    });

    expect(feedback).toBe('evidence-updated');
    expect(actions.capture).toHaveBeenCalledTimes(1);
    expect(actions.capture).toHaveBeenCalledWith('a', captured);
    expect(actions.add).not.toHaveBeenCalled();
  });

  it('reports created when the matched entry had no evidence', async () => {
    const existing = entry('a', 'abyss');
    const actions = makeActions([existing]);

    const feedback = await captureFromSelection('abyss', {
      actions,
      entries: [existing],
      evidence: evidence(),
    });

    expect(feedback).toBe('created');
    expect(actions.capture).toHaveBeenCalledTimes(1);
    expect(actions.add).not.toHaveBeenCalled();
  });

  it('attaches a multi-word selection with exactly one distinct match', async () => {
    const existing = entry('a', 'abyss', { quote: 'an older quote' });
    const actions = makeActions([existing]);

    const feedback = await captureFromSelection('the abyss', {
      actions,
      entries: [existing],
      evidence: evidence(),
    });

    expect(feedback).toBe('evidence-updated');
    expect(actions.capture).toHaveBeenCalledWith('a', expect.anything());
    expect(actions.add).not.toHaveBeenCalled();
  });

  it('is resolve-only when the evidence is null: no write, already-in-dictionary', async () => {
    const existing = entry('a', 'abyss', { quote: 'an older quote' });
    const actions = makeActions([existing]);

    const feedback = await captureFromSelection('abyss', {
      actions,
      entries: [existing],
      evidence: null,
    });

    expect(feedback).toBe('already-in-dictionary');
    expectNoWrites(actions);
  });

  it('is resolve-only regardless of whether the matched entry already had evidence', async () => {
    const existing = entry('a', 'abyss');
    const actions = makeActions([existing]);

    const feedback = await captureFromSelection('abyss', {
      actions,
      entries: [existing],
      evidence: null,
    });

    expect(feedback).toBe('already-in-dictionary');
    expectNoWrites(actions);
  });
});

describe('captureFromSelection - no-write branches', () => {
  it('writes nothing for a multi-word selection with no match', async () => {
    const actions = makeActions([entry('a', 'abyss')]);

    const feedback = await captureFromSelection('the void', {
      actions,
      entries: [entry('a', 'abyss')],
      evidence: evidence(),
    });

    expect(feedback).toBe('no-match');
    expectNoWrites(actions);
  });

  it('writes nothing for an ambiguous multi-word selection', async () => {
    const actions = makeActions([entry('a', 'abyss'), entry('v', 'void')]);

    const feedback = await captureFromSelection('abyss void', {
      actions,
      entries: [entry('a', 'abyss'), entry('v', 'void')],
      evidence: evidence(),
    });

    expect(feedback).toBe('ambiguous');
    expectNoWrites(actions);
  });

  it('writes nothing for an empty, whitespace-only or punctuation-only selection', async () => {
    for (const selection of ['', '   ', '...']) {
      const actions = makeActions([entry('a', 'abyss')]);

      const feedback = await captureFromSelection(selection, {
        actions,
        entries: [entry('a', 'abyss')],
        evidence: evidence(),
      });

      expect(feedback).toBe('no-match');
      expectNoWrites(actions);
    }
  });
});

describe('captureFromSelection - errors', () => {
  it('maps a rejected add to error and leaves the injected words untouched', async () => {
    const actions = makeActions();
    actions.add.mockRejectedValueOnce(new Error('duplicate'));
    const before = [...actions.words];

    const feedback = await captureFromSelection('Abyss', {
      actions,
      entries: [],
      evidence: evidence(),
    });

    expect(feedback).toBe('error');
    expect(actions.capture).not.toHaveBeenCalled();
    expect(actions.words).toEqual(before);
  });

  it('maps a rejected capture to error and leaves the injected words untouched', async () => {
    const existing = entry('a', 'abyss', { quote: 'an older quote' });
    const actions = makeActions([existing]);
    actions.capture.mockRejectedValueOnce(new Error('offline'));
    const before = [...actions.words];

    const feedback = await captureFromSelection('abyss', {
      actions,
      entries: [existing],
      evidence: evidence(),
    });

    expect(feedback).toBe('error');
    expect(actions.add).not.toHaveBeenCalled();
    expect(actions.words).toEqual(before);
  });
});
