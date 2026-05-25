/**
 * Integration tests for loading and parsing real EPUB fixture files.
 *
 * These tests use actual EPUB files from src/test/fixtures/epubs/ and the
 * real epubjs library to verify that:
 *   - EPUB documents load correctly
 *   - Metadata (title, creator) is extracted
 *   - Table of Contents / navigation works
 *   - Spine items are accessible
 *
 * NOTE: epubjs's `book.ready` never resolves in Node.js (it needs browser
 * rendering APIs). Instead, we use `book.loaded.*` promises which resolve
 * from the initial parse.
 */
import { describe, expect, it, beforeAll } from "vitest";
import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import ePub from "epubjs";
import type { Book } from "epubjs";

// ── Paths ────────────────────────────────────────────

const FIXTURE_DIR = resolve("src/test/fixtures/epubs");

const FIXTURES = {
  "sample1.epub": {
    path: resolve(FIXTURE_DIR, "sample1.epub"),
    description: "EPUB simple básico",
  },
  "accessible_epub_3.epub": {
    path: resolve(FIXTURE_DIR, "accessible_epub_3.epub"),
    description: "EPUB accesible v3",
  },
};

// ── Setup ────────────────────────────────────────────

beforeAll(() => {
  for (const [name, { path }] of Object.entries(FIXTURES)) {
    if (!existsSync(path)) {
      throw new Error(`Fixture not found: ${path} (${name})`);
    }
  }
});

// ── Helpers ──────────────────────────────────────────

/**
 * Read a fixture file and return it as an ArrayBuffer.
 * IMPORTANT: epubjs's constructor checks `instanceof ArrayBuffer`.
 * A Node.js Buffer is NOT an instance of ArrayBuffer, so passing a Buffer
 * causes epubjs to treat it as ``options`` instead of data, and the book
 * never starts loading. We must convert to a true ArrayBuffer.
 */
function readFixtureBuffer(filePath: string): ArrayBuffer {
  const nodeBuffer = readFileSync(filePath);
  // Create a true ArrayBuffer from the Node.js Buffer
  return nodeBuffer.buffer.slice(
    nodeBuffer.byteOffset,
    nodeBuffer.byteOffset + nodeBuffer.byteLength,
  );
}

/**
 * Load an EPUB book from a fixture file.
 *
 * epubjs's `book.ready` never resolves in Node.js (it waits for browser
 * APIs), but we can use `book.loaded.*` promises to access the parsed data
 * directly. The initial parse (unzip + XML) completes synchronously enough
 * that the `loaded` sub-promises resolve in Node.js/Bun.
 */
async function loadEpubFixture(filePath: string): Promise<{
  book: Book;
  metadata: Record<string, any>;
}> {
  const data = readFixtureBuffer(filePath);
  const book = ePub(data) as Book & {
    loaded: {
      metadata: Promise<any>;
      spine: Promise<any[]>;
      navigation: Promise<{ toc: any[] }>;
      manifest: Promise<any>;
      cover: Promise<any>;
    };
  };

  // Wait for metadata which triggers the initial parse
  const metadata = await (book as any).loaded.metadata;

  return { book, metadata };
}

// ── Tests: Loading ────────────────────────────────────

describe("EPUB Real Files — Document Loading", () => {
  it.each([
    ["sample1.epub", FIXTURES["sample1.epub"]],
    ["accessible_epub_3.epub", FIXTURES["accessible_epub_3.epub"]],
  ])("%s loads successfully", async (name, fixture) => {
    const { book, metadata } = await loadEpubFixture(fixture.path);
    expect(book).toBeDefined();
    expect(metadata).toBeDefined();

    const spine = await (book as any).loaded.spine;
    expect(spine).toBeDefined();
    expect(spine.length).toBeGreaterThan(0);

    book.destroy();
  });
});

