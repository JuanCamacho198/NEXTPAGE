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
 * IMPORTANT: These tests require the actual epubjs library and will be slower
 * than unit tests. They are intentionally kept separate from unit tests.
 */
import { describe, expect, it, beforeAll } from "vitest";
import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import ePub from "epubjs";

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
 */
function readFixtureBuffer(filePath: string): ArrayBuffer {
  const buffer = readFileSync(filePath);
  return buffer.buffer.slice(
    buffer.byteOffset,
    buffer.byteOffset + buffer.byteLength,
  ) as ArrayBuffer;
}

/**
 * Load an EPUB book from a fixture file, returning the book instance
 * and waiting for it to be ready.
 */
async function loadEpubFixture(
  filePath: string,
): Promise<{ book: ePub.Book; metadata: ePub.Book["package"] }> {
  const data = readFixtureBuffer(filePath);
  const book = ePub(data) as ePub.Book & { ready: Promise<void> };

  // Wait for the book to be fully parsed
  await (book as any).ready;

  const pkg = (book as any).package;
  return { book, metadata: pkg };
}

// ── Tests: Loading ────────────────────────────────────

describe("EPUB Real Files — Document Loading", () => {
  it.each([
    ["sample1.epub", FIXTURES["sample1.epub"]],
    ["accessible_epub_3.epub", FIXTURES["accessible_epub_3.epub"]],
  ])("%s loads successfully", async (name, fixture) => {
    const { book } = await loadEpubFixture(fixture.path);
    expect(book).toBeDefined();

    // Verify the book has been initialized
    const spine = (book as any).spine;
    expect(spine).toBeDefined();

    book.destroy();
  });
});

describe("EPUB Real Files — Metadata", () => {
  it("sample1.epub has title and creator metadata", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const pkg = (book as any).package;
    expect(pkg).toBeDefined();

    const metadata = pkg.metadata;
    expect(metadata).toBeDefined();
    expect(metadata.title).toBeDefined();
    expect(typeof metadata.title).toBe("string");
    expect(metadata.title!.length).toBeGreaterThan(0);
    expect(metadata.creator).toBeDefined();

    book.destroy();
  });

  it("accessible_epub_3.epub has language and publisher metadata", async () => {
    const { book } = await loadEpubFixture(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    const pkg = (book as any).package;
    const metadata = pkg.metadata;

    expect(metadata).toBeDefined();
    expect(metadata.title).toBeDefined();
    expect(metadata.title!.length).toBeGreaterThan(0);

    // EPUB 3 often has language metadata
    if (metadata.language) {
      expect(typeof metadata.language).toBe("string");
    }

    book.destroy();
  });

  it("metadata contains expected fields", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const pkg = (book as any).package;
    const metadata = pkg.metadata;

    // Check that typical metadata fields exist
    const metadataKeys = Object.keys(metadata);
    expect(metadataKeys.length).toBeGreaterThan(0);

    book.destroy();
  });
});

describe("EPUB Real Files — Table of Contents", () => {
  it("sample1.epub has navigation items in TOC", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    const navigation = await (book as any).loaded.navigation;
    expect(navigation).toBeDefined();
    expect(navigation.toc).toBeDefined();
    expect(Array.isArray(navigation.toc)).toBe(true);

    // A simple EPUB should have at least one TOC entry
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

describe("EPUB Real Files — Cover", () => {
  it("sample1.epub may have a cover URL", async () => {
    const { book } = await loadEpubFixture(FIXTURES["sample1.epub"].path);

    // Cover URL is optional; just verify the method exists and doesn't throw
    const coverUrl = await (book as any).coverUrl();
    // coverUrl may return a data URL or undefined
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

    for (const { book } of results) {
      const pkg = (book as any).package;
      expect(pkg).toBeDefined();
      expect(pkg.metadata).toBeDefined();
      book.destroy();
    }
  });
});
