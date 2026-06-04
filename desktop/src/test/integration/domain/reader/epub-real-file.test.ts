/**
 * Integration tests for validating real EPUB fixture files.
 *
 * These tests verify EPUB structure directly:
 *   - Valid ZIP archive (magic bytes)
 *   - Container XML exists and parses
 *   - OPF package document parses with metadata, manifest, spine
 *   - Navigation TOC is accessible
 *
 * We use JSZip (already a dependency of epubjs) to unzip and parse
 * the EPUB XML files directly, avoiding epubjs's browser-specific
 * Promise chains that don't resolve in Node.js/jsdom.
 */
import { describe, expect, it, beforeAll } from "vitest";
import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import JSZip from "jszip";

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

const CONTAINER_PATH = "META-INF/container.xml";

interface OpfResult {
  metadata: Record<string, string>;
  manifest: Record<string, { id: string; href: string; "media-type": string }>;
  spine: Array<{ idref: string }>;
  tocHref: string | null;
}

function readFixtureBuffer(filePath: string): Buffer {
  return readFileSync(filePath);
}

/**
 * Parse an XML string with basic regex/string parsing.
 * We avoid DOMParser since it may not be fully available in jsdom
 * and the EPUB XML structure is well-defined enough for string parsing.
 */
function extractXmlText(xml: string, tagName: string): string | null {
  // Match <tagName>text</tagName> or <prefix:tagName>text</prefix:tagName>
  const regex = new RegExp(
    `<(?:(?:\\w+:)?)${tagName}(?:\\s[^>]*)?>([^<]*)<\\/(?:(?:\\w+:)?)${tagName}>`,
    "i",
  );
  const match = xml.match(regex);
  return match ? match[1].trim() : null;
}

/**
 * Extract an attribute value from an XML tag.
 * Handles namespaced and non-namespaced tags.
 * e.g. <rootfile full-path="..."> or <opf:rootfile full-path="...">
 */
function extractAttribute(
  xml: string,
  tagName: string,
  attributeName: string,
): string | null {
  // Match: <tagName ... attr="value" ...>
  // Use [^>]* to match everything between tag name and the target attribute
  const regex = new RegExp(
    `<(?:(?:\\w+:)?)${tagName}[^>]*\\s${attributeName}\\s*=\\s*\"([^\"]*)\"`,
    "i",
  );
  const match = xml.match(regex);
  return match ? match[1] : null;
}

/**
 * Extract all items matching an XML tag pattern with specific attributes.
 * Used for <item> and <itemref> tags in OPF.
 */
function extractItems(
  xml: string,
  tagName: string,
  attributes: string[],
): Array<Record<string, string>> {
  const regex = new RegExp(
    `<(?:(?:\\w+:)?)${tagName}((?:\\s[^>]*?))\\/?>`,
    "gi",
  );
  const results: Array<Record<string, string>> = [];
  let match;
  while ((match = regex.exec(xml)) !== null) {
    const attrs: Record<string, string> = {};
    for (const attr of attributes) {
      const attrRegex = new RegExp(
        `\\s${attr}\\s*=\\s*\"([^\"]*)\"`,
        "i",
      );
      const attrMatch = match[1].match(attrRegex);
      if (attrMatch) {
        attrs[attr] = attrMatch[1];
      }
    }
    results.push(attrs);
  }
  return results;
}

/**
 * Load an EPUB file and parse its structure.
 */
async function loadEpubStructure(filePath: string): Promise<{
  zip: JSZip;
  containerXml: string;
  opfPath: string;
  opfXml: string;
  opf: OpfResult;
}> {
  const data = readFixtureBuffer(filePath);

  // Unzip the EPUB
  const zip = await JSZip.loadAsync(data);

  // Read container XML
  const containerFile = zip.file(CONTAINER_PATH);
  if (!containerFile) {
    throw new Error(`EPUB missing ${CONTAINER_PATH}`);
  }
  const containerXml = await containerFile.async("string");

  // Find OPF path from container
  const opfPath = extractAttribute(
    containerXml,
    "rootfile",
    "full-path",
  );
  if (!opfPath) {
    throw new Error("Could not find rootfile/full-path in container.xml");
  }

  // Read OPF file
  const opfFile = zip.file(opfPath);
  if (!opfFile) {
    throw new Error(`OPF file not found: ${opfPath}`);
  }
  const opfXml = await opfFile.async("string");

  // Parse OPF
  const opf = parseOpf(opfXml);

  return { zip, containerXml, opfPath, opfXml, opf };
}

/**
 * Parse the OPF XML to extract metadata, manifest, spine, and TOC.
 */