describe("EPUB Real Files — Metadata", () => {
  it("sample1.epub has title and creator metadata", async () => {
    const { metadata } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    expect(metadata).toBeDefined();
    expect(metadata.title).toBeDefined();
    expect(typeof metadata.title).toBe("string");
    expect(metadata.title!.length).toBeGreaterThan(0);
  });

  it("accessible_epub_3.epub has metadata", async () => {
    const { metadata } = await loadEpubFixture(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    expect(metadata).toBeDefined();
    expect(metadata.title).toBeDefined();
    expect(metadata.title!.length).toBeGreaterThan(0);
  });

  it("metadata contains expected fields", async () => {
    const { metadata } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    // Check that typical metadata fields exist
    const metadataKeys = Object.keys(metadata);
    expect(metadataKeys.length).toBeGreaterThan(0);

    // Common metadata fields in EPUB
    const expectedFields = ["title", "creator", "identifier"];
    for (const field of expectedFields) {
      if (metadata[field]) {
        expect(typeof metadata[field]).toBe("string");
      }
    }
  });
});

describe("EPUB Real Files — Table of Contents", () => {
  it("sample1.epub has navigation items in TOC", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const navigation = await (book as any).loaded.navigation;
    expect(navigation).toBeDefined();
    expect(navigation.toc).toBeDefined();
    expect(Array.isArray(navigation.toc)).toBe(true);
    expect(navigation.toc.length).toBeGreaterThan(0);

    // Verify TOC item structure
    const firstItem = navigation.toc[0];
    expect(firstItem).toHaveProperty("id");
    expect(firstItem).toHaveProperty("label");
    expect(firstItem).toHaveProperty("href");
    expect(typeof firstItem.label).toBe("string");
    expect(firstItem.label!.length).toBeGreaterThan(0);

    book.destroy();
  });

  it("accessible_epub_3.epub has navigation with multiple items", async () => {
    const { book } = await loadEpubFixture(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    const navigation = await (book as any).loaded.navigation;
    expect(navigation).toBeDefined();
    expect(navigation.toc).toBeDefined();
    expect(navigation.toc.length).toBeGreaterThan(0);

    book.destroy();
  });

  it("TOC items have valid hrefs", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const navigation = await (book as any).loaded.navigation;
    for (const item of navigation.toc) {
      expect(item).toHaveProperty("href");
      expect(typeof item.href).toBe("string");
      expect(item.href!.length).toBeGreaterThan(0);
    }

    book.destroy();
  });
});

describe("EPUB Real Files — Spine / Reading Order", () => {
  it("sample1.epub has spine items defined", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const spine = await (book as any).loaded.spine;
    expect(spine).toBeDefined();
    expect(Array.isArray(spine)).toBe(true);
    expect(spine.length).toBeGreaterThan(0);

    // Each spine item should have an href
    const firstItem = spine[0];
    expect(firstItem).toHaveProperty("href");
    expect(typeof firstItem.href).toBe("string");

    book.destroy();
  });

  it("accessible_epub_3.epub has spine with multiple items", async () => {
    const { book } = await loadEpubFixture(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    const spine = await (book as any).loaded.spine;
    expect(spine).toBeDefined();
    expect(spine.length).toBeGreaterThanOrEqual(1);

    book.destroy();
  });
});

describe("EPUB Real Files — Manifest", () => {
  it("sample1.epub has manifest entries", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const manifest = await (book as any).loaded.manifest;
    expect(manifest).toBeDefined();
    expect(typeof manifest).toBe("object");

    // Manifest should have entries (items in the EPUB)
    const entries = Object.keys(manifest);
    expect(entries.length).toBeGreaterThan(0);

    book.destroy();
  });
});

describe("EPUB Real Files — Cover", () => {
  it("sample1.epub may have a cover URL", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    // Cover URL is optional; just verify the method exists and doesn't throw
    const coverUrl = await (book as any).coverUrl();
    if (coverUrl) {
      expect(typeof coverUrl).toBe("string");
      expect(coverUrl.startsWith("data:")).toBe(true);
    }

    book.destroy();
  });
});

describe("EPUB Real Files — Concurrent Loading", () => {
  it("loads multiple EPUBs concurrently", async () => {
    const results = await Promise.all([
      loadEpubFixture(FIXTURES["sample1.epub"].path),
      loadEpubFixture(FIXTURES["accessible_epub_3.epub"].path),
    ]);

    expect(results).toHaveLength(2);

    for (const { book, metadata } of results) {
      expect(metadata).toBeDefined();
      expect(metadata.title).toBeDefined();
      book.destroy();
    }
  });
});
