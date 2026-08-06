import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import JSZip from 'jszip';
import { JSDOM } from 'jsdom';
import { setSpine, rangeToCFI, cfiToRange } from '$lib/features/reader/viewer-epub/cfiBridge';
import { locatorFromCfi, cfiFromLocator } from '$lib/shared/sync/LocatorCodec';

const FIXTURES_DIR = join(process.cwd(), 'src', 'lib', 'shared', 'sync', 'fixtures');
const FIXTURE_EPUB = join(FIXTURES_DIR, 'continuity-fixture.epub');
const GOLDEN_JSON = join(FIXTURES_DIR, 'continuity-fixture.golden.json');

interface Golden {
  chapterHref: string;
  paragraphText: string;
  expectedCfi: string;
  chapterChars: number;
  expectedProgression: number;
  tolerance: number;
  readingOrder: string[];
  note: string;
}

/** Walk all text nodes of an element in document order, returning them. */
function textNodes(doc: Document): Node[] {
  const out: Node[] = [];
  const walker = doc.createTreeWalker(doc.body, 0x4 /* NodeFilter.SHOW_TEXT */);
  let node = walker.nextNode();
  while (node) {
    out.push(node);
    node = walker.nextNode();
  }
  return out;
}

/** Character offset of `needle` within the concatenation of the chapter's text
 * nodes, plus the total chapter char count. Mirrors the fixture generator. */
function chapterGeometry(doc: Document, needle: string): { charOffset: number; chapterChars: number } {
  const nodes = textNodes(doc);
  const full = nodes.map((n) => (n as CharacterData).data).join('');
  const chapterChars = full.length;
  const charOffset = full.indexOf(needle);
  if (charOffset < 0) throw new Error(`needle '${needle}' not found in chapter text`);
  return { charOffset, chapterChars };
}

describe('continuity golden fixture — cross-engine (desktop side)', () => {
  let golden: Golden;
  let zip: JSZip;
  let chapterDoc: Document;

  beforeAll(async () => {
    golden = JSON.parse(readFileSync(GOLDEN_JSON, 'utf8')) as Golden;
    const buf = readFileSync(FIXTURE_EPUB);
    zip = await JSZip.loadAsync(buf);
    const chapterHtml = await zip.file(golden.chapterHref)?.async('string');
    if (!chapterHtml) throw new Error(`fixture missing chapter ${golden.chapterHref}`);
    chapterDoc = new JSDOM(chapterHtml).window.document;
  });

  it('fixes the reading order to all four spine chapters', () => {
    expect(golden.readingOrder).toHaveLength(4);
    for (const href of golden.readingOrder) {
      expect(zip.file(href), `missing ${href}`).toBeDefined();
    }
  });

  it('emits the golden precise CFI for the Brave paragraph via rangeToCFI', () => {
    setSpine(golden.readingOrder);
    const nodes = textNodes(chapterDoc);
    const found = nodes.find((n) => (n.nodeValue ?? '').includes(golden.paragraphText));
    expect(found, `golden paragraph text missing in ${golden.chapterHref}`).toBeTruthy();
    if (!found) throw new Error(`golden paragraph text missing in ${golden.chapterHref}`);
    const text = found.nodeValue ?? '';
    const start = text.indexOf(golden.paragraphText);
    const range = chapterDoc.createRange();
    range.setStart(found, start);
    range.setEnd(found, start + golden.paragraphText.length);
    const cfi = rangeToCFI(range, golden.chapterHref, chapterDoc);
    expect(cfi).toBe(golden.expectedCfi);
  });

  it('derives the golden progression from the chapter geometry (Brave paragraph)', () => {
    const { charOffset, chapterChars } = chapterGeometry(chapterDoc, golden.paragraphText);
    expect(charOffset).toBeGreaterThan(0);
    expect(chapterChars).toBe(golden.chapterChars);
    const loc = locatorFromCfi(golden.readingOrder, golden.expectedCfi, { charOffset, chapterChars });
    expect(loc).not.toBeNull();
    expect(loc!.href).toBe(golden.chapterHref);
    expect(loc!.locations.progression).toBeCloseTo(golden.expectedProgression, 3);
    expect(Math.abs((loc!.locations.progression ?? 0) - golden.expectedProgression)).toBeLessThanOrEqual(
      golden.tolerance,
    );
  });

  it('round-trips: precise CFI stored on the locator survives serialize/deserialize', () => {
    const { charOffset, chapterChars } = chapterGeometry(chapterDoc, golden.paragraphText);
    const loc = locatorFromCfi(golden.readingOrder, golden.expectedCfi, { charOffset, chapterChars });
    expect(cfiFromLocator(loc!)).toBe(golden.expectedCfi);
  });

  it('re-anchors: cfiToRange on the golden CFI resolves back to the golden paragraph', () => {
    setSpine(golden.readingOrder);
    const range = cfiToRange(golden.expectedCfi, golden.chapterHref, chapterDoc);
    expect(range).not.toBeNull();
    const text = range!.startContainer.nodeValue ?? '';
    expect(text.slice(range!.startOffset, range!.startOffset + golden.paragraphText.length)).toBe(
      golden.paragraphText,
    );
  });
});