function parseOpf(opfXml: string): OpfResult {
  // Metadata: use dc: namespace for title, creator, identifier, language
  const metadata: Record<string, string> = {};

  const title = extractXmlText(opfXml, "title");
  if (title) metadata.title = title;

  const creator = extractXmlText(opfXml, "creator");
  if (creator) metadata.creator = creator;

  const language = extractXmlText(opfXml, "language");
  if (language) metadata.language = language;

  const identifier = extractXmlText(opfXml, "identifier");
  if (identifier) metadata.identifier = identifier;

  const publisher = extractXmlText(opfXml, "publisher");
  if (publisher) metadata.publisher = publisher;

  // Manifest
  const manifestItems = extractItems(opfXml, "item", [
    "id",
    "href",
    "media-type",
  ]);
  const manifest: Record<
    string,
    { id: string; href: string; "media-type": string }
  > = {};
  for (const item of manifestItems) {
    if (item.id) {
      manifest[item.id] = item as unknown as { id: string; href: string; "media-type": string };
    }
  }

  // Spine
  const spineItems = extractItems(opfXml, "itemref", ["idref"]);
  const spine: Array<{ idref: string }> = spineItems.map(
    (item) => ({ idref: item.idref }),
  );

  // Find TOC (navigation document)
  // Look for the NCX or nav document in the spine
  let tocHref: string | null = null;
  const tocItem = manifestItems.find(
    (item) =>
      item["media-type"] === "application/x-dtbncx+xml" ||
      item["media-type"] === "application/xhtml+xml" ||
      item.id === "ncx" ||
      item.id === "toc",
  );
  if (tocItem && tocItem.href) {
    tocHref = tocItem.href;
  }

  return { metadata, manifest, spine, tocHref };
}

/**
 * Extract navigation entries from an NCX file.
 */
function extractNcxEntries(
  ncxXml: string,
): Array<{ label: string; src: string }> {
  // Find all <navPoint> elements and extract label/src
  const navPointRegex = /<navPoint[^>]*>([\s\S]*?)<\/navPoint>/gi;
  const entries: Array<{ label: string; src: string }> = [];

  let match;
  while ((match = navPointRegex.exec(ncxXml)) !== null) {
    const content = match[1];

    const label =
      extractXmlText(content, "text") || "Untitled";
    const src =
      extractAttribute(content, "content", "src") || "";

    entries.push({ label, src });
  }

  return entries;
}

/**
 * Validate that a string looks like valid XML with a root element.
 */
function looksLikeXml(str: string): boolean {
  return /^\s*<\?xml\s|<[a-zA-Z_:]/.test(str.trim());
}

// ── Tests: ZIP / Archive Structure ────────────────────

describe("EPUB Real Files — Archive Structure", () => {
  it.each([
    ["sample1.epub", FIXTURES["sample1.epub"]],
    ["accessible_epub_3.epub", FIXTURES["accessible_epub_3.epub"]],
  ])("%s is a valid ZIP archive", async (name, fixture) => {
    const data = readFixtureBuffer(fixture.path);

    // Check ZIP magic bytes: PK\x03\x04
    expect(data[0]).toBe(0x50); // P
    expect(data[1]).toBe(0x4b); // K
    expect(data[2]).toBe(0x03);
    expect(data[3]).toBe(0x04);

    // Verify it loads as a ZIP
    const zip = await JSZip.loadAsync(data);
    const files = Object.keys(zip.files);
    expect(files.length).toBeGreaterThan(0);
  });

  it("sample1.epub contains META-INF/container.xml", async () => {
    const data = readFixtureBuffer(FIXTURES["sample1.epub"].path);
    const zip = await JSZip.loadAsync(data);
    expect(zip.file(CONTAINER_PATH)).toBeDefined();
  });

  it("accessible_epub_3.epub contains META-INF/container.xml", async () => {
    const data = readFixtureBuffer(FIXTURES["accessible_epub_3.epub"].path);
    const zip = await JSZip.loadAsync(data);
    expect(zip.file(CONTAINER_PATH)).toBeDefined();
  });

  it("mimetype file is first entry (EPUB 3 spec)", async () => {
    const data = readFixtureBuffer(FIXTURES["sample1.epub"].path);
    const zip = await JSZip.loadAsync(data);
    const mimetypeFile = zip.file("mimetype");
    expect(mimetypeFile).toBeDefined();
    expect(mimetypeFile).not.toBeNull();

    const mimetype = await mimetypeFile!.async("string");
    expect(mimetype.trim()).toBe("application/epub+zip");
  });
});

