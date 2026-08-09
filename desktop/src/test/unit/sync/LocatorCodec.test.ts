/**
 * Unit tests for {@link $lib/shared/sync/LocatorCodec} — the canonical
 * Readium Locator JSON codec (cross-device continuity T1).
 *
 * Canonical shape (single string stored in `locator_json`):
 *   {"href":"chapter/001.xhtml","type":"application/xhtml+xml",
 *    "locations":{"progression":0.37,"position":6,"fragment":"epubcfi(...)"}}
 *
 * STRICT TDD surface:
 *   - `locatorFromCfi(readingOrder, cfi, chapter?)`: CFI spine index → href,
 *     within-chapter `progression` from a caller-supplied char offset, raw
 *     precise CFI as `locations.fragment`.
 *   - `cfiFromLocator(loc)`: round-trip back to the fragment CFI.
 *   - `deriveLocatorForChapter(readingOrder, href, chapter?)`: chapter-anchored
 *     locator without a precise CFI.
 *   - `locatorToJson` / canonical serialisation.
 *
 * DOM-char-offset derivation is left to the caller (it needs the rendered
 * chapter DOM in the iframe); this codec is a pure serializer + resolver.
 */
import { describe, expect, it } from 'vitest';
import {
  locatorFromCfi,
  cfiFromLocator,
  locatorToJson,
  locatorFromJson,
  deriveLocatorForChapter,
  charOffsetToProgression,
} from '$lib/shared/sync/LocatorCodec';

const CHAPTER_HREF = 'OEBPS/Text/chapter3.xhtml';
const READING_ORDER = [
  'OEBPS/Text/chapter1.xhtml',
  'OEBPS/Text/chapter2.xhtml',
  CHAPTER_HREF,
  'OEBPS/Text/chapter4.xhtml',
];

// A precise CFI anchored in spine item 3. spineIndex is parsed to resolve href.
const PRECISE_CFI = 'epubcfi(/6/3!/8/1:2,/8/1:8)';

describe('LocatorCodec', () => {
  describe('locatorFromCfi', () => {
    it('resolves spineIndex 3 to the third chapter href', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, null);
      expect(loc).not.toBeNull();
      expect(loc!.href).toBe(CHAPTER_HREF);
    });

    it('fills type application/xhtml+xml', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, null);
      expect(loc).not.toBeNull();
      expect(loc!.type).toBe('application/xhtml+xml');
    });

    it('stores the raw precise CFI in locations.fragment for round-trip', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, null);
      expect(loc).not.toBeNull();
      expect(loc!.locations).not.toBeNull();
      expect(loc!.locations!.fragment).toBe(PRECISE_CFI);
    });

    it('computes within-chapter progression = charOffset / totalChars when the chapter is provided', () => {
      // charOffset 50, total 200 → 0.25.
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, {
        chapterChars: 200,
        charOffset: 50,
      });
      expect(loc).not.toBeNull();
      expect(loc!.locations!.progression).toBeCloseTo(0.25, 6);
    });

    it('clamps progression into [0, 1]', () => {
      const over = locatorFromCfi(READING_ORDER, PRECISE_CFI, {
        chapterChars: 100,
        charOffset: 950,
      });
      expect(over).not.toBeNull();
      expect(over!.locations!.progression).toBe(1);

      const under = locatorFromCfi(READING_ORDER, PRECISE_CFI, {
        chapterChars: 100,
        charOffset: -3,
      });
      expect(under).not.toBeNull();
      expect(under!.locations!.progression).toBe(0);
    });

    it('returns null for a CFI with an out-of-range spine index', () => {
      const loc = locatorFromCfi(READING_ORDER, 'epubcfi(/6/99!/8/1:0,/8/1:1)', null);
      expect(loc).toBeNull();
    });

    it('returns null for a malformed CFI', () => {
      expect(locatorFromCfi(READING_ORDER, '', null)).toBeNull();
      expect(locatorFromCfi(READING_ORDER, 'not-a-cfi', null)).toBeNull();
      expect(locatorFromCfi(READING_ORDER, null as unknown as string, null)).toBeNull();
    });
  });

  describe('cfiFromLocator round-trip', () => {
    it('returns the stored fragment CFI', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, null);
      expect(loc).not.toBeNull();
      expect(cfiFromLocator(loc!)).toBe(PRECISE_CFI);
    });

    it('returns null when no fragment exists', () => {
      const loc = deriveLocatorForChapter(READING_ORDER, CHAPTER_HREF);
      expect(loc).not.toBeNull();
      expect(cfiFromLocator(loc!)).toBeNull();
    });
  });

  describe('deriveLocatorForChapter', () => {
    it('produces a chapter-anchored locator for the chapter href', () => {
      const loc = deriveLocatorForChapter(READING_ORDER, CHAPTER_HREF);
      expect(loc).not.toBeNull();
      expect(loc!.href).toBe(CHAPTER_HREF);
      expect(loc!.type).toBe('application/xhtml+xml');
    });

    it('sets progression 0.0 (chapter start) when no precise position is available', () => {
      const loc = deriveLocatorForChapter(READING_ORDER, CHAPTER_HREF);
      expect(loc!.locations!.progression).toBe(0);
      expect(loc!.locations!.fragment).toBeUndefined();
    });

    it('returns null for an href not present in the reading order', () => {
      expect(deriveLocatorForChapter(READING_ORDER, 'OEBPS/nope.xhtml')).toBeNull();
    });
  });

  describe('serialisation', () => {
    it('locatorToJson produces the canonical Readium shape', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, {
        chapterChars: 200,
        charOffset: 74,
      })!;
      const json = locatorToJson(loc);
      const parsed: Record<string, unknown> = JSON.parse(json);
      expect(parsed.href).toBe(CHAPTER_HREF);
      expect(parsed.type).toBe('application/xhtml+xml');
      const locations = parsed.locations as Record<string, unknown>;
      expect(locations.progression).toBe(0.37);
      expect(locations.fragment).toBe(PRECISE_CFI);
    });

    it('round-trips locatorToJson → locatorFromJson', () => {
      const loc = locatorFromCfi(READING_ORDER, PRECISE_CFI, {
        chapterChars: 200,
        charOffset: 120,
      })!;
      const restored = locatorFromJson(locatorToJson(loc));
      expect(restored).not.toBeNull();
      expect(restored!.href).toBe(loc.href);
      expect(restored!.locations!.progression).toBeCloseTo(loc.locations!.progression!, 6);
      expect(restored!.locations!.fragment).toBe(PRECISE_CFI);
    });

    it('locatorFromJson returns null on invalid JSON', () => {
      expect(locatorFromJson('')).toBeNull();
      expect(locatorFromJson('{not json')).toBeNull();
    });
  });

  describe('charOffsetToProgression (pure geometry)', () => {
    it('divides offset by total', () => {
      expect(charOffsetToProgression(50, 200)).toBeCloseTo(0.25, 6);
    });
    it('returns null for non-positive total', () => {
      expect(charOffsetToProgression(10, 0)).toBeNull();
      expect(charOffsetToProgression(10, -5)).toBeNull();
    });
    it('clamps into [0,1]', () => {
      expect(charOffsetToProgression(0, 100)).toBe(0);
      expect(charOffsetToProgression(100, 100)).toBe(1);
      expect(charOffsetToProgression(250, 100)).toBe(1);
    });
  });
});