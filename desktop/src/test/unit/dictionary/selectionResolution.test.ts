/**
 * `selectionResolution` unit tests (tasks 2A.2 / 2A.4).
 *
 * Covers all five resolution kinds and the REQ-DRE-004 scenarios: the
 * single-word create/attach bisection, the multi-word exactly-one-match rule,
 * no-match, ambiguous and the no-op paths. `create` must carry the tokenizer's
 * surface-form token, never the normalized key.
 */
import { describe, expect, it } from 'vitest';
import {
  resolveSelectionTarget,
  type SelectionEntry,
} from '$lib/shared/dictionary/selectionResolution';

function entry(id: string, word: string, evidence: Partial<SelectionEntry> = {}): SelectionEntry {
  return { id, word, ...evidence };
}

describe('resolveSelectionTarget - no-op', () => {
  it('is a no-op for an empty or whitespace-only selection', () => {
    expect(resolveSelectionTarget('', [entry('a', 'abyss')])).toEqual({ kind: 'no-op' });
    expect(resolveSelectionTarget('   ', [entry('a', 'abyss')])).toEqual({ kind: 'no-op' });
  });

  it('is a no-op for a punctuation-only selection', () => {
    expect(resolveSelectionTarget('...', [entry('a', 'abyss')])).toEqual({ kind: 'no-op' });
    expect(resolveSelectionTarget(' \u2014 ', [])).toEqual({ kind: 'no-op' });
  });
});

describe('resolveSelectionTarget - single token', () => {
  it('creates with the surface-form token when nothing matches', () => {
    expect(resolveSelectionTarget('Abyss,', [])).toEqual({ kind: 'create', token: 'Abyss' });
    expect(resolveSelectionTarget('(Ephemeral)', [])).toEqual({
      kind: 'create',
      token: 'Ephemeral',
    });
  });

  it('preserves accents and casing in the created token', () => {
    expect(resolveSelectionTarget('caf\u00E9', [])).toEqual({
      kind: 'create',
      token: 'caf\u00E9',
    });
    expect(resolveSelectionTarget('\u00CEle', [])).toEqual({ kind: 'create', token: '\u00CEle' });
  });

  it('never creates the normalized key', () => {
    const resolution = resolveSelectionTarget('\u014Ckami,', []);
    expect(resolution).toEqual({ kind: 'create', token: '\u014Ckami' });
  });

  it('attaches an exact match and reports no evidence', () => {
    const resolution = resolveSelectionTarget('abyss', [entry('e1', 'abyss')]);
    expect(resolution).toEqual({
      kind: 'attach',
      token: 'abyss',
      entryId: 'e1',
      hadEvidence: false,
    });
  });

  it('matches case- and accent-insensitively', () => {
    expect(resolveSelectionTarget('CAFE', [entry('e1', 'Caf\u00E9')])).toMatchObject({
      kind: 'attach',
      entryId: 'e1',
    });
    expect(resolveSelectionTarget('Abyss,', [entry('e1', '  abyss ')])).toMatchObject({
      kind: 'attach',
      token: 'Abyss',
      entryId: 'e1',
    });
  });

  it('reports hadEvidence when the entry already carries a quote', () => {
    const resolution = resolveSelectionTarget('abyss', [
      entry('e1', 'abyss', { quote: 'The abyss stared back.' }),
    ]);
    expect(resolution).toEqual({
      kind: 'attach',
      token: 'abyss',
      entryId: 'e1',
      hadEvidence: true,
    });
  });

  it('reports hadEvidence for a reference-only populated entry', () => {
    const resolution = resolveSelectionTarget('abyss', [
      entry('e1', 'abyss', { sourceBookTitle: 'House of Leaves' }),
    ]);
    expect(resolution).toMatchObject({ kind: 'attach', hadEvidence: true });
  });

  it('ignores entries with an empty or missing word', () => {
    const entries = [entry('blank', '   '), { id: 'null' } as SelectionEntry];
    expect(resolveSelectionTarget('abyss', entries)).toEqual({ kind: 'create', token: 'abyss' });
  });
});

describe('resolveSelectionTarget - multi token', () => {
  it('attaches when exactly one distinct entry matches, creating no second entry', () => {
    const resolution = resolveSelectionTarget('the abyss', [entry('a', 'abyss')]);
    expect(resolution).toEqual({
      kind: 'attach',
      token: 'abyss',
      entryId: 'a',
      hadEvidence: false,
    });
  });

  it('reports hadEvidence for the single distinct match too', () => {
    const resolution = resolveSelectionTarget('the abyss', [
      entry('a', 'abyss', { quote: 'Down into the abyss.' }),
    ]);
    expect(resolution).toMatchObject({ kind: 'attach', entryId: 'a', hadEvidence: true });
  });

  it('is a no-match when nothing matches', () => {
    expect(resolveSelectionTarget('the abyss', [entry('a', 'caf\u00E9')])).toEqual({
      kind: 'no-match',
    });
  });

  it('is ambiguous with the matching entry ids when several entries match', () => {
    const resolution = resolveSelectionTarget('the abyss', [
      entry('a', 'the'),
      entry('b', 'abyss'),
    ]);
    expect(resolution).toEqual({ kind: 'ambiguous', entryIds: ['a', 'b'] });
  });

  it('attaches on whichever token matches and ignores the remaining tokens', () => {
    expect(resolveSelectionTarget('down into the abyss', [entry('a', 'abyss')])).toMatchObject({
      kind: 'attach',
      token: 'abyss',
      entryId: 'a',
    });
    expect(resolveSelectionTarget('abyss swallows', [entry('a', 'abyss')])).toMatchObject({
      kind: 'attach',
      token: 'abyss',
      entryId: 'a',
    });
  });

  it('collapses duplicate tokens back into the single-token branch', () => {
    // "abyss abyss" tokenizes to one token, so the single-word rule applies.
    expect(resolveSelectionTarget('abyss abyss', [])).toEqual({
      kind: 'create',
      token: 'abyss',
    });
    expect(resolveSelectionTarget('Abyss abyss,', [entry('a', 'abyss')])).toMatchObject({
      kind: 'attach',
      token: 'Abyss',
      entryId: 'a',
    });
  });
});