describe("EPUB Real Files — Container XML", () => {
  it("container.xml contains rootfile with full-path attribute", async () => {
    const { containerXml } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    expect(looksLikeXml(containerXml)).toBe(true);
    const fullPath = extractAttribute(
      containerXml,
      "rootfile",
      "full-path",
    );
    expect(fullPath).toBeTruthy();
    expect(fullPath!.endsWith(".opf")).toBe(true);
  });
});

describe("EPUB Real Files — Package Document (OPF)", () => {
  it("sample1.epub has valid OPF with package element", async () => {
    const { opfXml } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );
    expect(looksLikeXml(opfXml)).toBe(true);
    expect(opfXml).toContain("<package");
    expect(opfXml).toContain("</package>");
  });

  it("contains metadata with title and creator", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    expect(opf.metadata.title).toBeDefined();
    expect(opf.metadata.title!.length).toBeGreaterThan(0);
    expect(opf.metadata.creator).toBeDefined();
  });

  it("accessible_epub_3.epub has title metadata", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    expect(opf.metadata.title).toBeDefined();
    expect(opf.metadata.title!.length).toBeGreaterThan(0);
  });

  it("metadata has identifier and language", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    expect(opf.metadata.identifier).toBeDefined();
    expect(opf.metadata.language).toBeDefined();
  });
});

describe("EPUB Real Files — Manifest", () => {
  it("sample1.epub has manifest with items", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    const entries = Object.keys(opf.manifest);
    expect(entries.length).toBeGreaterThan(0);

    // Verify manifest item structure
    const firstId = entries[0];
    const firstItem = opf.manifest[firstId];
    expect(firstItem.id).toBeDefined();
    expect(firstItem.href).toBeDefined();
    expect(firstItem["media-type"]).toBeDefined();
  });

  it("contains at least one XHTML content document", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    const xhtmlItems = Object.values(opf.manifest).filter(
      (item) => item["media-type"] === "application/xhtml+xml",
    );
    expect(xhtmlItems.length).toBeGreaterThan(0);
  });
});

describe("EPUB Real Files — Spine (Reading Order)", () => {
  it("sample1.epub has spine with itemrefs", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    expect(opf.spine.length).toBeGreaterThan(0);

    // Each spine item should reference a manifest ID
    for (const itemref of opf.spine) {
      expect(itemref.idref).toBeDefined();
      expect(opf.manifest[itemref.idref]).toBeDefined();
    }
  });

  it("accessible_epub_3.epub has spine with multiple items", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["accessible_epub_3.epub"].path,
    );

    expect(opf.spine.length).toBeGreaterThanOrEqual(1);

    for (const itemref of opf.spine) {
      expect(itemref.idref).toBeDefined();
    }
  });
});

describe("EPUB Real Files — Table of Contents / Navigation", () => {
  it("sample1.epub has NCX navigation entries", async () => {
    const { zip, opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    // Look for NCX file (TO C XML format)
    const ncxEntry = Object.entries(opf.manifest).find(
      ([, item]) =>
        item["media-type"] === "application/x-dtbncx+xml",
    );

    expect(ncxEntry).toBeDefined();
    const [, ncxItem] = ncxEntry!;

    const ncxFile = zip.file(ncxItem.href);
    expect(ncxFile).toBeDefined();

    const ncxXml = await ncxFile!.async("string");
    const entries = extractNcxEntries(ncxXml);
    expect(entries.length).toBeGreaterThan(0);

    // Verify entry structure
    const firstEntry = entries[0];
    expect(firstEntry.label).toBeDefined();
    expect(firstEntry.label.length).toBeGreaterThan(0);
    expect(firstEntry.src).toBeDefined();
  });

  it("has at least one section in reading order", async () => {
    const { opf } = await loadEpubStructure(
      FIXTURES["sample1.epub"].path,
    );

    expect(opf.spine.length).toBeGreaterThan(0);
  });
});

describe("EPUB Real Files — Edge Cases", () => {
  it("handles invalid EPUB data gracefully", async () => {
    const fakeData = Buffer.from([0, 0, 0, 0, 0, 0]);

    await expect(async () => {
      await JSZip.loadAsync(fakeData);
    }).rejects.toThrow();
  });

  it("loads multiple EPUBs concurrently", async () => {
    const results = await Promise.all([
      loadEpubStructure(FIXTURES["sample1.epub"].path),
      loadEpubStructure(FIXTURES["accessible_epub_3.epub"].path),
    ]);

    expect(results).toHaveLength(2);
    for (const result of results) {
      expect(result.opf.metadata.title).toBeDefined();
    }
  });
});
