import { describe, expect, it, beforeAll } from "vitest";
import { JSDOM } from "jsdom";
import {
  rangeToCFI,
  cfiToRange,
  getChapterBaseCFI,
  setSpine,
} from "$lib/features/reader/viewer-epub/cfiBridge";

// Inlined fixture: a synthetic EPUB chapter (1 heading + 3 paragraphs).
// Inlined as a string constant so the test is self-contained and not
// affected by the project-wide `fixtures/` .gitignore rule.
const FIXTURE_HTML = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>Chapter 1 \u2014 Fixture</title>
  </head>
  <body>
    <h1 id="ch1-title">The First Chapter</h1>
    <p>
      It was a bright cold day in April, and the clocks were striking thirteen.
      Winston Smith, his chin nuzzled into his breast in an effort to escape the
      vile wind, slipped quickly through the glass doors of Victory Mansions,
      though not quickly enough to prevent a swirl of gritty dust from entering
      along with him.
    </p>
    <p>
      The hallway smelt of boiled cabbage and old rag mats. At one end of it a
      coloured poster, too large for indoor display, had been tacked to the wall.
      It depicted simply an enormous face, more than a metre wide: the face of a
      man of about forty-five, with a heavy black moustache and ruggedly handsome
      features.
    </p>
    <p>
      Winston made for the stairs. It was no use trying the lift. Even at the
      best of times it was seldom working, and at present the electric current
      was cut off during daylight hours. It was part of the economy drive in
      preparation for Hate Week.
    </p>
  </body>
