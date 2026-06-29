/**
 * Integration tests for loading and parsing real PDF fixture files.
 *
 * These tests use actual PDF files from src/test/fixtures/pdfs/ and the
 * real pdfjs-dist library (legacy build) to verify that:
 *   - PDF documents load correctly
 *   - Page count is accurate
 *   - Text content can be extracted
 *   - Outlines/TOC work
 *   - Page dimensions are returned
 *
 * IMPORTANT: These tests require a real PDF.js worker and will be slower
 * than unit tests. They are intentionally kept separate from unit tests.
 */
import { describe, expect, it, beforeAll } from 'vitest';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { pathToFileURL } from 'url';
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs';

// ── Paths ────────────────────────────────────────────

const FIXTURE_DIR = resolve('src/test/fixtures/pdfs');

const FIXTURES = {
  'sample-small.pdf': {
    path: resolve(FIXTURE_DIR, 'sample-small.pdf'),
    expectedPages: 1,
    description: 'PDF de 1 página',
  },
  'sample-multi.pdf': {
    path: resolve(FIXTURE_DIR, 'sample-multi.pdf'),
    expectedMinPages: 2,
    description: 'PDF multi-página',
  },
  'sample-toc.pdf': {
    path: resolve(FIXTURE_DIR, 'sample-toc.pdf'),
    expectedMinPages: 1,
    hasOutline: true,
    description: 'PDF con tabla de contenidos',
  },
  'sample-withimages.pdf': {
    path: resolve(FIXTURE_DIR, 'sample-withimages.pdf'),
    expectedMinPages: 1,
    description: 'PDF con imágenes',
  },
};

// ── Setup ────────────────────────────────────────────

beforeAll(() => {
  // Verify fixtures exist before running tests
  for (const [name, { path }] of Object.entries(FIXTURES)) {
    if (!existsSync(path)) {
      throw new Error(`Fixture not found: ${path} (${name})`);
    }
  }

  // Set up the PDF.js worker for Node.js
  // On Windows, workerSrc must be a file:// URL, not a drive-letter path
  const workerPath = resolve('node_modules/pdfjs-dist/legacy/build/pdf.worker.min.mjs');
  if (!existsSync(workerPath)) {
    throw new Error(`PDF.js worker not found at: ${workerPath}`);
  }
  pdfjsLib.GlobalWorkerOptions.workerSrc = pathToFileURL(workerPath).href;
});

// ── Helpers ──────────────────────────────────────────

/**
 * Read a fixture file and return its bytes as a Uint8Array.
 */
function readFixtureBytes(filePath: string): Uint8Array {
  const buffer = readFileSync(filePath);
  return new Uint8Array(buffer);
}

/**
 * Load a PDF document from a fixture file and return the PDF document proxy.
 */
async function loadPdfFixture(filePath: string): Promise<pdfjsLib.PDFDocumentProxy> {
  const data = readFixtureBytes(filePath);
  const loadingTask = pdfjsLib.getDocument({ data });
  return await loadingTask.promise;
}

/**
 * Extract all text from a single page.
 */
async function getPageText(page: pdfjsLib.PDFPageProxy): Promise<string> {
  const textContent = await page.getTextContent();
  return textContent.items
    .map((item) => ('str' in item ? (item as { str: string }).str : ''))
    .join(' ');
}

// ── Tests: Loading ────────────────────────────────────

describe('PDF Real Files — Document Loading', () => {
  it.each([
    ['sample-small.pdf', FIXTURES['sample-small.pdf']],
    ['sample-multi.pdf', FIXTURES['sample-multi.pdf']],
    ['sample-toc.pdf', FIXTURES['sample-toc.pdf']],
    ['sample-withimages.pdf', FIXTURES['sample-withimages.pdf']],
  ])('%s loads successfully', async (name, fixture) => {
    const pdf = await loadPdfFixture(fixture.path);
    expect(pdf).toBeDefined();
    expect(pdf.numPages).toBeGreaterThanOrEqual(
      'expectedPages' in fixture ? ((fixture as { expectedPages?: number }).expectedPages ?? 1) : 1,
    );
    // Clean up
    pdf.destroy();
  });
});

describe('PDF Real Files — Page Count', () => {
  it('sample-small.pdf has exactly 1 page', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);
    expect(pdf.numPages).toBe(1);
    pdf.destroy();
  });

  it('sample-multi.pdf has multiple pages', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-multi.pdf'].path);
    expect(pdf.numPages).toBeGreaterThanOrEqual(2);
    pdf.destroy();
  });
});

