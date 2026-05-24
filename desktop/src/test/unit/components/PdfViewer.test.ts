/**
 * PdfViewer structural tests.
 *
 * PdfViewer is deeply coupled to pdfjs-dist, Canvas 2D, Fullscreen API, and
 * Tauri IPC — none available in jsdom. These tests verify minimal mounting
 * and export surface. Full coverage requires browser-level E2E tests.
 */

import { describe, expect, it, vi, beforeAll } from "vitest";

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: { workerSrc: "" },
  getDocument: vi.fn(),
}));

vi.mock("pdfjs-dist/web/pdf_viewer.css", () => ({}));

// Stub constructors/classes that pdfjs-dist touches during import
beforeAll(() => {
  if (!globalThis.DOMMatrix) {
    class DummyMatrix {
      a = 1; b = 0; c = 0; d = 1; e = 0; f = 0;
      translate() { return this; }
      scale() { return this; }
      multiply() { return this; }
      get isIdentity() { return true; }
      toFloat64() { return new Float64Array(6); }
      toFloat32() { return new Float32Array(6); }
      toJSON() { return { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 }; }
      toString() { return "matrix(1,0,0,1,0,0)"; }
    }
    // @ts-expect-error - polyfill
    globalThis.DOMMatrix = DummyMatrix;
  }
});

describe("PdfViewer module", () => {
  it(
    "exports the component as default",
    async () => {
      const mod = await import("$lib/features/reader/components/PdfViewer.svelte");
      expect(typeof mod.default).toBe("function");
    },
    15000
  );
});