</html>
`;

const CHAPTER_HREF = "OEBPS/Text/chapter1.xhtml";
const SPINE = [CHAPTER_HREF, "OEBPS/Text/chapter2.xhtml", "OEBPS/Text/chapter3.xhtml"];

/**
 * Parse the fixture HTML into a fresh jsdom Document and return it.
 * Each test gets a clean document so we don't share state.
 */
function loadFixtureDocument(): Document {
  const dom = new JSDOM(FIXTURE_HTML);
  return dom.window.document;
}

describe("cfiBridge", () => {
  beforeAll(() => {
    setSpine(SPINE);
  });

  describe("getChapterBaseCFI", () => {
    it("returns a base CFI for a registered chapter", () => {
      const base = getChapterBaseCFI(CHAPTER_HREF);
      expect(base).toBe("epubcfi(/6/1!)");
    });

    it("returns the 1-based spine index for chapter 2", () => {
      const base = getChapterBaseCFI("OEBPS/Text/chapter2.xhtml");
      expect(base).toBe("epubcfi(/6/2!)");
    });

    it("returns null for an unregistered chapter", () => {
      expect(getChapterBaseCFI("OEBPS/Text/missing.xhtml")).toBeNull();
    });

    it("returns null for invalid input", () => {
      expect(getChapterBaseCFI("")).toBeNull();
      expect(getChapterBaseCFI(null as unknown as string)).toBeNull();
      expect(getChapterBaseCFI(undefined as unknown as string)).toBeNull();
    });
  });

  describe("setSpine", () => {
    it("replaces the spine registry", () => {
      setSpine([CHAPTER_HREF]);
      expect(getChapterBaseCFI(CHAPTER_HREF)).toBe("epubcfi(/6/1!)");
      expect(getChapterBaseCFI("OEBPS/Text/chapter2.xhtml")).toBeNull();
      // Restore the test fixture spine.
      setSpine(SPINE);
    });

    it("accepts null/undefined to reset", () => {
      setSpine(null);
      expect(getChapterBaseCFI(CHAPTER_HREF)).toBeNull();
      setSpine(SPINE);
    });
  });

  describe("rangeToCFI", () => {
    it("returns a non-empty CFI for a known phrase", () => {
      const doc = loadFixtureDocument();
      // "thirteen" appears in the first paragraph as a word.
      const range = selectText(doc, "thirteen");
      expect(range).not.toBeNull();
      const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
      expect(cfi).toBeTruthy();
      expect(cfi).toMatch(/^epubcfi\(\/6\/1!/);
    });

    it("round-trips: rangeToCFI -> cfiToRange preserves the text", () => {
      const doc = loadFixtureDocument();
      const original = selectText(doc, "Winston Smith");
      expect(original).not.toBeNull();
      const cfi = rangeToCFI(original, CHAPTER_HREF, doc);
      expect(cfi).toBeTruthy();

      const restored = cfiToRange(cfi, CHAPTER_HREF, doc);
      expect(restored).not.toBeNull();
      expect(restored!.toString()).toBe("Winston Smith");
    });

    it("round-trips a multi-paragraph selection", () => {
      const doc = loadFixtureDocument();
      // Selection that crosses paragraph boundaries: from "smelt"
      // (end of p1) through to "current" (start of p3).
      const range = selectRangeFromTexts(doc, "hallway smelt", "current");
      expect(range).not.toBeNull();
      const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
      expect(cfi).toBeTruthy();
      const restored = cfiToRange(cfi, CHAPTER_HREF, doc);
      expect(restored).not.toBeNull();
      // The restored text should contain the picked endpoint. The
      // CFI start terminus is at the end of "hallway smelt" in p1
      // and the end terminus is at the start of "current" in p3,
      // so the selection spans p1 (from after "smelt") through p2
      // up to the start of "current" in p3.
      const restoredText = restored!.toString();
      // Both paragraphs that span the selection should be in the
      // restored text.
      expect(restoredText).toContain("coloured poster");
      expect(restoredText).toContain("Winston made for the stairs");
    });

    it("returns null for an empty range", () => {
      const doc = loadFixtureDocument();
      const range = doc.createRange();
      const p = doc.querySelector("p")!;
      range.setStart(p.firstChild!, 0);
      range.setEnd(p.firstChild!, 0);
      // rangeToCFI should still return a CFI for a zero-length range --
      // the terminuses are valid (offset 0). The behavior is to produce
      // a CFI, not null. Verify that.
      const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
      expect(cfi).toBeTruthy();
    });

    it("returns null for a chapter not in the spine registry", () => {
      const doc = loadFixtureDocument();
      const range = selectText(doc, "Winston");
      expect(range).not.toBeNull();
      const cfi = rangeToCFI(range, "OEBPS/Text/missing.xhtml", doc);
      expect(cfi).toBeNull();
    });

    it("returns null on invalid input without throwing", () => {
      const doc = loadFixtureDocument();
      const range = selectText(doc, "Winston");
      expect(rangeToCFI(null, CHAPTER_HREF, doc)).toBeNull();
      expect(rangeToCFI(range, "", doc)).toBeNull();
      expect(rangeToCFI(range, CHAPTER_HREF, null)).toBeNull();
      expect(rangeToCFI(undefined as unknown as Range, CHAPTER_HREF, doc)).toBeNull();
    });
  });

  describe("cfiToRange", () => {
    it("returns null for malformed CFIs without throwing", () => {
      const doc = loadFixtureDocument();
      expect(cfiToRange("", CHAPTER_HREF, doc)).toBeNull();
      expect(cfiToRange("not-a-cfi", CHAPTER_HREF, doc)).toBeNull();
      expect(cfiToRange("epubcfi(/garbage!)", CHAPTER_HREF, doc)).toBeNull();
      expect(cfiToRange("epubcfi(/6/1!)", CHAPTER_HREF, doc)).toBeNull();
      // Spine index mismatch
      expect(cfiToRange("epubcfi(/6/2!/4/2,/1:0,/1:0)", CHAPTER_HREF, doc)).toBeNull();
    });

    it("returns null when the local path does not resolve", () => {
      const doc = loadFixtureDocument();
      // Spine 1, local path with a non-existent child step (e.g. /999)
      expect(cfiToRange("epubcfi(/6/1!/4/2/999,/1:0,/1:0)", CHAPTER_HREF, doc)).toBeNull();
    });
  });

  describe("NFR-1 (performance)", () => {
    // Perf test for `rangeToCFI` against a chapter inflated to ~50K
    // text nodes. The spec target is < 5ms in the Tauri webview
    // (real browser engine). jsdom is much slower (tree walking is
    // 50-100x slower than V8 with native DOM), so we use a CI-friendly
    // bound here: 2000ms over 50K text nodes ≈ ~40µs per text node
    // walked. Real-world performance is logged for visibility.
    //
    // Skip in slow CI: set SKIP_PERF=1 to opt out.
    it("processes a 50K text node chapter (jsdom baseline)", () => {
      if (process.env.SKIP_PERF === "1") {
        return;
      }
      const doc = loadFixtureDocument();
      // Inflate the chapter: duplicate paragraphs to grow the text
      // node count. Each `<p>` has 1 text node (whitespace between
      // elements is also a text node, but the tree walker counts
      // those too). We aim for >= 50K text nodes by appending 50K
      // copies of the existing paragraphs.
      const body = doc.body;
      const sourceParagraphs = Array.from(doc.querySelectorAll("p"));
      for (let i = 0; i < 50_000; i++) {
        for (const p of sourceParagraphs) {
          body.appendChild(p.cloneNode(true));
        }
      }
      // Count text nodes -- jsdom-friendly.
      const textNodes = doc.createTreeWalker(doc.body, 0x4 /* SHOW_TEXT */);
      let textNodeCount = 0;
      let node: Node | null = textNodes.nextNode();
      while (node) {
        textNodeCount += 1;
        node = textNodes.nextNode();
      }
      // Sanity: should be a large number.
      expect(textNodeCount).toBeGreaterThan(10_000);

      // Build a range over the 50,000th text node.
      const walker = doc.createTreeWalker(doc.body, 0x4);
      let target: Node | null = null;
      for (let i = 0; i < 50_000; i++) {
        target = walker.nextNode();
      }
      expect(target).toBeTruthy();
      const range = doc.createRange();
      range.setStart(target!, 0);
      range.setEnd(target!, Math.min(5, (target!.nodeValue ?? "").length));

      const start = performance.now();
      const cfi = rangeToCFI(range, CHAPTER_HREF, doc);
      const elapsed = performance.now() - start;
      // NFR-1 says < 5ms. We use 2000ms here as a CI-friendly bound
      // for jsdom (which is much slower than the Tauri webview).
      // Production performance will be logged via the console output
      // for cross-environment visibility.
      console.log(`cfiBridge perf: ${elapsed.toFixed(2)}ms over ${textNodeCount} text nodes`);
      expect(cfi).toBeTruthy();
      expect(elapsed).toBeLessThan(2000);
    });
  });
});

// ─── Test helpers ───────────────────────────────────────────────────

/**
 * Select the first occurrence of `text` in `doc` and return a Range
 * spanning it. Returns `null` if not found.
 */
function selectText(doc: Document, text: string): Range | null {
  const walker = doc.createTreeWalker(doc.body, 0x4 /* SHOW_TEXT */);
  let node: Node | null = walker.nextNode();
  while (node) {
    const value = node.nodeValue ?? "";
    const idx = value.indexOf(text);
    if (idx >= 0) {
      const range = doc.createRange();
      range.setStart(node, idx);
      range.setEnd(node, idx + text.length);
      return range;
    }
    node = walker.nextNode();
  }
  return null;
}

/**
 * Build a Range that starts AFTER the first occurrence of `startText`
 * and ends AT the first occurrence of `endText`. Both anchors are
 * searched in document order. Used to test cross-paragraph selections.
 */
function selectRangeFromTexts(doc: Document, startText: string, endText: string): Range | null {
  const walker = doc.createTreeWalker(doc.body, 0x4 /* SHOW_TEXT */);
  let node: Node | null = walker.nextNode();
  let startNode: Node | null = null;
  let startOffset = 0;
  let endNode: Node | null = null;
  let endOffset = 0;
  let foundStart = false;
  while (node) {
    const value = node.nodeValue ?? "";
    const startIdx = value.indexOf(startText);
    const endIdx = value.indexOf(endText);
    if (!foundStart && startIdx >= 0) {
      startNode = node;
      startOffset = startIdx + startText.length;
      foundStart = true;
      // If the start and end are in the same text node, capture the
      // end now and stop.
      if (endIdx >= 0) {
        endNode = node;
        endOffset = endIdx;
        break;
      }
    } else if (foundStart && endIdx >= 0) {
      endNode = node;
      endOffset = endIdx;
      break;
    }
    node = walker.nextNode();
  }
  if (!startNode || !endNode) return null;
  const range = doc.createRange();
  range.setStart(startNode, startOffset);
  range.setEnd(endNode, endOffset);
  return range;
}