describe('PDF Real Files — Page Dimensions', () => {
  it('returns viewport dimensions for a page', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);
    const page = await pdf.getPage(1);
    const viewport = page.getViewport({ scale: 1 });

    expect(viewport.width).toBeGreaterThan(0);
    expect(viewport.height).toBeGreaterThan(0);
    // Standard A4 portrait is 595.28 x 841.89
    expect(viewport.width).toBeCloseTo(595.28, 0);
    expect(viewport.height).toBeCloseTo(841.89, 0);

    pdf.destroy();
  });

  it('can render at different scales', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-multi.pdf'].path);
    const page = await pdf.getPage(1);

    const vp1 = page.getViewport({ scale: 1 });
    const vp2 = page.getViewport({ scale: 2 });

    expect(vp2.width).toBeCloseTo(vp1.width * 2, 1);
    expect(vp2.height).toBeCloseTo(vp1.height * 2, 1);

    pdf.destroy();
  });
});

describe('PDF Real Files — Text Extraction', () => {
  it('sample-small.pdf contains extractable text', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);
    const page = await pdf.getPage(1);
    const text = await getPageText(page);

    expect(text.length).toBeGreaterThan(0);
    // The small PDF should contain readable text
    expect(text).toMatch(/Image not found|Fecha|publicación/i);

    pdf.destroy();
  });

  it('sample-multi.pdf has text content on each page', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-multi.pdf'].path);

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const text = await getPageText(page);
      expect(text.length).toBeGreaterThan(0);
    }

    pdf.destroy();
  });
});

describe('PDF Real Files — Outline / TOC', () => {
  it('sample-toc.pdf returns outline items', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-toc.pdf'].path);
    const outline = await pdf.getOutline();

    expect(outline).not.toBeNull();
    expect(Array.isArray(outline)).toBe(true);
    expect(outline!.length).toBeGreaterThan(0);

    // Verify outline item structure
    const firstItem = outline![0];
    expect(firstItem).toHaveProperty('title');
    expect(typeof firstItem.title).toBe('string');
    expect(firstItem.title!.length).toBeGreaterThan(0);

    pdf.destroy();
  });

  it('sample-small.pdf has no outline (returns null or empty)', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);
    const outline = await pdf.getOutline();

    // Simple PDFs without TOC may return null or []
    expect(outline === null || (Array.isArray(outline) && outline.length === 0)).toBe(true);

    pdf.destroy();
  });

  it('sample-toc.pdf outline items have valid destinations', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-toc.pdf'].path);
    const outline = await pdf.getOutline();

    expect(outline).not.toBeNull();
    expect(outline!.length).toBeGreaterThan(0);

    for (const item of outline!) {
      expect(item).toHaveProperty('title');
      if (item.dest) {
        // Destination can be a string, array, or null
        expect(typeof item.dest === 'string' || Array.isArray(item.dest)).toBe(true);
      }
    }

    pdf.destroy();
  });
});

describe('PDF Real Files — Metadata', () => {
  it('sample-small.pdf has metadata info', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);

    // pdfjs-dist provides metadata via getMetadata()
    const metadata = await pdf.getMetadata();
    expect(metadata).toBeDefined();
    expect(metadata.info).toBeDefined();

    pdf.destroy();
  });
});

describe('PDF Real Files — Edge Cases', () => {
  it('handles unknown file gracefully with clear error', async () => {
    const fakeData = new Uint8Array([0, 0, 0, 0, 0, 0]);
    const loadingTask = pdfjsLib.getDocument({ data: fakeData });

    await expect(async () => {
      await loadingTask.promise;
    }).rejects.toThrow();
  });

  it('can request a specific page without loading all pages', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-multi.pdf'].path);
    const page = await pdf.getPage(pdf.numPages); // get the last page

    expect(page).toBeDefined();
    expect(page.pageNumber).toBe(pdf.numPages);

    pdf.destroy();
  });

  it('cleans up document resources on destroy', async () => {
    const pdf = await loadPdfFixture(FIXTURES['sample-small.pdf'].path);
    // destroy() should resolve without error
    await expect(pdf.destroy()).resolves.toBeUndefined();
  });

  it('loads multiple documents concurrently', async () => {
    const results = await Promise.all([
      loadPdfFixture(FIXTURES['sample-small.pdf'].path),
      loadPdfFixture(FIXTURES['sample-withimages.pdf'].path),
      loadPdfFixture(FIXTURES['sample-multi.pdf'].path),
    ]);

    expect(results).toHaveLength(3);
    expect(results[0].numPages).toBe(1); // small
    expect(results[2].numPages).toBeGreaterThanOrEqual(2); // multi

    // Clean up all
    for (const pdf of results) {
      pdf.destroy();
    }
  });
});
