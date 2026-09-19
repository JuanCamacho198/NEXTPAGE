/**
 * `dictionaryKey` unit tests (tasks 2A.1 / 2A.3).
 *
 * The conformance table is the authoritative list shared with Rust
 * (`normalization_vectors_match_shared_contract`, task 1D.3) and Kotlin
 * (`DictionaryNormalizerTest`, task 5B.1): same inputs, same expected keys
 * (REQ-DSI-004). Transcribe it verbatim - do not "clean up" a vector.
 *
 * Non-ASCII inputs are written as escapes so the code points are unambiguous.
 */
import { describe, expect, it } from 'vitest';
import {
  MAX_QUOTE_LENGTH,
  normalizeDictionaryKey,
  tokenizeSelection,
} from '$lib/shared/dictionary/dictionaryKey';

const SHARED_NORMALIZATION_VECTORS: [string, string][] = [
  ['', ''],
  ['   ', ''],
  ['  Serendipity  ', 'serendipity'],
  ['caf\u00E9', 'cafe'], // café (U+00E9 precomposed)
  ['CAF\u00C9', 'cafe'],
  ['  Caf\u00E9  ', 'cafe'],
  ['\u00D1and\u00FA', 'nandu'], // Ñandú
  ['\u014Ckami', 'okami'], // Ōkami (U+014D)
  ['\u0158eka', 'reka'], // Řeka (U+0159)
  ['\u00CEle', 'ile'], // Île (U+00CE)
  ['\u0100ris', 'aris'], // Āris (U+0100)
  ['\u0218tefan', 'stefan'], // Ștefan (U+0218)
  ['\u0130stanbul', 'istanbul'], // İstanbul (U+0130)
  ['Abyss,', 'abyss,'], // punctuation is preserved by the normalizer
  ['(Ephemeral)', '(ephemeral)'],
  ['\u00A1Hola!', '\u00A1hola!'], // ¡Hola!
  ["Ephemeral's", "ephemeral's"],
];

describe('normalizeDictionaryKey - shared conformance vectors', () => {
  it('declares exactly the 17 vectors pinned by the Rust contract', () => {
    expect(SHARED_NORMALIZATION_VECTORS).toHaveLength(17);
  });

  it.each(SHARED_NORMALIZATION_VECTORS)('normalizes %j to %j', (input, expected) => {
    expect(normalizeDictionaryKey(input)).toBe(expected);
  });
});

describe('normalizeDictionaryKey - pinned rules', () => {
  it('preserves punctuation instead of stripping it', () => {
    expect(normalizeDictionaryKey('Ephemeral.')).toBe('ephemeral.');
    expect(normalizeDictionaryKey('"word"')).toBe('"word"');
    expect(normalizeDictionaryKey('(Ephemeral)')).toBe('(ephemeral)');
    expect(normalizeDictionaryKey('\u00A1Hola!')).toBe('\u00A1hola!');
  });

  it('lowercases locale-independently (Turkish dotted capital I)', () => {
    expect(normalizeDictionaryKey('I')).toBe('i');
    expect(normalizeDictionaryKey('\u0130')).toBe('i');
    expect(normalizeDictionaryKey('\u0130stanbul')).toBe('istanbul');
  });

  it('strips combining marks only: stroke letters survive unchanged', () => {
    expect(normalizeDictionaryKey('\u0142')).toBe('\u0142'); // ł
    expect(normalizeDictionaryKey('\u0111')).toBe('\u0111'); // đ
    expect(normalizeDictionaryKey('\u00F8')).toBe('\u00F8'); // ø
    expect(normalizeDictionaryKey('Sm\u00F8rrebr\u00F8d')).toBe('sm\u00F8rrebr\u00F8d');
    // Composed accents still decompose around an untouched stroke letter.
    expect(normalizeDictionaryKey('\u0141\u00F3d\u017A')).toBe('\u0142odz');
  });

  it('is idempotent', () => {
    const once = normalizeDictionaryKey('  \u014Ckami,  ');
    expect(normalizeDictionaryKey(once)).toBe(once);
  });
});

describe('MAX_QUOTE_LENGTH', () => {
  it('is 2000 characters', () => {
    expect(MAX_QUOTE_LENGTH).toBe(2000);
  });
});

describe('tokenizeSelection', () => {
  it('returns no tokens for empty or whitespace-only input', () => {
    expect(tokenizeSelection('')).toEqual([]);
    expect(tokenizeSelection('   ')).toEqual([]);
    expect(tokenizeSelection('\n\t ')).toEqual([]);
  });

  it('returns no tokens for punctuation-only selections', () => {
    expect(tokenizeSelection('...')).toEqual([]);
    expect(tokenizeSelection('---')).toEqual([]);
    expect(tokenizeSelection('\u2014')).toEqual([]); // em dash
    expect(tokenizeSelection(' \u00BF? ')).toEqual([]);
  });

  it('splits on whitespace and trims each token', () => {
    expect(tokenizeSelection('  the   abyss  ')).toEqual(['the', 'abyss']);
  });

  it('strips surrounding punctuation while preserving the surface form', () => {
    expect(tokenizeSelection('Abyss,')).toEqual(['Abyss']);
    expect(tokenizeSelection('(Ephemeral)')).toEqual(['Ephemeral']);
    expect(tokenizeSelection('\u00A1Hola!')).toEqual(['Hola']);
    expect(tokenizeSelection('\u00ABquoted\u00BB')).toEqual(['quoted']);
  });

  it('preserves casing, accents and internal punctuation', () => {
    expect(tokenizeSelection('caf\u00E9')).toEqual(['caf\u00E9']);
    expect(tokenizeSelection("Ephemeral's")).toEqual(["Ephemeral's"]);
    expect(tokenizeSelection('well-known')).toEqual(['well-known']);
  });

  it('collapses duplicate tokens, keeping the first surface form', () => {
    expect(tokenizeSelection('abyss abyss')).toEqual(['abyss']);
    expect(tokenizeSelection('Abyss abyss,')).toEqual(['Abyss']);
    expect(tokenizeSelection('caf\u00E9 CAF\u00C9 caf\u00E9')).toEqual(['caf\u00E9']);
    expect(tokenizeSelection('to be or not to be')).toEqual(['to', 'be', 'or', 'not']);
  });

  it('keeps tokens that only become empty after normalization out of the list', () => {
    expect(tokenizeSelection('\u0301')).toEqual([]);
  });
});
